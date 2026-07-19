from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any
import json
import math
import uuid

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.base import clone
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier, RandomForestClassifier, VotingClassifier
from sklearn.metrics import (accuracy_score, balanced_accuracy_score, confusion_matrix, f1_score,
                             cohen_kappa_score, log_loss, matthews_corrcoef,
                             precision_recall_fscore_support)
from sklearn.model_selection import TimeSeriesSplit
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import RobustScaler

MODEL_DIR = Path("models")
MODEL_DIR.mkdir(exist_ok=True)
REGISTRY_PATH = MODEL_DIR / "registry.json"
ACTIVE_PATH = MODEL_DIR / "active.json"

app = FastAPI(title="AEGIS ML Service", version="1.2.0")
ARTIFACT_SCHEMA_VERSION = 3
CLASS_LABELS = np.asarray([-1, 0, 1])
CLASS_NAMES = {-1: "SHORT", 0: "WAIT", 1: "LONG"}


class TrainingExample(BaseModel):
    features: dict[str, float]
    label: int = Field(ge=-1, le=1)
    observedAt: str | None = None


class TrainingRequest(BaseModel):
    examples: list[TrainingExample]
    model_name: str = "aegis-direction"
    activate: bool = False
    minimum_margin: float = Field(default=0.18, ge=0.0, le=1.0)
    validation_gap: int = Field(default=1, ge=0, le=50)


class PredictionRequest(BaseModel):
    features: dict[str, float]
    model_name: str = "aegis-direction"


class ActivationRequest(BaseModel):
    model_name: str
    version: str


def load_json(path: Path, fallback: Any) -> Any:
    if not path.exists():
        return fallback
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path: Path, value) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2), encoding="utf-8")
    temporary.replace(path)


def artifact_path(model_name: str, version: str) -> Path:
    return MODEL_DIR / f"{model_name.replace('/', '_')}__{version.replace('/', '_')}.joblib"


def active_version(model_name: str) -> str | None:
    return load_json(ACTIVE_PATH, {}).get(model_name)


def component_models() -> dict[str, Pipeline]:
    def pipeline(estimator: Any) -> Pipeline:
        return Pipeline([("scale", RobustScaler()), ("classifier", estimator)])
    return {
        "randomForest": pipeline(RandomForestClassifier(
            n_estimators=180, max_depth=10, min_samples_leaf=5,
            class_weight="balanced_subsample", random_state=42, n_jobs=-1)),
        "extraTrees": pipeline(ExtraTreesClassifier(
            n_estimators=180, max_depth=12, min_samples_leaf=4,
            class_weight="balanced", random_state=43, n_jobs=-1)),
        "histGradientBoosting": pipeline(HistGradientBoostingClassifier(
            max_iter=180, max_depth=6, learning_rate=0.045,
            l2_regularization=1.0, random_state=44)),
    }


def build_model() -> VotingClassifier:
    components = component_models()
    return VotingClassifier(
        estimators=[
            ("rf", components["randomForest"]),
            ("extra", components["extraTrees"]),
            ("hist", components["histGradientBoosting"]),
        ],
        voting="soft", weights=[0.42, 0.38, 0.20], flatten_transform=True,
    )


def normalize_probabilities(model: Any, frame: pd.DataFrame) -> np.ndarray:
    raw = model.predict_proba(frame)
    classes = list(model.classes_)
    output = np.zeros((len(frame), 3), dtype=float)
    for index, label in enumerate((-1, 0, 1)):
        if label in classes:
            output[:, index] = raw[:, classes.index(label)]
    output = np.clip(output, 1e-6, 1.0)
    return output / output.sum(axis=1, keepdims=True)


def _metric_summary(actual_array: np.ndarray, predicted_array: np.ndarray,
                    probability_array: np.ndarray) -> dict[str, Any]:
    precision, recall, f1, support = precision_recall_fscore_support(
        actual_array, predicted_array, labels=CLASS_LABELS, zero_division=0
    )
    distribution = {CLASS_NAMES[label]: int(np.sum(actual_array == label)) for label in CLASS_LABELS}
    calibration_error = expected_calibration_error(actual_array, predicted_array, probability_array)
    one_hot = np.eye(3)[np.searchsorted(CLASS_LABELS, actual_array)]
    multiclass_brier = float(np.mean(np.sum(np.square(probability_array - one_hot), axis=1)))
    directional_mask = predicted_array != 0
    false_directional_rate = float(np.mean((predicted_array != actual_array) & directional_mask))
    return {
        "accuracy": float(accuracy_score(actual_array, predicted_array)),
        "balancedAccuracy": float(balanced_accuracy_score(actual_array, predicted_array)),
        "macroF1": float(f1_score(actual_array, predicted_array, average="macro", zero_division=0)),
        "logLoss": float(log_loss(actual_array, probability_array, labels=CLASS_LABELS)),
        "calibrationError": calibration_error,
        "multiclassBrierScore": multiclass_brier,
        "matthewsCorrelationCoefficient": float(matthews_corrcoef(actual_array, predicted_array)),
        "cohenKappa": float(cohen_kappa_score(actual_array, predicted_array)),
        "directionalPrecision": float((precision[0] + precision[2]) / 2.0),
        "directionalCoverage": float(np.mean(directional_mask)),
        "falseDirectionalRate": false_directional_rate,
        "validationSampleCount": int(len(actual_array)),
        "classDistribution": distribution,
        "confusionMatrix": confusion_matrix(actual_array, predicted_array, labels=CLASS_LABELS).tolist(),
        "perClass": {
            CLASS_NAMES[label]: {"precision": float(precision[index]), "recall": float(recall[index]),
                                 "f1": float(f1[index]), "support": int(support[index])}
            for index, label in enumerate(CLASS_LABELS)
        },
    }


def expected_calibration_error(actual: np.ndarray, predicted: np.ndarray,
                               probabilities: np.ndarray, bins: int = 10) -> float:
    confidence = probabilities.max(axis=1)
    correctness = (actual == predicted).astype(float)
    edges = np.linspace(0.0, 1.0, bins + 1)
    error = 0.0
    for index in range(bins):
        include = (confidence > edges[index]) & (confidence <= edges[index + 1])
        if index == 0:
            include |= confidence == 0.0
        if np.any(include):
            error += float(np.mean(include)) * abs(float(np.mean(confidence[include])) - float(np.mean(correctness[include])))
    return float(error)


def block_bootstrap_intervals(actual: np.ndarray, predicted: np.ndarray,
                              probabilities: np.ndarray, iterations: int = 400) -> dict[str, list[float]]:
    sample_count = len(actual)
    block_size = max(2, int(round(math.sqrt(sample_count))))
    rng = np.random.default_rng(20260719)
    estimates: dict[str, list[float]] = {"balancedAccuracy": [], "macroF1": [], "directionalPrecision": []}
    for _ in range(iterations):
        indices: list[int] = []
        while len(indices) < sample_count:
            start = int(rng.integers(0, max(1, sample_count - block_size + 1)))
            indices.extend(range(start, min(start + block_size, sample_count)))
        selected = np.asarray(indices[:sample_count])
        selected_actual = actual[selected]
        selected_predicted = predicted[selected]
        precision, _, _, _ = precision_recall_fscore_support(
            selected_actual, selected_predicted, labels=CLASS_LABELS, zero_division=0)
        estimates["balancedAccuracy"].append(float(balanced_accuracy_score(selected_actual, selected_predicted)))
        estimates["macroF1"].append(float(f1_score(selected_actual, selected_predicted, average="macro", zero_division=0)))
        estimates["directionalPrecision"].append(float((precision[0] + precision[2]) / 2.0))
    return {name: [float(np.quantile(values, 0.025)), float(np.quantile(values, 0.975))]
            for name, values in estimates.items()}


def choose_margin_threshold(actual: np.ndarray, probabilities: np.ndarray) -> dict[str, Any]:
    ordered = np.sort(probabilities, axis=1)
    margins = ordered[:, -1] - ordered[:, -2]
    raw = CLASS_LABELS[probabilities.argmax(axis=1)]
    best_threshold, best_score, best_metrics = 0.18, -1.0, {}
    for threshold in np.linspace(0.05, 0.35, 13):
        adjusted = np.where(margins < threshold, 0, raw)
        metrics = _metric_summary(actual, adjusted, probabilities)
        score = (metrics["macroF1"] + 0.25 * metrics["balancedAccuracy"]
                 + 0.15 * metrics["directionalPrecision"] - 0.10 * metrics["falseDirectionalRate"])
        if score > best_score:
            best_threshold, best_score, best_metrics = float(threshold), float(score), metrics
    return {"threshold": best_threshold, "objective": best_score, "metrics": best_metrics}


def temporal_validation(frame: pd.DataFrame, labels: np.ndarray, gap: int = 1) -> dict[str, Any]:
    safe_gap = min(max(0, gap), max(0, len(frame) // 20))
    splitter = TimeSeriesSplit(n_splits=min(5, max(2, len(frame) // 50)), gap=safe_gap)
    candidates: dict[str, Any] = {**component_models(), "ensemble": build_model()}
    results: dict[str, dict[str, Any]] = {}
    ensemble_actual: np.ndarray | None = None
    ensemble_probabilities: np.ndarray | None = None
    for model_name, candidate in candidates.items():
        actual: list[int] = []
        predicted: list[int] = []
        probabilities: list[np.ndarray] = []
        fold_metrics: list[dict[str, Any]] = []
        previous_train_end = -1
        for train_index, test_index in splitter.split(frame):
            if train_index[-1] + safe_gap >= test_index[0] or train_index[0] <= previous_train_end and test_index[0] <= previous_train_end:
                raise RuntimeError("Temporal split ordering invariant violated")
            previous_train_end = int(train_index[-1])
            if len(set(labels[train_index])) < 2:
                continue
            model = clone(candidate)
            model.fit(frame.iloc[train_index], labels[train_index])
            fold_probabilities = normalize_probabilities(model, frame.iloc[test_index])
            fold_predictions = CLASS_LABELS[fold_probabilities.argmax(axis=1)]
            fold_summary = _metric_summary(labels[test_index], fold_predictions, fold_probabilities)
            fold_metrics.append({"trainStart": int(train_index[0]), "trainEnd": int(train_index[-1]),
                                 "testStart": int(test_index[0]), "testEnd": int(test_index[-1]),
                                 "balancedAccuracy": fold_summary["balancedAccuracy"],
                                 "macroF1": fold_summary["macroF1"],
                                 "logLoss": fold_summary["logLoss"],
                                 "calibrationError": fold_summary["calibrationError"]})
            actual.extend(labels[test_index].tolist())
            predicted.extend(fold_predictions.tolist())
            probabilities.extend(fold_probabilities)
        if not actual:
            continue
        actual_array = np.asarray(actual)
        predicted_array = np.asarray(predicted)
        probability_array = np.asarray(probabilities)
        results[model_name] = {**_metric_summary(actual_array, predicted_array, probability_array),
                               "foldMetrics": fold_metrics,
                               "worstFoldBalancedAccuracy": min(metric["balancedAccuracy"] for metric in fold_metrics),
                               "foldBalancedAccuracyStd": float(np.std(
                                   [metric["balancedAccuracy"] for metric in fold_metrics], ddof=1))
                                   if len(fold_metrics) > 1 else 0.0}
        if model_name == "ensemble":
            ensemble_actual, ensemble_probabilities = actual_array, probability_array
    if ensemble_actual is None or ensemble_probabilities is None:
        raise HTTPException(status_code=400, detail="Insufficient chronological class diversity")
    threshold_selection = choose_margin_threshold(ensemble_actual, ensemble_probabilities)
    ensemble_predictions = CLASS_LABELS[ensemble_probabilities.argmax(axis=1)]
    true_class_positions = np.searchsorted(CLASS_LABELS, ensemble_actual)
    nonconformity = 1.0 - ensemble_probabilities[np.arange(len(ensemble_actual)), true_class_positions]
    conformal_quantile = float(np.quantile(nonconformity, 0.9, method="higher"))
    return {**results["ensemble"], "components": results,
            "chosenMinimumMargin": threshold_selection["threshold"],
            "thresholdSelection": threshold_selection,
            "confidenceIntervals95": block_bootstrap_intervals(
                ensemble_actual, ensemble_predictions, ensemble_probabilities),
            "conformalAlpha": 0.1, "conformalNonconformityQuantile": conformal_quantile,
            "validationGap": safe_gap,
            "validationMethod": "purged-chronological-TimeSeriesSplit-with-gap"}


@app.get("/health")
def health() -> dict:
    active = load_json(ACTIVE_PATH, {})
    return {"status": "UP", "activeModels": active, "modelAvailable": bool(active)}


@app.get("/models")
def models() -> dict:
    return {"models": load_json(REGISTRY_PATH, []), "active": load_json(ACTIVE_PATH, {})}


@app.post("/models/activate")
def activate(request: ActivationRequest) -> dict:
    path = artifact_path(request.model_name, request.version)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Model artifact not found")
    active = load_json(ACTIVE_PATH, {})
    previous = active.get(request.model_name)
    active[request.model_name] = request.version
    save_json(ACTIVE_PATH, active)
    return {"status": "ACTIVE", "model": request.model_name, "version": request.version, "previous": previous}


@app.post("/models/rollback")
def rollback(request: ActivationRequest) -> dict:
    return activate(request)


@app.post("/train")
def train(request: TrainingRequest) -> dict:
    examples = sorted(request.examples, key=lambda item: item.observedAt or "") if all(e.observedAt for e in request.examples) else request.examples
    if len(examples) < 150:
        raise HTTPException(status_code=400, detail="At least 150 labelled examples are required")
    feature_names = sorted(examples[0].features.keys())
    if not feature_names:
        raise HTTPException(status_code=400, detail="Feature set cannot be empty")
    rows, labels = [], []
    for example in examples:
        if sorted(example.features.keys()) != feature_names:
            raise HTTPException(status_code=400, detail="All examples must contain the same features")
        rows.append([example.features[name] for name in feature_names])
        labels.append(example.label)
    frame = pd.DataFrame(rows, columns=feature_names).replace([np.inf, -np.inf], np.nan)
    if frame.isna().any().any():
        raise HTTPException(status_code=400, detail="Training data contains invalid values")
    if len(set(labels)) < 3:
        raise HTTPException(status_code=400, detail="Training requires SHORT, WAIT and LONG examples")

    validation = temporal_validation(frame, np.asarray(labels), request.validation_gap)
    model = build_model()
    label_array = np.asarray(labels)
    calibration_gap = min(request.validation_gap, max(0, len(frame) // 20))
    calibration_splits = list(TimeSeriesSplit(n_splits=3, gap=calibration_gap).split(frame))
    can_calibrate = all(len(set(label_array[train_index])) == 3 for train_index, _ in calibration_splits)
    fitted_model: Any
    calibration_method = "none"
    if can_calibrate:
        fitted_model = CalibratedClassifierCV(estimator=model, method="sigmoid", cv=calibration_splits)
        fitted_model.fit(frame, label_array)
        calibration_method = "sigmoid-TimeSeriesSplit"
    else:
        fitted_model = model.fit(frame, label_array)
    version = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + uuid.uuid4().hex[:8]
    path = artifact_path(request.model_name, version)
    reference = {
        name: {"mean": float(frame[name].mean()), "std": float(max(frame[name].std(), 1e-9)),
               "median": float(frame[name].median()),
               "quantiles": [float(value) for value in frame[name].quantile([0, .1, .25, .5, .75, .9, 1]).tolist()]}
        for name in feature_names
    }
    chosen_margin = max(float(request.minimum_margin), float(validation["chosenMinimumMargin"]))
    artifact = {"schema_version": ARTIFACT_SCHEMA_VERSION, "model": fitted_model,
                "features": feature_names, "version": version,
                "model_name": request.model_name, "minimum_margin": chosen_margin,
                "training_reference": reference, "validation": validation,
                "conformal_nonconformity_quantile": validation["conformalNonconformityQuantile"],
                "conformal_alpha": validation["conformalAlpha"],
                "calibration_method": calibration_method,
                "trained_at": datetime.now(timezone.utc).isoformat()}
    joblib.dump(artifact, path)

    registry = load_json(REGISTRY_PATH, [])
    registry.append({"modelName": request.model_name, "version": version, "artifact": str(path),
                     "features": feature_names, "metrics": validation, "minimumMargin": chosen_margin,
                     "artifactSchemaVersion": ARTIFACT_SCHEMA_VERSION, "calibrationMethod": calibration_method,
                     "createdAt": datetime.now(timezone.utc).isoformat(), "status": "CANDIDATE"})
    save_json(REGISTRY_PATH, registry)
    if request.activate:
        activate(ActivationRequest(model_name=request.model_name, version=version))
    return {"status": "TRAINED", "modelName": request.model_name, "version": version,
            "examples": len(examples), "features": feature_names, "validation": validation}


@app.post("/predict")
def predict(request: PredictionRequest) -> dict:
    version = active_version(request.model_name)
    if version is None:
        raise HTTPException(status_code=503, detail="No active model is available")
    path = artifact_path(request.model_name, version)
    if not path.exists():
        raise HTTPException(status_code=503, detail="Active model artifact is missing")
    artifact = joblib.load(path)
    if artifact.get("schema_version") != ARTIFACT_SCHEMA_VERSION:
        raise HTTPException(status_code=503, detail="Active model artifact metadata is incompatible")
    feature_names: list[str] = artifact["features"]
    missing = [name for name in feature_names if name not in request.features]
    extra = sorted(set(request.features) - set(feature_names))
    if missing:
        raise HTTPException(status_code=400, detail=f"Missing features: {', '.join(missing)}")
    if extra:
        raise HTTPException(status_code=400, detail=f"Unexpected features: {', '.join(extra)}")
    row = pd.DataFrame([[request.features[name] for name in feature_names]], columns=feature_names)
    if not np.isfinite(row.to_numpy()).all():
        raise HTTPException(status_code=400, detail="Prediction contains invalid values")

    short_probability, wait_probability, long_probability = map(float, normalize_probabilities(artifact["model"], row)[0])
    ordered = sorted([short_probability, wait_probability, long_probability], reverse=True)
    margin = ordered[0] - ordered[1]
    entropy = float(-np.sum(np.asarray([short_probability, wait_probability, long_probability])
                            * np.log(np.clip([short_probability, wait_probability, long_probability], 1e-12, 1.0)))
                    / np.log(3.0))
    label = int(CLASS_LABELS[int(np.argmax([short_probability, wait_probability, long_probability]))])
    decision = CLASS_NAMES[label]
    if margin < float(artifact.get("minimum_margin", 0.18)):
        decision = "WAIT"
    if entropy >= 0.92:
        decision = "WAIT"

    drift_values: list[float] = []
    drift_contributions: dict[str, float] = {}
    for name in feature_names:
        reference = artifact["training_reference"][name]
        value = float(request.features[name])
        robust_z = abs((value - reference["median"]) / reference["std"])
        quantiles = reference["quantiles"]
        if value < quantiles[1]:
            tail_score = (quantiles[1] - value) / max(quantiles[1] - quantiles[0], reference["std"], 1e-9)
        elif value > quantiles[5]:
            tail_score = (value - quantiles[5]) / max(quantiles[6] - quantiles[5], reference["std"], 1e-9)
        else:
            tail_score = 0.0
        contribution = 0.65 * min(robust_z, 10.0) + 0.35 * min(tail_score, 10.0)
        drift_values.append(contribution)
        drift_contributions[name] = contribution
    drift_score = float(np.mean(np.minimum(drift_values, 10.0)))
    if drift_score >= 4.0:
        decision = "WAIT"

    probabilities_by_label = {
        "SHORT": short_probability, "WAIT": wait_probability, "LONG": long_probability
    }
    conformal_quantile = float(artifact.get("conformal_nonconformity_quantile", 1.0))
    probability_floor = max(0.0, 1.0 - conformal_quantile)
    prediction_set = [name for name, probability in probabilities_by_label.items()
                      if probability >= probability_floor]
    uncertainty_status = "LOW" if len(prediction_set) == 1 and entropy < 0.65 else "ELEVATED" if len(prediction_set) <= 2 else "HIGH"
    if len(prediction_set) != 1:
        decision = "WAIT"

    return {"longProbability": long_probability, "waitProbability": wait_probability,
            "shortProbability": short_probability, "decision": decision, "confidence": ordered[0],
            "confidenceMargin": margin, "entropy": entropy,
            "predictionSet": prediction_set, "uncertaintyStatus": uncertainty_status,
            "model": f"{request.model_name}:{version}",
            "ensemble": ["random-forest", "extra-trees", "hist-gradient-boosting"],
            "featureDriftScore": drift_score,
            "featureDriftContributions": dict(sorted(drift_contributions.items(),
                                                     key=lambda item: item[1], reverse=True)),
            "driftStatus": "HALTED" if drift_score >= 4 else "DEGRADED" if drift_score >= 2 else "HEALTHY"}
