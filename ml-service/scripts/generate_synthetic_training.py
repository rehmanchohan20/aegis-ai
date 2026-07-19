"""Emit deterministic chronological three-class training data for local smoke tests."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
import math


def build_payload() -> dict[str, object]:
    start = datetime(2025, 1, 1, tzinfo=timezone.utc)
    examples: list[dict[str, object]] = []
    for index in range(240):
        phase = math.sin(index / 8.0)
        momentum = math.sin(index / 3.0) + phase * 0.4
        label = 1 if momentum > 0.45 else -1 if momentum < -0.45 else 0
        examples.append({
            "features": {
                "momentum": round(momentum, 8),
                "volatility": round(0.8 + abs(math.cos(index / 11.0)), 8),
                "trend": round(phase, 8),
            },
            "label": label,
            "observedAt": (start + timedelta(minutes=index)).isoformat(),
        })
    return {"model_name": "aegis-direction", "activate": False, "examples": examples}


def main() -> None:
    print(json.dumps(build_payload()))


if __name__ == "__main__":
    main()
