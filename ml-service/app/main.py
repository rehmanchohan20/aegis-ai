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
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, precision_score, recall_score
from sklearn.model_selection import train_test_split

MODEL_DIR = Path("models")
MODEL_DIR.mkdir(exist_ok=True)
REGISTRY_PATH = MODEL_DIR / "registry.json"
ACTIVE_PATH = MODEL_DIR / "active.json"

app = FastAPI(title="AEGIS ML Service", version="1.0.0")


class TrainingExample(BaseModel):
    features: Dict[str, float]
    label: int = Field(ge=-1, le=1)


class TrainingRequest(BaseModel):
    examples: List[TrainingExample]
    model_name: str = "aegis-direction"
    activate: bool = False


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
    safe_name = model_name.replace("/", "_")
    safe_version = version.replace("/", "_")
    return MODEL_DIR / f"{safe_name}__{safe_version}.joblib"


def active_version(model_name: str) -> str | None:
    return load_json(ACTIVE_PATH, {}).get(model_name)


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
    if len(request.examples) < 50:
        raise HTTPException(status_code=400, detail="At least 50 labelled examples are required")

    feature_names = sorted(request.examples[0].features.keys())
    if not feature_names:
        raise HTTPException(status_code=400, detail="Feature set cannot be empty")

    rows: list[list[float]] = []
    labels: list[int] = []
    for example in request.examples:
        if sorted(example.features.keys()) != feature_names:
            raise HTTPException(status_code=400, detail="All examples must contain the same features")
        rows.append([example.features[name] for name in feature_names])
        labels.append(example.label)

    frame = pd.DataFrame(rows, columns=feature_names).replace([np.inf, -np.inf], np.nan)
    if frame.isna().any().any():
        raise HTTPException(status_code=400, detail="Training data contains invalid values")
    if len(set(labels)) < 2:
        raise HTTPException(status_code=400, detail="Training data requires at least two label classes")

    x_train, x_test, y_train, y_test = train_test_split(
        frame, labels, test_size=0.25, random_state=42, stratify=labels
    )
    model = RandomForestClassifier(
        n_estimators=400, max_depth=10, min_samples_leaf=5,
        class_weight="balanced", random_state=42, n_jobs=-1,
    )
    model.fit(x_train, y_train)
    predictions = model.predict(x_test)

    version = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + uuid.uuid4().hex[:8]
    path = artifact_path(request.model_name, version)
    artifact = {"model": model, "features": feature_names, "version": version, "model_name": request.model_name}
    joblib.dump(artifact, path)

    metrics = {
        "accuracy": float(accuracy_score(y_test, predictions)),
        "precisionMacro": float(precision_score(y_test, predictions, average="macro", zero_division=0)),
        "recallMacro": float(recall_score(y_test, predictions, average="macro", zero_division=0)),
        "featureImportance": dict(sorted(zip(feature_names, model.feature_importances_), key=lambda x: x[1], reverse=True)),
    }
    registry = load_json(REGISTRY_PATH, [])
    registry.append({
        "modelName": request.model_name, "version": version, "artifact": str(path),
        "features": feature_names, "metrics": metrics, "createdAt": datetime.now(timezone.utc).isoformat(),
    })
    save_json(REGISTRY_PATH, registry)
    if request.activate:
        activate(ActivationRequest(model_name=request.model_name, version=version))

    return {"status": "TRAINED", "modelName": request.model_name, "version": version,
            "examples": len(request.examples), "features": feature_names, **metrics}


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
    model = artifact["model"]
    class_probabilities = dict(zip(model.classes_.tolist(), model.predict_proba(row)[0].tolist()))
    short_probability = float(class_probabilities.get(-1, 0.0))
    neutral_probability = float(class_probabilities.get(0, 0.0))
    long_probability = float(class_probabilities.get(1, 0.0))
    decision = "LONG" if long_probability >= 0.55 else "SHORT" if short_probability >= 0.55 else "WAIT"
    confidence = max(long_probability, short_probability, neutral_probability) * 100.0
    return {
        "longProbability": long_probability,
        "shortProbability": short_probability,
        "neutralProbability": neutral_probability,
        "decision": decision,
        "confidence": confidence,
        "model": f"{request.model_name}:{version}",
    }
