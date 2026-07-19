"""Train, explicitly activate, and predict with deterministic local synthetic data."""

from __future__ import annotations

import os
from fastapi.testclient import TestClient

from app.main import app
from scripts.generate_synthetic_training import build_payload


def main() -> None:
    client = TestClient(app)
    training = client.post("/train", json=build_payload())
    training.raise_for_status()
    candidate = training.json()
    os.environ["AEGIS_MODEL_DEPLOYMENT_APPROVAL_TOKEN"] = "smoke-approval-token"
    activation = client.post("/models/activate", json={
        "model_name": candidate["modelName"], "version": candidate["version"],
        "approval_token": "smoke-approval-token"
    })
    activation.raise_for_status()
    prediction = client.post("/predict", json={
        "model_name": candidate["modelName"],
        "features": {"momentum": 0.8, "trend": 0.5, "volatility": 1.1},
    })
    prediction.raise_for_status()
    result = prediction.json()
    required = {"longProbability", "waitProbability", "shortProbability", "decision",
                "confidence", "confidenceMargin", "model", "featureDriftScore", "driftStatus",
                "predictionSet", "uncertaintyStatus", "entropy", "featureDriftContributions"}
    required |= {"expectedReturn", "expectedVolatility", "stopHitProbability",
                 "targetHitProbability", "tradeQualityScore"}
    missing = required - result.keys()
    if missing:
        raise RuntimeError(f"Prediction response omitted: {sorted(missing)}")
    print({"candidate": candidate["version"], "prediction": result})


if __name__ == "__main__":
    main()
