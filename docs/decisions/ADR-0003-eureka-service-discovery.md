# ADR-0003: Netflix Eureka for Service Discovery

## Status
Accepted

## Context
Microservices run across dynamic container IP addresses in Docker Compose and Kubernetes environments. Hardcoding upstream URLs impedes dynamic scaling and health-aware routing.

## Decision
Adopt Netflix Eureka Server as the central service registry. Spring Cloud Gateway and downstream services discover endpoints dynamically via service IDs (`lb://inventory-service`, etc.).

## Consequences
- **Pros**: Zero hardcoded IP addresses, seamless client-side load balancing, automatic failover.
- **Cons**: Additional infrastructure service to run and monitor.
