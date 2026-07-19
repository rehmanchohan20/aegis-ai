# AEGIS AI

AEGIS is a local-first, real-time quantitative research and guarded paper-trading platform. It consumes public Binance streams, maintains one-second multi-pair market state, derives multi-timeframe structure and microstructure features, combines explainable rules with approval-gated calibrated ML, simulates exchange-like paper orders, and measures every prediction and trade outcome.

Real-money trading is disabled. No production venue adapter or exchange credential is included.

## Architecture

- Java 21 / Spring Boot 3.5 backend: WebSocket ingestion, features, structure, rules, portfolio supervision, paper matching, persistence, security, scheduled reconciliation, attribution, Actuator, and Prometheus.
- PostgreSQL 16 / TimescaleDB: candles, one-second market snapshots, prediction history, model/drift registry, paper orders/fills, trade journal, and Flyway V1 onward.
- Python 3.12 / FastAPI ML service: robust preprocessing, calibrated multiclass ensemble, auxiliary return/volatility/path models, chronological holdout, purged walk-forward validation, feature schema/reference statistics, versioned artifacts.
- React / strict TypeScript / Vite / Lightweight Charts: authenticated SSE command center with live prices, candles, EMA/VWAP/ATR overlays, validated trend lines, structure, order flow, predictions, rankings, trades, attribution, and health.

```text
Binance book/trade/depth/kline streams
  -> deduplicated per-symbol state -> one-second Timescale snapshots + authenticated SSE
  -> closed-candle features + microstructure + confirmed-pivot market structure
  -> rules + regime + multi-timeframe + correlation + cost/risk gates
  -> calibrated ML confirm/veto (never originates trades)
  -> persisted paper order/fill -> monitored position -> journal/attribution
  -> prediction/path reconciliation -> rolling performance/drift/model health
```

## Defaults and configuration

Default markets: `BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT,ADAUSDT,DOGEUSDT,LINKUSDT`.

Default analysis timeframes: `1m,3m,5m,15m,1h,4h`.

Copy [.env.example](.env.example) to `.env` for local Compose. Use [.env.production.example](.env.production.example) only as a template and replace every placeholder. Never commit `.env`, passwords, exchange keys, or model-approval tokens.

| Variable | Purpose | Local behavior |
|---|---|---|
| `AEGIS_MARKET_SYMBOLS` | comma-separated stream universe | eight pairs above |
| `AEGIS_MARKET_INTERVALS` | analysis/kline timeframes | six timeframes above |
| `AEGIS_MARKET_SNAPSHOT_INTERVAL_MS` | snapshot/SSE cadence | `1000` |
| `BINANCE_INGESTION_ENABLED` | public market-data ingestion | `true` |
| `AEGIS_VIEWER_API_KEY` | read APIs | local non-secret key |
| `AEGIS_ADMIN_API_KEY` | admin/model APIs | local non-secret key |
| `AEGIS_MODEL_DEPLOYMENT_APPROVAL_TOKEN` | separate backend-to-ML activation gate | local non-secret token |
| `AEGIS_LIVE_EXECUTION_ENABLED` | live execution master switch | always `false` in Compose |
| `AEGIS_EXECUTION_CONFIRMATION_TOKEN` | separate live confirmation | `DISABLED` locally |
| `ML_LABEL_HORIZON_MINUTES` | prediction resolution horizon | `15` |
| `ML_NEUTRAL_RETURN_THRESHOLD` | realized WAIT zone | `0.001` |
| `ML_PROMOTION_*` | balanced accuracy/F1/precision/calibration/sample gates | see examples |

## Prerequisites

- Docker Desktop with Compose v2.
- IntelliJ IDEA with a Java 21 project SDK. IntelliJ 2026.1 bundled JBR and bundled Maven are supported; no system Java source tree is required.
- Node.js 22 and npm.
- Python 3.12 for host-side ML development.

## Start the complete safe stack

```powershell
docker compose up -d --build
docker compose ps
```

Open [http://localhost:3000](http://localhost:3000). Backend is on `:8080`, ML on `:8000`, TimescaleDB on host `:5433`, and Redis on `:6379`.

```powershell
$headers = @{ 'X-AEGIS-API-KEY' = 'local-viewer-change-me' }
Invoke-RestMethod http://localhost:8080/actuator/health -Headers $headers
Invoke-RestMethod http://localhost:8080/api/v1/market-data/status -Headers $headers
Invoke-RestMethod 'http://localhost:8080/api/v1/dashboard?symbol=ETHUSDT&interval=15m' -Headers $headers
Invoke-RestMethod http://localhost:8080/api/v1/pair-ranking -Headers $headers
Invoke-RestMethod http://localhost:8000/health
Invoke-RestMethod http://localhost:8000/models
```

The dashboard viewer key is compiled from `VITE_AEGIS_API_KEY` at image build time. Change both Compose/backend and frontend build values together.

## IntelliJ / host development on Windows

Start infrastructure:

```powershell
docker compose up -d timescaledb redis ml-service
```

Run `ai.aegis.AegisApplication` from IntelliJ with its Java 21 SDK and these safe variables:

```text
DB_URL=jdbc:postgresql://localhost:5433/aegis
DB_USERNAME=aegis
DB_PASSWORD=aegis
ML_BASE_URL=http://localhost:8000
AEGIS_VIEWER_API_KEY=local-viewer-change-me
AEGIS_ADMIN_API_KEY=local-admin-change-me
AEGIS_MODEL_DEPLOYMENT_APPROVAL_TOKEN=local-model-deployment-approval
AEGIS_LIVE_EXECUTION_ENABLED=false
BINANCE_INGESTION_ENABLED=true
```

ML development:

```powershell
cd ml-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Frontend development:

```powershell
cd frontend
npm install
$env:VITE_AEGIS_API_KEY='local-viewer-change-me'
npm run dev
```

## Builds and tests

Use IntelliJ's bundled Maven if `mvn` is not on PATH:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd' -B -ntp clean verify

cd ..\frontend
npm install
npm run lint
npm run build

cd ..\ml-service
.\.venv\Scripts\python.exe -m compileall app scripts tests
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -c "from app.main import app; print(app.title)"
```

Ordinary unit tests do not depend on a developer database. Flyway verification requires TimescaleDB and must be run against an empty database without editing old migrations.

## Model training, approval, activation, and rollback

Runtime training examples are captured once per closed symbol/timeframe observation only when the complete finite feature schema is usable. The scheduled labeler later assigns SHORT/WAIT/LONG without future leakage.

Train a candidate directly from PostgreSQL:

```powershell
cd ml-service
$env:AEGIS_TRAINING_DATABASE_URL='postgresql://aegis:aegis@localhost:5433/aegis'
$env:AEGIS_ML_URL='http://localhost:8000'
.\.venv\Scripts\python.exe -m scripts.train_from_postgres
```

At least 150 chronologically unique labelled examples with class diversity are required. Training never activates a model. It evaluates logistic, Random Forest, Extra Trees, Histogram Gradient Boosting, and the ensemble separately; uses chronological gaps and a final untouched holdout; calibrates only on temporal splits; and records balanced accuracy, macro F1, per-class precision/recall, log loss, Brier score, ECE/reliability, confusion matrix, fold stability, feature importance/redundancy, feature order, robust reference statistics, and the training range.

Register the returned artifact/version through `POST /api/v1/admin/models`, evaluate `/{id}/approval`, then call `/{id}/activate` with the admin API key. Activation requires a passing database approval, a readable shared artifact, feature compatibility, and the separate ML deployment token. Rollback uses `POST /api/v1/admin/models/rollback`, preserves audit history, and is subject to the same approval/artifact gates.

Synthetic generation under `ml-service/scripts` exists only for deterministic tests/smoke verification; it is not used by the live runtime path and does not claim predictive quality.

## Prediction result lifecycle

Each prediction stores its symbol/timeframe/strategy/model, rule/ML/final directions, calibrated LONG/WAIT/SHORT probabilities, expected return/volatility, trade-quality and path probabilities, starting price, evaluation horizon, features, regime, trend/order-flow context, and risk levels.

The scheduled reconciler:

1. waits for the row-specific horizon;
2. selects the first closed candle at or after it;
3. calculates forward return from the exact prediction starting price;
4. assigns LONG/WAIT/SHORT from the configured neutral zone;
5. walks closed candles chronologically to determine stop-first/target-first, leaving a same-candle double hit explicitly ambiguous;
6. updates only unresolved rows and never relabels history.

Read `/api/v1/predictions/summary`, `/rolling`, and `/performance`. The performance endpoint supports symbol, timeframe, regime, strategy, model version, and rolling sample limits and reports class metrics, Brier/log loss, expected versus realized return, conversion, win rate, profit factor, Sharpe, Sortino, and drawdown when linked trades exist.

## Live APIs

- `/api/v1/markets`
- `/api/v1/market-data/latest`, `/history`, `/status`, `/stream`
- `/api/v1/market-structure`
- `/api/v1/pair-ranking`, `/correlation`
- `/api/v1/dashboard`
- `/api/v1/predictions`, `/summary`, `/rolling`, `/performance`
- `/api/v1/paper-orders`, `/api/v1/paper-trades`
- `/actuator/health`, `/actuator/prometheus`
- ML `/health`, `/models`, `/predict`, `/train`

All `/api` routes require `X-AEGIS-API-KEY`; admin routes require the admin key. SSE uses authenticated `fetch` streaming because native `EventSource` cannot set the API-key header.

## Troubleshooting

- Docker is installed but containers do not start: ensure Docker Desktop is running, then use `docker compose ps` and `docker compose logs backend ml-service`.
- Port 5432 is already PostgreSQL: local Compose deliberately publishes TimescaleDB on `5433`.
- No prediction probabilities: expected on a clean install until a candidate passes promotion and is explicitly activated. Rules still display, but new paper trade approval fails closed.
- No training examples: wait for a full closed candle with `GOOD` live data and a complete feature vector; incomplete schemas are deliberately rejected.
- Stream is stale: inspect `/api/v1/market-data/status`. The client reconnects and resubscribes with exponential backoff.
- 401/403: make the viewer/admin key and the frontend build key agree. Model deployment additionally requires its separate token.
- Flyway failure: use TimescaleDB, verify from an empty database, and add a later migration—never edit an already-applied one.

## Production Compose and safety

```powershell
docker compose -f docker-compose.prod.yml config
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

Production Compose does not expose the ML service or database publicly. Replace every placeholder before starting it.

AEGIS is research software, not financial advice. The repository has no signed production order adapter. Public Binance data may be consumed, but every generated order remains a paper order. Live execution stays fail-closed unless a future implementation independently provides an explicit flag, separate confirmation, admin authentication, signed adapter, testnet verification, venue filters, reconciliation, and idempotency.
