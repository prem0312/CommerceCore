# ADR-0002: Database Per Service Pattern

## Status
Accepted

## Context
Shared databases across microservices lead to tight schema coupling, hidden dependency entanglements, and single points of storage failure.

## Decision
Enforce strict Database per Service. Each microservice possesses its own isolated PostgreSQL database schema managed via independent Flyway migration scripts. Zero cross-database joins or shared foreign keys.

## Consequences
- **Pros**: Schema independence, localized performance tuning, zero coupling.
- **Cons**: Requires asynchronous event choreography (Kafka) and REST integration for cross-domain data exchange.
