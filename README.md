# Aegis AI

AI-powered trading intelligence platform focused on explainable signals, risk-aware decisions, and backtestable strategies.

## Current Sprint

Sprint 1 is under active development in `sprint/01-foundation` and tracked in pull request #1.

### Implemented

- Java 21 + Spring Boot backend
- React + TypeScript dashboard shell
- TimescaleDB and Redis local infrastructure
- Immutable OHLCV candle model
- Pluggable indicator contract
- EMA 20, RSI 14 and ATR 14 calculations
- Explainable LONG / SHORT / WAIT scoring engine
- `POST /api/v1/analysis` endpoint
- Bullish and bearish scoring tests
- GitHub Actions CI foundation

### Run locally

```bash
docker compose up -d
cd backend
mvn spring-boot:run
```

The backend health endpoint is available at `http://localhost:8080/api/health`.

## Product Principle

AEGIS must never return an unexplained signal. Every market decision will include its contributing indicators, score, confidence, risk context and human-readable reasons.
