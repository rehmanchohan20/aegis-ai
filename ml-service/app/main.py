from __future__ import annotations

from pathlib import Path
from typing import Dict, List

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import train_test_split

MODEL_DIR = Path("models")
MODEL_DIR.mkdir(exist_ok=True)
MODEL_PATH = MODEL_DIR / "aegis_random_forest.joblib"

app = FastAPI(title="AEGIS ML Service", version="0.1.0")


class TrainingExample(BaseModel):
    features: Dict[str, float]
    label: int = Field(ge=0, le=1)


class TrainingRequest(BaseModel):
    examples: List[TrainingExample]


class PredictionRequest(BaseModel):
    features: Dict[str, float]


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "modelAvailable": MODEL_PATH.exists()}


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

    frame = pd.DataFrame(rows, columns=feature_names).replace([np.inf, -np.inf], np.nan).dropna()
    if len(frame) != len(labels):
        raise HTTPException(status_code=400, detail="Training data contains invalid values")

    x_train, x_test, y_train, y_test = train_test_split(
        frame, labels, test_size=0.25, random_state=42, stratify=labels
    )
    model = RandomForestClassifier(
        n_estimators=300,
        max_depth=8,
        min_samples_leaf=5,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1,
    )
    model.fit(x_train, y_train)
    predictions = model.predict(x_test)
    probabilities = model.predict_proba(x_test)[:, 1]

    artifact = {"model": model, "features": feature_names}
    joblib.dump(artifact, MODEL_PATH)

    return {
        "status": "TRAINED",
        "examples": len(request.examples),
        "features": feature_names,
        "accuracy": accuracy_score(y_test, predictions),
        "precision": precision_score(y_test, predictions, zero_division=0),
        "recall": recall_score(y_test, predictions, zero_division=0),
        "rocAuc": roc_auc_score(y_test, probabilities) if len(set(y_test)) > 1 else None,
        "featureImportance": dict(
            sorted(zip(feature_names, model.feature_importances_), key=lambda item: item[1], reverse=True)
        ),
    }


@app.post("/predict")
def predict(request: PredictionRequest) -> dict:
    if not MODEL_PATH.exists():
        raise HTTPException(status_code=503, detail="No trained model is available")

    artifact = joblib.load(MODEL_PATH)
    feature_names: list[str] = artifact["features"]
    missing = [name for name in feature_names if name not in request.features]
    if missing:
        raise HTTPException(status_code=400, detail=f"Missing features: {', '.join(missing)}")

    row = pd.DataFrame([[request.features[name] for name in feature_names]], columns=feature_names)
    model = artifact["model"]
    probability = float(model.predict_proba(row)[0, 1])
    return {
        "longProbability": probability,
        "shortProbability": 1.0 - probability,
        "decision": "LONG" if probability >= 0.55 else "SHORT" if probability <= 0.45 else "WAIT",
        "confidence": abs(probability - 0.5) * 200,
        "model": "random-forest-v1",
    }
