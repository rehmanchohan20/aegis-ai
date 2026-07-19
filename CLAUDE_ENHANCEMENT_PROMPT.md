# AEGIS AI — Claude quantitative enhancement prompt

Copy everything below into Claude Code while its terminal is opened at this repository root.

---

You are the senior quantitative engineer reviewing and extending the existing AEGIS AI repository. Work on the current `sprint/01-foundation` branch. Inspect the working tree, `CODEX_CONTINUATION_PROMPT.md`, `README.md`, all Flyway migrations, and the implementation before editing. Preserve working behavior and historical migrations. Do not create a pull request, switch branches, enable real-money execution, or commit credentials.

## Product and safety contract

AEGIS is a local-first, explainable, drift-aware, multi-symbol quantitative research and guarded paper-trading platform. It consumes public Binance WebSocket data, calculates closed-candle and live microstructure state, evaluates rules and market structure, allows approved calibrated ML only to confirm or veto a rules signal, performs realistic paper execution, and reconciles predictions and trades against later outcomes.

Real-money trading must remain disabled and fail-closed. There is no signed production venue adapter. ML must never originate an order. Missing/stale data, unavailable or incompatible artifacts, halted model/strategy health, weak calibration, insufficient liquidity, excessive costs, poor reward/risk, correlation limits, and daily risk limits must block approval. Do not weaken these invariants even for demos.

Never use random time-series splitting, future candle values, overlapping-label contamination, final values of an unclosed candle, global normalization fitted on validation/holdout data, or post-event pivots in historical signals. Do not claim predictive performance without an untouched chronological holdout and uncertainty around the result.

## What is already built

### Runtime architecture

- Java 21 / Spring Boot 3.5 backend with constructor injection, records, validation, Spring Security API keys, scheduled services, Actuator, Prometheus, PostgreSQL/TimescaleDB, and Flyway.
- Python 3.12 / FastAPI ML service using pandas, NumPy, scikit-learn and joblib with versioned immutable artifacts.
- React / strict TypeScript / Vite / Lightweight Charts command center behind Nginx.
- Docker Compose development and production definitions. Local defaults are non-secret; production examples contain placeholders.
- Configurable default universe: BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT, XRPUSDT, ADAUSDT, DOGEUSDT, LINKUSDT. Timeframes: 1m, 3m, 5m, 15m, 1h, 4h.

### Market data and execution

- Binance public combined WebSocket streams for book ticker, aggregate trades, depth, and klines.
- Per-symbol deduplication/out-of-order protection, exchange and receive timestamps, ingestion latency, stale/disconnected state, reconnect/resubscribe backoff, one-second snapshots, and authenticated SSE to the dashboard.
- Spread, weighted mid, book/depth imbalance, aggressive flow, CVD, trade intensity/size, realized volatility, volume acceleration, and explicit unsupported-feature states.
- Confirmed-pivot structure, HH/HL/LH/LL, BOS, ChoCH, support/resistance, regression trend lines with touch/break/confidence metadata, consolidation/breakout/retest/liquidity-sweep/FVG warnings, and ATR bands.
- Persisted paper orders/fills/trades with order types, partial fills, spread, slippage, fees, exposure, drawdown, cooldown, stop/targets, trailing/break-even/partial exits, attribution, MFE/MAE, and closure context.

### Prediction lifecycle and model governance

- Multiclass SHORT / WAIT / LONG prediction with calibrated probabilities, confidence margin, entropy/uncertainty neutralization, feature drift, expected return, expected volatility, stop-first and target-first probabilities, and trade quality.
- Robust preprocessing and strict finite feature-schema/order validation. Artifacts persist training range, feature order, reference statistics, metrics, importance, and config metadata.
- Chronological validation, temporal gaps, purged walk-forward evaluation, untouched final holdout, individual component and ensemble metrics, balanced accuracy, macro F1, per-class precision/recall, log loss, Brier, expected calibration error, reliability data, confusion matrix, class distributions, fold stability, and sample count.
- Candidate registration is separate from activation. Promotion requires database approval thresholds, compatible shared artifact, deployment approval token, and audit trail. Historical approved rollback is supported.
- Prediction history stores probabilities, decisions, price, horizon, contexts, risk levels, expected outputs, later realized return/class, correctness, path result, and resolution timestamp.
- Scheduled outcome reconciliation resolves only unresolved rows using the first closed candle at/after horizon and does not invent intrabar ordering when stop and target hit in one candle.
- Rolling performance, model-drift snapshots, HEALTHY/WATCH/DEGRADED/HALTED states, and strategy/model/symbol/timeframe/regime attribution exist.

### Selective directional setup engine (Flyway V11)

The newest engine intentionally does not predict every candle:

1. `DirectionalBiasService` calculates closed-candle 15m/1h/4h bias from EMA20/EMA50 separation, EMA slope, and confirmed structure. It remains WAIT unless 1h and 4h agree.
2. `VolumeProfileService` calculates fixed-range and UTC-session profiles, POC, 70% VAH/VAL, HVN/LVN, developing POC, acceptance/rejection, and range state.
3. Because Binance klines lack tick-level price-at-volume, volume is allocated across each candle's intersected price bins with triangular typical-price weighting. This limitation is embedded in the API. Do not relabel this approximation as tick volume profile.
4. `DirectionalSetupService` searches only in bias direction for a volume-confirmed BOS followed by pullback into value, breakout retest, or value/VWAP reclaim/rejection. It generates an entry zone, structure/value/ATR-buffered stop, liquidity-derived targets, expected R, quality, confirmations, rejection reasons, warnings, and chart markers.
5. Live approval requires good/fresh data, acceptable spread and top-book depth, no strong opposing imbalance, aligned structure, a defensible target of at least 2.5R, and an approved calibrated ML direction whose target-first probability is greater than stop-first and greater than 50%.
6. `GuardedExecutionService` now requires an actionable setup whose direction matches the requested paper order.
7. `DirectionalSetupBacktestService` reconstructs information sets at each decision time, allows entry only from the signal-close boundary onward, uses a bounded entry window, charges 7.5 bps per side plus 2 bps slippage, conservatively counts same-candle stop/target collision as a stop, and reports win rate, average R, expectancy, profit factor, maximum R drawdown, trades, warnings, and pair/timeframe/structure-regime breakdown.
8. `V11__directional_setup_engine.sql` persists setup snapshots and backtest reports with indexed lookup paths.
9. APIs: viewer GET `/api/v1/directional-setups` and `/bias`; admin POST `/api/v1/directional-setups/backtest`.
10. The dashboard draws the fixed profile, POC/VAH/VAL, developing POC, entry zone, stop, targets, BOS/ChoCH/retest markers, directional bias, expected R, setup quality, and target-first probability.

## Your mission

First reproduce the current green builds and inspect actual stored/live data. Then improve quantitative validity and predictive decision quality without adding fake sophistication. Prefer a smaller number of measurable, stable features and models over a large unvalidated feature set.

### 1. Audit the selective setup for leakage and state consistency

- Trace every field from exchange event/candle through profile, structure, bias, model request, setup, execution, persistence, dashboard, and backtest.
- Prove that pivots, BOS, trend lines, higher-timeframe bars, session/fixed profiles, developing POC, and all entry conditions use only information available at decision time.
- Align live and historical feature contracts. If a backtest cannot reproduce live microstructure inputs, mark the missing inputs and run a candle-only research variant; never substitute invented order flow.
- Use a deterministic setup identity or deduplication key so the scheduler does not treat the same market observation as independent repeated setups.
- Validate symbol/timeframe against configured allowlists and use UTC consistently.
- Add retention/partition strategy only through a new Flyway migration; never edit V1–V11.

### 2. Validate and improve volume profile mathematically

- Benchmark the current kline allocation against aggregate-trade-derived volume-at-price for periods where raw trades are captured.
- Quantify POC/VA boundary error and setup sensitivity to bin count, range selection, and allocation kernel.
- Consider tick-size-aware adaptive bins or Freedman–Diaconis/Scott-style widths with venue tick constraints. Avoid unstable bins that change signal meaning between assets.
- Define sessions explicitly in UTC and optionally configurable Asia/London/New York windows. Handle partial sessions and daylight-saving policy explicitly.
- Add profile shape metrics: concentration/entropy, bimodality, skew, value migration, POC velocity, HVN/LVN persistence, excess tails, and acceptance duration. Retain only features with stable contribution.
- Test incremental profile updates against full recomputation to prevent developing-POC drift.

### 3. Improve labels and target/stop competing-risk estimates

- Treat take-profit-first and stop-first as competing time-to-event outcomes with right censoring, not just independent binary classifications.
- Compare calibrated cause-specific classifiers, discrete-time hazards, survival forests where justified, and a transparent regularized baseline.
- Use the exact proposed entry/stop/target geometry available at prediction time when constructing labels.
- Purge and embargo folds by maximum holding horizon so paths do not overlap between train and validation.
- Report target-first, stop-first, neither/censored, and ambiguous rates by symbol/timeframe/regime/setup type.
- Calibrate out-of-fold temporal predictions only. Evaluate reliability overall and conditionally by side, asset, regime, volatility and setup type. Do not fit calibration on the final holdout.
- Add confidence intervals using block bootstrap or another dependence-aware method.

### 4. Improve directional and trade-quality models

- Keep a calibrated multinomial logistic baseline and compare tree ensembles independently and together.
- If adding LightGBM/XGBoost/CatBoost, make dependencies optional, pinned, reproducible, CPU-safe, and justified by walk-forward evidence. Do not make runtime startup depend on unavailable native binaries.
- Consider separate experts by regime only when every expert has sufficient effective samples; otherwise use regime as a feature with regularization.
- Optimize thresholds on temporal validation for net expectancy after spread/slippage/fees and minimum directional precision, not raw accuracy.
- Add abstention/selective-classification curves: coverage versus precision, expectancy and calibration. A lower-coverage engine is acceptable and intended.
- Evaluate probability averaging, stacked generalization with strictly out-of-fold temporal meta-features, and regime-weighted ensembles. Reject an ensemble that does not beat its best stable component after costs.
- Quantify uncertainty with conformal prediction or calibrated prediction sets where exchangeability assumptions are discussed and monitored.
- Measure importance with temporal permutation or drop-column analysis; treat impurity importance as descriptive only.

### 5. Backtest and statistical evidence

- Backtest every setup type with event-driven chronology, no same-observation duplicate entries, portfolio concurrency, capital/exposure constraints, correlation limits, order expiry, realistic spread/slippage/fees, and conservative intrabar uncertainty.
- Separate setup detection, order placement, fill, and exit timestamps.
- Report eligible observations, setup coverage, orders, fills, rejected orders, trades, win rate, average/median R, expectancy, profit factor, maximum drawdown, Sharpe, Sortino, turnover, exposure time, holding duration, MFE/MAE, calibration and conversion.
- Break down by pair, timeframe, side, regime, setup type, volatility bucket, calendar period and model version. Include empty/low-sample groups rather than silently dropping them.
- Include baseline comparisons: always-WAIT, simple HTF trend pullback, rules without ML, and current full setup.
- Use walk-forward research periods plus an untouched final holdout. Apply multiple-testing awareness when tuning many variants.
- Add dependence-aware confidence intervals and clearly label results exploratory unless sample size and stability are credible. Never infer profitability from screenshots or social media examples.

### 6. Dashboard and explainability

- Keep strict TypeScript and authenticated SSE. Do not poll expensive endpoints every second.
- Add an on-demand admin backtest panel with explicit running/error/empty states and the statistical warnings returned by the API.
- Visualize session versus fixed profile distinctly, HVN/LVN and profile approximation/source, value migration, breakout/retest, entry/stop/targets, and reasons each gate passed or failed.
- Show setup coverage and abstention: users must understand that WAIT is expected behavior, not a service failure.
- Never display uncalibrated diffusion proxy output as a live approved probability.

### 7. Tests required

Add deterministic tests for:

- no higher-timeframe candle beyond decision time;
- no post-confirmation pivot leakage;
- profile value-area mass and bin-boundary/tick-size behavior;
- developing profile incremental/full equivalence;
- bullish and bearish inverse setup conditions;
- opposing order-flow rejection;
- stale/spread/liquidity rejection;
- minimum 2.5R enforcement;
- calibrated TP-first gate and missing-model failure;
- setup deduplication;
- fill begins at signal-close boundary, never earlier;
- conservative ambiguous intrabar exit;
- fees/slippage and R arithmetic;
- portfolio concurrency/correlation constraints;
- backtest admin authorization versus viewer read access;
- persistence round-trip and clean Flyway migration;
- guarded execution blocked by default and unable to send a real order.

Use Testcontainers for PostgreSQL integration paths where practical, but keep ordinary unit tests independent of a developer database.

## Required local verification

Use IntelliJ's bundled Java 21 runtime/Maven if system Maven is absent. Run and fix every failure:

```powershell
git status
git branch --show-current
git log --oneline -10

$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd' -B -ntp -f backend\pom.xml clean verify

cd frontend
npm install
npm run lint
npm run build
cd ..

cd ml-service
.\.venv\Scripts\python.exe -m compileall app scripts tests
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -c "from app.main import app; print(app.title)"
cd ..

docker compose config
docker compose -f docker-compose.prod.yml config
docker compose up -d --build
docker compose ps
```

Apply all Flyway migrations to a newly created empty disposable database and verify V1 onward, without altering an existing migration. Smoke-test health, dashboard, multi-symbol market status, setup/bias APIs, admin backtest, prediction persistence/reconciliation, paper fills, model registry/activation/rollback gates, and explicit live-execution refusal. Inspect the rendered dashboard in a browser.

## Deliverables

Implement and test the work; do not merely propose it. Update README/config examples and add a concise verification report. At completion report:

1. Bugs and leakage risks found.
2. Architecture and mathematical changes.
3. Files added and modified.
4. Dataset periods, counts, class/path distributions and effective sample sizes.
5. Baselines and candidate models compared.
6. Walk-forward and untouched-holdout metrics with uncertainty.
7. Calibration and abstention results.
8. Setup/backtest results and breakdowns.
9. Safety/security status.
10. Exact commands and build/test results.
11. Honest remaining limitations or external blockers.
12. Latest commit SHA.

Do not claim the tool predicts “accurately” in general. State exactly what was measured, over which untouched period, after which costs, with what sample size and uncertainty. If evidence is weak, keep the engine neutral and explain what additional data would establish or refute value.

---
