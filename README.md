# API Gateway

Spring Cloud Gateway for the e-commerce platform. Routes requests to backend microservices, validates JWTs, enforces rate limits, applies CORS, and logs requests.

## Responsibilities

- Route `/api/v1/*` to the correct service
- Validate JWT for protected routes (cart, orders, admin)
- `X-Internal-API-Key` enforcement for service-to-service calls
- Rate limiting (login/register heavily throttled)
- Configurable CORS for the Next.js frontend
- Request/response logging with PII masking

## Routes

| Route | Target | Auth |
|-------|--------|------|
| `/api/v1/auth/**` | auth-service:8081 | No |
| `/api/v1/cart/**` | cart-service:8082 | Yes (JWT) |
| `/api/v1/products/**`, `/api/v1/categories/**` | product-service:8083 | No (GET) / Yes (write) |
| `/api/v1/orders/**`, `/api/v1/customers/**` | order-api:8084 | Yes (JWT) |
| `/api/v1/admin/**` | admin-service:8085 | Yes (JWT + ADMIN) |

## Stack

- Java 21, Spring Boot 3.4, Spring Cloud Gateway
- JWT via `spring-security-oauth2-resource-server`

## Development

```bash
./mvnw spring-boot:run
# http://localhost:8080
```

Full local orchestration lives in the `sdlc` repo (`make dev`).

## Related

- SDLC plan: `/Users/sushant/Projects/Library/sdlc/planning/SDLC-PLAN-v2.0.md`