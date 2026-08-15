# ADR-0011: Observability Stack (Prometheus, Grafana, OpenTelemetry)

## Status
Accepted

## Context
Diagnosing latency anomalies, tracing distributed transactions, and tracking business health across 7 microservices requires centralized metrics, logging, and tracing.

## Decision
Equip all microservices with Spring Boot Actuator, Micrometer Prometheus exporters, and structured logging. Stand up Prometheus (`:9090`) to scrape `/actuator/prometheus` and Grafana (`:3000`) pre-provisioned with custom CommerceCore dashboards.

## Consequences
- **Pros**: Real-time visibility into order rates, cache hit ratios, outbox backlogs, payment success rates, and gateway latency.
- **Cons**: Minor CPU and memory overhead for telemetry collection and scraping.
