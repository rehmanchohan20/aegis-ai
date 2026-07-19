# Local verification report

Date: 2026-07-19 (Asia/Karachi)

Branch: `sprint/01-foundation`

## Verified outcomes

- Docker Desktop 29.6.1 and Compose are installed and running.
- Public Binance combined WebSocket connected for eight symbols and six kline timeframes.
- The backend persists one-second state for all eight symbols; observed average ingestion latency during smoke verification was approximately 2.3 ms.
- The authenticated dashboard SSE stream updates at roughly one-second cadence. BTC/1m and DOGE/5m selections were visually and interactively verified in the in-app browser.
- Market structure returned confirmed pivots, HH/HL or transition state, support/resistance, ATR bands, markers, and trend-line metadata and rendered through Lightweight Charts.
- Flyway V1 through V11 applied successfully to a completely empty disposable TimescaleDB database. Both directional setup snapshot/backtest tables and their lookup indexes exist.
- Eight-symbol directional setup responses were exercised with 48 profile bins each. DOGE exposed a nullable structure-anchor crash during the first runtime pass; the fix and regression test were rebuilt and the second pass produced no setup errors.
- Backend-to-ML traffic now uses explicit HTTP/1.1. This fixed an h2c/Uvicorn interoperability defect that discarded prediction request bodies. With no approved artifact, the service now correctly returns 503 and the setup remains non-actionable.
- The setup engine displays fixed/session volume profiles, POC, VAH/VAL, HVN/LVN, developing POC, value state, directional bias, entry/invalidation/targets, expected R, setup quality and target-first probability source.
- Admin-only setup backtesting was exercised. Viewer receives 403 and anonymous receives 401. The first BTCUSDT/1m 500-candle research run found only three eligible filled setups, all losses (0% win, -19.64685068 average R, zero profit factor, 58.94055204R drawdown). This is an explicitly exploratory, negative result and does not support a profitability or activation claim.
- Runtime chronological training observations are persisted once per closed symbol/timeframe candle only when the full feature vector is usable.
- Prediction performance SQL was exercised against PostgreSQL; an initially reserved alias was found, fixed, rebuilt, and reverified with HTTP 200.
- Wrong ML deployment token returned HTTP 403. Real-money execution remained disabled and fail-closed.
- A live-order safety smoke returned `BLOCKED_LIVE_DISABLED`; no venue adapter or real order path was enabled.

## Build and test results

- Backend: 51 unit/security/statistical tests passed; no failures, errors, or skips.
- Frontend: ESLint, strict TypeScript, and Vite production build passed.
- ML: compile/import passed; 10 deterministic tests passed. Candidate/train/explicit-token activation/predict smoke passed with calibrated three-class probabilities, margin, conformal set, entropy, drift contributions, expected return/volatility, path probabilities, and trade-quality fields.
- Docker: development and production Compose configurations rendered; full development stack built and all five services reached healthy/running state.

## Important safety result

No synthetic candidate was activated in the Docker live runtime. The production-path dashboard correctly reports no approved model until enough real chronological observations have matured into labels and a candidate passes deployment approval. ML remains confirm/veto-only and cannot originate a trade.

## Known external/data-time constraint

The clean live database has begun accumulating observations, but the 15-minute label horizon and 150-sample promotion minimum require real elapsed market time before a statistically defensible model can be trained and approved. This is intentional; the system does not fabricate labels or activate a smoke model to make the dashboard appear populated.

## Primary commands

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd' -B -ntp clean verify

cd frontend
npm install
npm run lint
npm run build

cd ..\ml-service
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m compileall app scripts tests
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m scripts.smoke_train
.\.venv\Scripts\python.exe -c "from app.main import app; print(app.title)"

cd ..
docker compose config
docker compose -f docker-compose.prod.yml config
docker compose up -d --build
```
