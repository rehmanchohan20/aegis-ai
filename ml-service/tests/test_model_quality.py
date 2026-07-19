from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import pytest
from fastapi import HTTPException
from sklearn.model_selection import TimeSeriesSplit

from app import main


class FixedProbabilityModel:
    classes_ = np.asarray([-1, 0, 1])

    def __init__(self, probabilities: list[float]):
        self.probabilities = np.asarray(probabilities)

    def predict_proba(self, frame: pd.DataFrame) -> np.ndarray:
        return np.tile(self.probabilities, (len(frame), 1))


def install_artifact(monkeypatch: pytest.MonkeyPatch, tmp_path: Path,
                     probabilities: list[float], reference_mean: float = 0.0) -> None:
    monkeypatch.setattr(main, "MODEL_DIR", tmp_path)
    monkeypatch.setattr(main, "ACTIVE_PATH", tmp_path / "active.json")
    monkeypatch.setattr(main, "REGISTRY_PATH", tmp_path / "registry.json")
    main.save_json(main.ACTIVE_PATH, {"aegis-direction": "v1"})
    joblib.dump({"schema_version": main.ARTIFACT_SCHEMA_VERSION,
                 "model": FixedProbabilityModel(probabilities), "features": ["signal"],
                 "minimum_margin": 0.18,
                 "conformal_nonconformity_quantile": 0.55,
                 "training_reference": {"signal": {"mean": reference_mean, "median": reference_mean,
                                                       "std": 1.0,
                                                       "quantiles": [reference_mean - 3, reference_mean - 1.5,
                                                                     reference_mean - .7, reference_mean,
                                                                     reference_mean + .7, reference_mean + 1.5,
                                                                     reference_mean + 3]}}},
                main.artifact_path("aegis-direction", "v1"))


def test_temporal_split_ordering() -> None:
    indices = np.arange(180)
    for train_index, test_index in TimeSeriesSplit(n_splits=5).split(indices):
        assert train_index[-1] < test_index[0]


def test_embargoed_temporal_split_preserves_gap() -> None:
    indices = np.arange(180)
    for train_index, test_index in TimeSeriesSplit(n_splits=5, gap=3).split(indices):
        assert train_index[-1] + 3 < test_index[0]


def test_class_mapping_and_weak_margin_neutralization(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    install_artifact(monkeypatch, tmp_path, [0.36, 0.30, 0.34])
    result = main.predict(main.PredictionRequest(features={"signal": 0.0}))
    assert result["decision"] == "WAIT"
    assert result["shortProbability"] == pytest.approx(0.36)


def test_high_drift_forces_wait(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    install_artifact(monkeypatch, tmp_path, [0.02, 0.03, 0.95])
    result = main.predict(main.PredictionRequest(features={"signal": 10.0}))
    assert result["decision"] == "WAIT"
    assert result["driftStatus"] == "HALTED"


def test_conformal_prediction_set_reports_low_uncertainty(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    install_artifact(monkeypatch, tmp_path, [0.02, 0.03, 0.95])
    result = main.predict(main.PredictionRequest(features={"signal": 0.0}))
    assert result["decision"] == "LONG"
    assert result["predictionSet"] == ["LONG"]
    assert result["uncertaintyStatus"] == "LOW"


def test_statistical_metrics_include_calibration_and_dependence_aware_intervals() -> None:
    actual = np.asarray([-1, 0, 1] * 40)
    probabilities = np.full((120, 3), 0.05)
    probabilities[np.arange(120), np.searchsorted(main.CLASS_LABELS, actual)] = 0.90
    predicted = main.CLASS_LABELS[probabilities.argmax(axis=1)]
    metrics = main._metric_summary(actual, predicted, probabilities)
    intervals = main.block_bootstrap_intervals(actual, predicted, probabilities, iterations=40)
    assert metrics["multiclassBrierScore"] < 0.02
    assert metrics["calibrationError"] <= 0.11
    assert intervals["macroF1"][0] > 0.95


@pytest.mark.parametrize("features,detail", [({}, "Missing features"),
                                               ({"signal": 0.0, "extra": 1.0}, "Unexpected features"),
                                               ({"signal": float("nan")}, "invalid values")])
def test_rejects_invalid_feature_schema(monkeypatch: pytest.MonkeyPatch, tmp_path: Path,
                                        features: dict[str, float], detail: str) -> None:
    install_artifact(monkeypatch, tmp_path, [0.1, 0.2, 0.7])
    with pytest.raises(HTTPException) as raised:
        main.predict(main.PredictionRequest(features=features))
    assert detail in str(raised.value.detail)


def test_model_activation_requires_separate_approval_token(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.setattr(main, "MODEL_DIR", tmp_path)
    monkeypatch.setattr(main, "ACTIVE_PATH", tmp_path / "active.json")
    monkeypatch.setattr(main, "REGISTRY_PATH", tmp_path / "registry.json")
    monkeypatch.setenv("AEGIS_MODEL_DEPLOYMENT_APPROVAL_TOKEN", "approved-separate-token")
    joblib.dump({"schema_version": main.ARTIFACT_SCHEMA_VERSION}, main.artifact_path("direction", "v1"))
    with pytest.raises(HTTPException) as raised:
        main.activate(main.ActivationRequest(model_name="direction", version="v1", approval_token="wrong"))
    assert raised.value.status_code == 403
    result = main.activate(main.ActivationRequest(
        model_name="direction", version="v1", approval_token="approved-separate-token"))
    assert result["status"] == "ACTIVE"
