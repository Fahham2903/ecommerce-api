# E-Commerce API Service

REST backend for a small online store: catalog search, JWT auth with roles, transactional checkout, Redis caching.

**Stack:** Java 17, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA/Hibernate, MySQL, Flyway, Redis (Spring Cache), Docker.

## How it works

- **Auth:** `POST /api/auth/register` creates a CUSTOMER; `POST /api/auth/login` returns a JWT. The first ADMIN is seeded from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
- **Checkout is safe under concurrency.** Each order runs in one `@Transactional` unit (stock deduction + order insert). `Product` has a `@Version` column, so if two buyers change the same product at once, one transaction rolls back and is retried against fresh stock (`OrderService.checkout`). Overselling is impossible; after 5 attempts the client gets a 409. See `CheckoutConcurrencyTest`.
- **Caching:** single-product lookups are cached in Redis (10 min TTL), evicted on update, delete, and checkout.

## Run locally with Docker

```bash
cp .env.example .env     # then edit the values
docker compose up --build
```

API on http://localhost:8080, Swagger UI at http://localhost:8080/swagger-ui.html.

## Endpoints

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/products?q=&category=&page=&size=` | public |
| GET | `/api/products/{id}` | public (cached) |
| POST / PUT / DELETE | `/api/products[/{id}]` | ADMIN |
| POST | `/api/orders` | authenticated |
| GET | `/api/orders`, `/api/orders/{id}` | owner (ADMIN sees any) |
| PATCH | `/api/orders/{id}/status` | ADMIN |

Checkout body: `{"items":[{"productId":1,"quantity":2}]}`

## Tests

```bash
mvn verify
```

Unit tests (Mockito) cover stock validation and totals; an integration test (H2) fires 20 parallel checkouts at 5 units of stock and asserts nothing is oversold.

## Deploying to AWS (outline)

1. Create an RDS MySQL instance; allow inbound 3306 only from the app's security group.
2. Create an ECR repository, then build and push the image: `docker build -t <acct>.dkr.ecr.<region>.amazonaws.com/ecommerce-api .` and `docker push` it.
3. Launch an EC2 instance with Docker, pull the image, and run it with `DB_URL` pointing at RDS plus `JWT_SECRET`, `ADMIN_*`, and `REDIS_HOST` (a Redis container on the same host, or ElastiCache).
4. Open port 8080 (or put a load balancer in front) and use the public URL as the live link.
