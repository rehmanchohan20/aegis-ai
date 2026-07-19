"""Register a chronology-preserving candidate from labelled PostgreSQL examples."""

from __future__ import annotations

import os
from typing import Any

import httpx
import psycopg


def load_examples() -> list[dict[str, Any]]:
    connection_string = os.environ.get(
        "AEGIS_TRAINING_DATABASE_URL", "postgresql://aegis:aegis@localhost:5433/aegis")
    minimum_examples = int(os.environ.get("AEGIS_TRAINING_MINIMUM_EXAMPLES", "150"))
    with psycopg.connect(connection_string) as connection:
        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT features, label, created_at, forward_return
                FROM intelligence.ml_examples
                WHERE label IS NOT NULL AND labelled_at IS NOT NULL
                ORDER BY created_at ASC
            """)
            rows = cursor.fetchall()
    if len(rows) < minimum_examples:
        raise RuntimeError(f"Only {len(rows)} labelled examples; at least {minimum_examples} are required")
    return [{"features": row[0], "label": int(row[1]), "observedAt": row[2].isoformat(),
             "forwardReturn": float(row[3]) if row[3] is not None else None} for row in rows]


def main() -> None:
    endpoint = os.environ.get("AEGIS_ML_URL", "http://localhost:8000").rstrip("/")
    model_name = os.environ.get("AEGIS_MODEL_NAME", "aegis-direction")
    response = httpx.post(f"{endpoint}/train", json={
        "model_name": model_name,
        "activate": False,
        "validation_gap": int(os.environ.get("AEGIS_VALIDATION_GAP", "3")),
        "examples": load_examples(),
    }, timeout=900)
    response.raise_for_status()
    candidate = response.json()
    print({"status": "CANDIDATE_REGISTERED", "model": candidate["modelName"],
           "version": candidate["version"], "artifact": candidate["artifact"]})


if __name__ == "__main__":
    main()
