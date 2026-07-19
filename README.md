# AEGIS AI

AEGIS is a local-first quantitative research and guarded paper-trading platform. It combines explainable rules, chronological ML validation, prediction outcome maintenance, model-health monitoring, risk sizing, paper execution, and normalized strategy/model attribution. Real-money execution is disabled by default and deliberately fail-closed.

## Architecture

- **Backend:** Java 21, Spring Boot 3.5, Maven, JDBC, Flyway, Spring Security API keys, Actuator, Prometheus.
- **Data:** PostgreSQL 16 with TimescaleDB. Flyway creates market, trading, intelligence, and execution structures.
- **ML service:** Python 3.12, FastAPI, pandas/NumPy, scikit-learn, joblib versioned artifacts.
- **Dashboard:** React, strict TypeScript, Vite, responsive local operations dashboard, Nginx production image.
- **Safe pipeline:** closed candles → features → configured strategies → rules decision → drift-aware ML confirm/veto → supervisor → risk → paper trade → monitoring → journal/attribution → prediction resolution → model health.

ML never originates a trade. `WAIT` is neutral, degraded ML cannot veto a valid rules signal, and a halted/drifted prediction is forced to `WAIT`.

## Prerequisites

- IntelliJ IDEA with a Java 21+ project SDK (the repository targets Java 21).
- Maven 3.9+ (IntelliJ's bundled Maven is supported).
- Node.js 22 recommended (20+ works for the current build).
- Python 3.12.
- Docker Desktop with Compose v2 for database and full-stack verification.

No exchange credentials are needed or expected.

## Environment

Copy `.env.example` to `.env` for safe local Docker development. Production uses `.env.production.example` as a template; replace every placeholder. Never commit `.env` files or exchange secrets.

Important variables:

| Variable | Purpose | Safe default |
|---|---|---|
| `AEGIS_VIEWER_API_KEY` | Read-only API access | local development key |
| `AEGIS_ADMIN_API_KEY` | Model administration/execution API access | local development key |
| `AEGIS_LIVE_EXECUTION_ENABLED` | Master live-execution flag | `false` |
| `AEGIS_EXECUTION_CONFIRMATION_TOKEN` | Separate live confirmation | `DISABLED` |
| `BINANCE_INGESTION_ENABLED` | External candle stream | local Compose uses `false` |
| `ML_LABEL_HORIZON_MINUTES` | Prediction outcome horizon | `15` |
| `ML_NEUTRAL_RETURN_THRESHOLD` | WAIT return band, absolute decimal | `0.001` |
| `ML_HEALTH_MINIMUM_SAMPLE_SIZE` | Minimum resolved health window | `30` |
| `ML_PROMOTION_*` | Promotion quality gates | see examples |

API calls use `X-AEGIS-API-KEY`:

```powershell
$headers = @{ 'X-AEGIS-API-KEY' = 'local-viewer-change-me' }
Invoke-RestMethod http://localhost:8080/api/v1/dashboard -Headers $headers
```

## Windows PowerShell development

Start infrastructure:

```powershell
docker compose up -d timescaledb redis
docker compose ps
```

Use the IntelliJ project SDK and bundled Maven to run `ai.aegis.AegisApplication`. Configure these safe environment variables in the run configuration:

```text
DB_URL=jdbc:postgresql://localhost:5432/aegis
DB_USERNAME=aegis
DB_PASSWORD=aegis
AEGIS_VIEWER_API_KEY=local-viewer-change-me
AEGIS_ADMIN_API_KEY=local-admin-change-me
AEGIS_LIVE_EXECUTION_ENABLED=false
BINANCE_INGESTION_ENABLED=false
ML_BASE_URL=http://localhost:8000
```

ML service:

```powershell
cd ml-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Dashboard:

```powershell
cd frontend
npm install
$env:VITE_AEGIS_API_KEY='local-viewer-change-me'
npm run dev
```

Open `http://localhost:3000` for Vite development or `http://localhost:3000` for the local full Compose stack.

## Builds and tests

```powershell
cd backend
mvn -B -ntp clean verify
cd ..\frontend
npm install
npm run build
cd ..\ml-service
.\.venv\Scripts\python.exe -m compileall app tests
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -c "from app.main import app; print(app.title)"
```

Ordinary unit tests do not require a developer database. Clean Flyway/integration verification requires TimescaleDB.

## Full safe stack

```powershell
docker compose up --build
```

Services: dashboard `:3000`, backend `:8080`, ML `:8000`, PostgreSQL `:5432`, Redis `:6379`.

Production configuration validation/startup:

```powershell
Copy-Item .env.production.example .env.production
# Replace every placeholder before continuing.
docker compose --env-file .env.production -f docker-compose.prod.yml config
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

## ML training and activation

Training accepts at least 150 chronologically ordered labelled examples. Each example must contain the exact same finite feature set and label `-1` (SHORT), `0` (WAIT), or `1` (LONG). Training produces a **candidate** only unless `activate` is explicitly requested; normal promotion is separate.

```powershell
& .\.venv\Scripts\python.exe .\scripts\generate_synthetic_training.py | Set-Content .\.local-training.json
$payload = Get-Content .\.local-training.json -Raw
Invoke-RestMethod -Method Post -Uri http://localhost:8000/train -ContentType application/json -Body $payload
Invoke-RestMethod http://localhost:8000/models
```

For a deterministic in-process candidate/activation/prediction smoke test:

```powershell
.\.venv\Scripts\python.exe -m scripts.smoke_train
```

Validation uses a chronological `TimeSeriesSplit` with a configurable embargo gap between train and validation windows. It reports balanced accuracy, macro F1, Matthews correlation, Cohen's kappa, multiclass Brier score, log loss, per-class precision/recall, confusion matrices, class distribution, directional coverage/error, component results, fold dispersion, and worst-fold performance. Moving-block bootstrap intervals preserve local serial dependence and provide 95% uncertainty bounds for the principal scores. Promotion can require the lower confidence bound and worst-fold score, rather than trusting a favorable point estimate.

The Random Forest, Extra Trees, and Histogram Gradient Boosting components are evaluated separately and as a soft-voting ensemble. Probability calibration is fitted only on chronological splits. Decision thresholds are chosen from out-of-fold validation probabilities. A conformal prediction set, normalized entropy, probability margin, robust feature drift, and schema checks force uncertain observations to `WAIT`; this is an abstention mechanism, not a promise of predictive accuracy.

Closed-candle feature engineering includes realized and downside volatility, bias-corrected return skewness and excess kurtosis, lag-one return autocorrelation, Parkinson and Garman-Klass range volatility, a log-price trend t-statistic, volume z-score, and Amihud illiquidity. Training labels use a volatility-adaptive neutral band with a configured floor. All feature order, reference medians, robust scales, and reference quantiles are stored with the artifact; missing, extra, NaN, or infinite inputs are rejected.

Register the candidate with the backend admin API, evaluate it against configurable promotion thresholds, then activate the approved ID. Activation verifies approval, local artifact readability, feature schema, and records an audit row. Rollback uses `POST /api/v1/admin/models/rollback` and requires the historical version to remain approved and readable.

## Prediction lifecycle

1. Guarded execution records symbol/timeframe, strategy, model version, rule/ML directions, three probabilities, confidence/margin, drift, feature snapshot, timestamp, and starting market price.
2. The scheduled reconciler waits for `ML_LABEL_HORIZON_MINUTES` and selects the first closed candle at or after the horizon.
3. Forward return is calculated from the prediction-time price. Returns above/below the configured neutral band become LONG/SHORT; values inside become WAIT.
4. Correctness is direction equality. Resolved rows are never relabelled.
5. Scheduled health snapshots calculate accuracy, directional precision, confidence, calibration error, class total-variation shift, feature-drift population stability index (PSI), unresolved count, and recent sample size with `HEALTHY`, `WATCH`, `DEGRADED`, or `HALTED` status.

Read APIs:

- `GET /api/v1/dashboard`
- `GET /api/v1/predictions`
- `GET /api/v1/predictions/summary`
- `GET /api/v1/predictions/rolling`
- `GET /api/v1/paper-trades`
- `GET /actuator/health`
- ML: `GET /health`, `GET /models`

## Troubleshooting

- **Flyway fails on `timescaledb`:** use the TimescaleDB image, not plain PostgreSQL. Do not edit an applied migration; add a later migration.
- **Backend reports Java version errors:** select Java 21+ as IntelliJ's project SDK and Maven runner JRE.
- **No analysis yet:** at least 30–35 validated closed candles are required.
- **No active model:** expected after a clean install. Train, approve, and explicitly activate a compatible candidate.
- **Prediction stays pending:** confirm a closed candle exists at/after the configured horizon and the prediction has `starting_price`.
- **401:** provide the viewer/admin key in `X-AEGIS-API-KEY` and ensure Vite's `VITE_AEGIS_API_KEY` matches.
- **Live order blocked:** expected. The current implementation has no signed production venue adapter and remains fail-closed even if a flag is accidentally changed.

## Safety disclaimer

AEGIS is research and paper-trading software, not financial advice. Real-money trading is disabled by default. Do not add exchange secrets to this repository. A production venue integration must independently implement signed adapters, testnet verification, price/quantity filters, reconciliation, and idempotency before live execution can ever be considered.
