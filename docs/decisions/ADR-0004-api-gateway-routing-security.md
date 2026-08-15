# ADR-0004: API Gateway Routing and Security

## Status
Accepted

## Context
Exposing individual microservice ports directly to external clients creates security risks, duplicates authentication logic across every service, and causes CORS management friction.

## Decision
Implement a reactive Spring Cloud Gateway (`api-gateway`) as the single ingress point. The Gateway performs JWT validation via `JwtAuthenticationGatewayFilterFactory`, strips credentials, and injects authenticated user headers (`X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Correlation-Id`) to internal services.

## Consequences
- **Pros**: Centralized security, unified routing, consistent distributed correlation IDs.
- **Cons**: Gateway becomes a single point of failure (mitigated by horizontal scaling in production).
