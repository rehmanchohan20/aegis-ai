from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List
import json
import uuid

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier, RandomForestClassifier, VotingClassifier
from sklearn.metrics import accuracy_score, balanced_accuracy_score, f1_score, log_loss, precision_recall_fscore_support
from sklearn.model_selection import TimeSeriesSplit

MODEL_DIR = Path("models")
MODEL_DIR.mkdir(exist_ok=True)
REGISTRY_PATH = MODEL_DIR / "registry.json"
ACTIVE_PATH = MODEL_DIR / "active.json"

app = FastAPI(title="AEGIS ML Service", version="1.1.0")


class TrainingExample(BaseModel):
    features: Dict[str, float]
    label: int = Field(ge=-1, le=1)
    observedAt: str | None = None


class TrainingRequest(BaseModel):
    examples: List[TrainingExample]
    model_name: str = "aegis-direction"
    activate: bool = False
    minimum_margin: float = Field(default=0.18, ge=0.0, le=1.0)


class PredictionRequest(BaseModel):
    features: Dict[str, float]
    model_name: str = "aegis-direction"


class ActivationRequest(BaseModel):
    model_name: str
    version: str


def load_json(path: Path, fallback):
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


def build_model() -> VotingClassifier:
    return VotingClassifier(
        estimators=[
            ("rf", RandomForestClassifier(n_estimators=400, max_depth=10, min_samples_leaf=5,
                                           class_weight="balanced_subsample", random_state=42, n_jobs=-1)),
            ("extra", ExtraTreesClassifier(n_estimators=400, max_depth=12, min_samples_leaf=4,
                                            class_weight="balanced", random_state=43, n_jobs=-1)),
            ("hist", HistGradientBoostingClassifier(max_iter=250, max_depth=6, learning_rate=0.045,
                                                      l2_regularization=1.0, random_state=44)),
        ],
        voting="soft", weights=[0.42, 0.38, 0.20], flatten_transform=True,
    )


def normalize_probabilities(model, frame: pd.DataFrame) -> np.ndarray:
    raw = model.predict_proba(frame)
    classes = list(model.classes_)
    output = np.zeros((len(frame), 3), dtype=float)
    for index, label in enumerate((-1, 0, 1)):
        if label in classes:
            output[:, index] = raw[:, classes.index(label)]
    output = np.clip(output, 1e-6, 1.0)
    return output / output.sum(axis=1, keepdims=True)


def temporal_validation(frame: pd.DataFrame, labels: np.ndarray) -> dict:
    splitter = TimeSeriesSplit(n_splits=min(5, max(2, len(frame) // 50)))
    actual: list[int] = []
    predicted: list[int] = []
    probabilities: list[np.ndarray] = []
    for train_index, test_index in splitter.split(frame):
        if len(set(labels[train_index])) < 2:
            continue
        model = build_model()
        model.fit(frame.iloc[train_index], labels[train_index])
        fold_probabilities = normalize_probabilities(model, frame.iloc[test_index])
        fold_predictions = np.asarray([-1, 0, 1])[fold_probabilities.argmax(axis=1)]
        actual.extend(labels[test_index].tolist())
        predicted.extend(fold_predictions.tolist())
        probabilities.extend(fold_probabilities)
    if not actual:
        raise HTTPException(status_code=400, detail="Insufficient chronological class diversity")
    actual_array = np.asarray(actual)
    predicted_array = np.asarray(predicted)
    probability_array = np.asarray(probabilities)
    precision, recall, f1, support = precision_recall_fscore_support(
        actual_array, predicted_array, labels=[-1, 0, 1], zero_division=0
    )
    return {
        "accuracy": float(accuracy_score(actual_array, predicted_array)),
        "balancedAccuracy": float(balanced_accuracy_score(actual_array, predicted_array)),
        "macroF1": float(f1_score(actual_array, predicted_array, average="macro", zero_division=0)),
        "logLoss": float(log_loss(actual_array, probability_array, labels=[-1, 0, 1])),
        "validationExamples": len(actual),
        "perClass": {
            "SHORT": {"precision": float(precision[0]), "recall": float(recall[0]), "f1": float(f1[0]), "support": int(support[0])},
            "WAIT": {"precision": float(precision[1]), "recall": float(recall[1]), "f1": float(f1[1]), "support": int(support[1])},
            "LONG": {"precision": float(precision[2]), "recall": float(recall[2]), "f1": float(f1[2]), "support": int(support[2])},
        },
    }


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

    validation = temporal_validation(frame, np.asarray(labels))
    model = build_model()
    model.fit(frame, labels)
    version = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + uuid.uuid4().hex[:8]
    path = artifact_path(request.model_name, version)
    reference = {name: {"mean": float(frame[name].mean()), "std": float(max(frame[name].std(), 1e-9))} for name in feature_names}
    artifact = {"model": model, "features": feature_names, "version": version,
                "model_name": request.model_name, "minimum_margin": request.minimum_margin,
                "training_reference": reference, "validation": validation}
    joblib.dump(artifact, path)

    registry = load_json(REGISTRY_PATH, [])
    registry.append({"modelName": request.model_name, "version": version, "artifact": str(path),
                     "features": feature_names, "metrics": validation, "minimumMargin": request.minimum_margin,
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
    feature_names: list[str] = artifact["features"]
    missing = [name for name in feature_names if name not in request.features]
    if missing:
        raise HTTPException(status_code=400, detail=f"Missing features: {', '.join(missing)}")
    row = pd.DataFrame([[request.features[name] for name in feature_names]], columns=feature_names)
    if not np.isfinite(row.to_numpy()).all():
        raise HTTPException(status_code=400, detail="Prediction contains invalid values")

    short_probability, wait_probability, long_probability = map(float, normalize_probabilities(artifact["model"], row)[0])
    ordered = sorted([short_probability, wait_probability, long_probability], reverse=True)
    margin = ordered[0] - ordered[1]
    label = [-1, 0, 1][int(np.argmax([short_probability, wait_probability, long_probability]))]
    decision = {-1: "SHORT", 0: "WAIT", 1: "LONG"}[label]
    if margin < float(artifact.get("minimum_margin", 0.18)):
        decision = "WAIT"

    drift_values = []
    for name in feature_names:
        reference = artifact["training_reference"][name]
        drift_values.append(abs((float(request.features[name]) - reference["mean"]) / reference["std"]))
    drift_score = float(np.mean(np.minimum(drift_values, 10.0)))
    if drift_score >= 4.0:
        decision = "WAIT"

    return {"longProbability": long_probability, "waitProbability": wait_probability,
            "shortProbability": short_probability, "decision": decision, "confidence": margin * 100.0,
            "model": f"{request.model_name}:{version}",
            "ensemble": ["random-forest", "extra-trees", "hist-gradient-boosting"],
            "featureDriftScore": drift_score,
            "driftStatus": "HIGH" if drift_score >= 4 else "ELEVATED" if drift_score >= 2 else "NORMAL"}