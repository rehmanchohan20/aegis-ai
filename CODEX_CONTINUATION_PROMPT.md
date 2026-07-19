# AEGIS Local Codex Continuation Prompt

Copy everything below into Codex while your terminal is opened at the repository root.

---

You are the lead engineer continuing the AEGIS AI quantitative research and guarded paper-trading platform in this local repository.

## Repository and branch

- Repository: `rehmanchohan20/aegis-ai`
- Required branch: `sposprint/01-foundation`
- Work directly on this branch unless I explicitly request another branch.
- Do not create a pull request.
- Do not enable real-money trading.
- Preserve all existing work and database migrations.

## Mission

Take the current repository from its present state to a locally verified, reliable, explainable, drift-aware paper-trading and ML research platform. Run all builds and tests locally, fix every error you encounter, improve weak implementations, and leave the repository in a reproducible state.

Do not merely describe changes. Inspect files, edit code, run commands, diagnose failures, rerun tests, and continue until the verification checklist is satisfied or a genuinely external blocker remains.

## Safety constraints

1. Real-money order placement must remain disabled by default.
2. No exchange API secret may be committed.
3. Live execution must remain fail-closed unless all of the following are present:
   - explicit environment flag;
   - explicit confirmation token;
   - installed signed venue adapter;
   - testnet verification;
   - quantity and price filter validation;
   - reconciliation and idempotency.
4. ML predictions may confirm or veto rule signals but may not independently place orders.
5. Never train using future information or shuffled time-series splits.
6. Never silently ignore failed persistence, failed migrations, missing model artifacts, or invalid market data.

## Current architecture

### Backend

- Java 21
- Spring Boot 3.5.x
- Maven
- PostgreSQL / TimescaleDB
- Flyway
- Spring Security API keys
- Actuator and Prometheus

### Frontend

- React
- TypeScript
- Vite
- Lightweight Charts
- Nginx production container

### ML service

- Python 3.12
- FastAPI
- pandas / NumPy
- scikit-learn
- joblib
- Versioned model artifacts

### Core pipeline

`Binance candles -> feature engineering -> configured strategies -> decision cycle -> ML ensemble -> supervisor -> trade admission -> risk sizing -> paper execution -> automatic monitoring -> journal -> strategy attribution -> prediction outcome tracking -> model health`

## Recently added prediction enhancements

Verify and complete these implementations:

- `V5__prediction_observability.sql`
- `PredictionAuditService`
- `PredictionResultsController`
- `MlPrediction.waitProbability`
- Prediction auditing from `GuardedExecutionService`
- ML ensemble using Random Forest, Extra Trees, and Histogram Gradient Boosting
- Chronological `TimeSeriesSplit` validation
- Multiclass `SHORT / WAIT / LONG` prediction
- Confidence-margin neutralization
- Feature drift scoring
- Prediction history and result summaries

## Required work order

### Phase 1 — Repository inspection

Run:

```bash
git status
git branch --show-current
git log --oneline -10
```

Confirm the branch is `sprint/01-foundation`. Inspect the complete repository tree and read the main backend, frontend, ML, Docker, Flyway, CI, security, and configuration files before editing.

### Phase 2 — Local build verification

Run the backend build:

```bash
cd backend
mvn -B -ntp clean verify
cd ..
```

Run the frontend build:

```bash
cd frontend
npm install
npm run build
cd ..
```

Run ML validation:

```bash
cd ml-service
python -m venv .venv
```

Windows PowerShell:

```powershell
.\.venv\Scripts\Activate.ps1
```

Windows CMD:

```cmd
.venv\Scripts\activate.bat
```

Linux/macOS:

```bash
source .venv/bin/activate
```

Then:

```bash
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m compileall app
python -c "from app.main import app; print(app.title)"
cd ..
```

Fix every compilation, type, import, test, serialization, Spring wiring, SQL, and dependency issue. Rerun commands until all pass.

### Phase 3 — Database and integration verification

Start infrastructure:

```bash
docker compose up -d timescaledb redis
```

Wait for PostgreSQL health. Then run the backend with safe local variables. Use non-secret local development API keys.

Verify all Flyway migrations V1 through V5 apply successfully to a clean database.

Check specifically:

- all schemas exist before tables are created;
- `execution` schema exists before `execution.order_audit`;
- JSONB parameters and JDBC casts compile and execute;
- unique active-model constraints behave correctly;
- nullable prediction outcomes are handled safely;
- no query assumes a row exists when none may exist;
- migrations work on a completely empty database.

### Phase 4 — Prediction result maintenance

Complete automatic outcome reconciliation for `intelligence.prediction_history`.

Requirements:

1. Scheduled service resolves predictions after a configurable horizon.
2. Match the first closed candle at or after the horizon.
3. Calculate forward return from the market price at prediction time.
4. Resolve outcome as:
   - `1` LONG when return is above positive threshold;
   - `-1` SHORT when below negative threshold;
   - `0` WAIT inside the neutral zone.
5. Set `correct` using the predicted direction and outcome.
6. Never relabel an already resolved prediction.
7. Persist resolution timestamp.
8. Add unit and repository tests.
9. Expose pending, resolved, accuracy, directional precision, average confidence, calibration error, and rolling results by model version.

If the prediction table lacks the starting price needed for correct forward returns, add a new Flyway migration rather than altering an old migration.

### Phase 5 — Prediction quality upgrades

Improve the ML service carefully:

1. Keep chronological validation only.
2. Add robust feature preprocessing using a pipeline where appropriate.
3. Evaluate ensemble components individually and together.
4. Record:
   - balanced accuracy;
   - macro F1;
   - per-class precision and recall;
   - log loss;
   - confusion matrix;
   - validation sample count;
   - class distribution.
5. Add probability calibration using a method valid for time-series data. Avoid data leakage.
6. Choose thresholds using validation data rather than hard-coded 0.55 where possible.
7. Prefer WAIT when:
   - probability margin is weak;
   - model entropy is high;
   - feature drift is high;
   - active model is missing;
   - model metadata is incompatible.
8. Persist the exact feature order and training reference statistics.
9. Reject missing, extra, infinite, or NaN features with useful errors.
10. Add deterministic tests using synthetic chronological datasets.
11. Keep model activation separate from training unless explicitly requested.
12. Do not claim a model is good based only on accuracy.

### Phase 6 — Drift and model health

Implement scheduled model monitoring.

Calculate at minimum:

- rolling prediction accuracy;
- directional precision;
- calibration error;
- average confidence;
- class distribution shift;
- feature population stability index or a defensible equivalent;
- unresolved prediction count;
- recent sample size.

Persist snapshots to `intelligence.model_drift_snapshot`.

Statuses:

- `HEALTHY`
- `WATCH`
- `DEGRADED`
- `HALTED`

A degraded or halted model must not veto otherwise valid rule signals without an explicit policy decision. A halted model must return WAIT and be excluded from execution approval.

### Phase 7 — Model promotion and rollback

Verify and harden:

- candidate registration;
- walk-forward approval;
- one active model per model name;
- rollback to approved historical versions;
- artifact existence verification;
- feature-schema compatibility;
- audit trail for activation and rollback.

Promotion should require configurable minimums for:

- balanced accuracy;
- macro F1;
- directional precision;
- out-of-sample Sharpe or strategy effectiveness;
- maximum drawdown;
- minimum validation sample size;
- acceptable calibration error.

### Phase 8 — Strategy and model attribution

Ensure every paper trade maintains:

- strategy ID;
- model version;
- rules direction;
- ML direction;
- probabilities;
- feature snapshot reference;
- signal score;
- risk plan;
- realized P&L;
- closure reason.

Avoid parsing critical metadata from free-form rationale strings. Add normalized database columns or linking tables through new migrations.

Produce aggregates by:

- strategy;
- model version;
- symbol;
- timeframe;
- market regime;
- LONG versus SHORT;
- rolling date window.

### Phase 9 — Dashboard enhancement

Update the React dashboard to use the unified APIs and show:

- latest rules decision;
- ML LONG / WAIT / SHORT probabilities;
- confidence margin;
- drift status;
- active model version;
- recent prediction accuracy;
- pending prediction outcomes;
- strategy P&L attribution;
- model-version P&L attribution;
- open paper positions;
- closed trades;
- health and kill-switch states;
- API errors and loading states.

Requirements:

- preserve strict TypeScript typing;
- do not use `any` unless unavoidable and documented;
- handle HTTP 204 and non-JSON errors;
- make the API key configurable through environment variables;
- do not hard-code production secrets;
- keep the interface responsive.

### Phase 10 — Tests

Add tests for at least:

- temporal split ordering;
- model output class mapping;
- neutral decision on weak margin;
- neutral decision on high drift;
- missing feature rejection;
- prediction audit persistence;
- outcome reconciliation;
- correctness calculation;
- model health status transitions;
- model activation refusal without approval;
- rollback;
- strategy/model attribution on closure;
- security access for viewer versus admin;
- live execution remains blocked by default.

Use Testcontainers for PostgreSQL integration tests when practical. Do not make ordinary unit tests depend on a developer’s local database.

### Phase 11 — End-to-end local smoke test

Run the full safe stack:

```bash
docker compose up --build
```

Verify:

```text
GET  /actuator/health
GET  /api/v1/dashboard
GET  /api/v1/predictions
GET  /api/v1/predictions/summary
GET  /health on ML service
GET  /models on ML service
```

Create synthetic labelled examples and train a candidate model. Verify prediction responses include:

- `longProbability`
- `waitProbability`
- `shortProbability`
- `decision`
- `confidence`
- `model`
- `featureDriftScore`
- `driftStatus`

Do not enable live exchange execution during smoke testing.

### Phase 12 — Documentation and reproducibility

Update `README.md` with:

- architecture;
- exact prerequisites;
- Windows PowerShell commands;
- local development startup;
- production compose startup;
- environment variables;
- API-key usage;
- ML training example;
- model activation workflow;
- prediction result lifecycle;
- troubleshooting;
- safety disclaimer.

Create or update:

- `.env.example`
- `.env.production.example`
- local seed/sample scripts where useful;
- a concise verification report.

## Coding standards

- Java records for immutable response/value types where appropriate.
- Constructor injection only.
- Validate all public service inputs.
- Return immutable collections.
- Use UTC timestamps.
- Do not catch broad exceptions without logging and preserving safe behavior.
- Keep SQL parameterized.
- Add indexes for scheduled lookup paths.
- Do not edit existing Flyway migrations after they may have been applied; add new migrations.
- Python must include type hints and clear validation.
- TypeScript must compile under strict mode.
- Never hide a failing test by deleting or disabling it without explaining and replacing the coverage.

## Required final verification

Before stopping, rerun:

```bash
cd backend && mvn -B -ntp clean verify && cd ..
cd frontend && npm run build && cd ..
cd ml-service && python -m compileall app && python -c "from app.main import app; print(app.title)" && cd ..
docker compose config
docker compose -f docker-compose.prod.yml config
```

Also run relevant integration and smoke tests.

## Final response format

At completion report:

1. Bugs found.
2. Files changed.
3. Prediction improvements.
4. Data/result-maintenance improvements.
5. Security and safety status.
6. Exact commands executed.
7. Backend test result.
8. Frontend build result.
9. ML validation result.
10. Docker configuration result.
11. Remaining external blockers, if any.
12. Latest commit SHA.

Continue autonomously. Do not ask me to manage individual steps. Ask a question only when credentials, destructive database action, or an irreversible real-money action would be required.

---
