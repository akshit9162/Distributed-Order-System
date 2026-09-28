# Distributed Order Processing System

**[Explore the interactive architecture walkthrough](https://akshit9162.github.io/Distributed-Order-System/)** — every stage explained with a diagram, including the animated SAGA state machine, the Kafka fan-out sequence, and the concurrency test's actual verified result.

A microservices-based order processing pipeline implementing the SAGA pattern for distributed transactions, with concurrency-safety guarantees under load. Built to mirror how systems like e-commerce checkout or payment processing work in practice: independent services that must stay consistent without a single database transaction spanning all of them.

## Why this exists

A normal CRUD app reads and writes one database. This system solves a different problem: **inventory**, **payment**, and **order status** are three separate services with three separate databases (true database-per-service). There is no single transaction that can atomically reserve stock, charge the customer, and mark the order complete — because in the real world those are often genuinely separate systems. The core engineering problems this project addresses:

- What happens if payment fails *after* inventory was already reserved? → compensating rollback (the SAGA pattern)
- What happens if a client retries the same request because it timed out? → idempotency keys
- What happens when two orders want the last unit of the same product at the same instant? → distributed locking, proven under real concurrent load in `InventoryConcurrencyTest`

## Architecture

```
           POST /orders
               |
               v
       +----------------+
       |  order-service  |  <-- owns the SAGA state machine
       |   (port 8081)   |      PENDING -> INVENTORY_RESERVED
       +--------+--------+              -> PAYMENT_CONFIRMED
                |                        -> COMPLETED
        publishes OrderCreatedEvent
                |
                v
     Kafka topic: order-created
                |
                v
     +--------------------+
     | inventory-service   |  <-- Redis distributed lock guards
     |    (port 8082)      |      each product during reservation
     +----------+----------+
                |
  reserved? --- Kafka: inventory-reserved --- rejected? --- Kafka: inventory-rejected
                |                                                  |
                v                                                  v
     +-------------------+                                (back to order-service,
     |  payment-service   |  <-- optimistic locking (@Version)     order marked
     |   (port 8083)      |      + retry on conflict               INVENTORY_REJECTED)
     +---------+----------+
               |
confirmed? --- Kafka: payment-confirmed --- rejected? --- Kafka: payment-rejected
               |                                                |
               v                                                v
     order marked COMPLETED                      order-service publishes
                                                   OrderRolledBackEvent
                                                           |
                                                           v
                                            inventory-service releases
                                            the stock it reserved earlier
                                            (the compensating transaction)
```

Each service has its own Postgres database and its own copy of the Kafka event classes (no shared library). This keeps the services independently deployable, at the cost of some duplication — the standard trade-off in microservice architectures.

## Concurrency safety

| Service | Mechanism | Why |
|---|---|---|
| inventory-service | Redis distributed lock (SET NX PX + Lua-script safe unlock, with retry-with-backoff on contention) | Conflicts are common — many orders can compete for one popular product |
| payment-service | JPA optimistic locking (`@Version`) + retry loop | Conflicts are rare — one customer rarely has two payments in flight at once |

## Fault tolerance: dead-letter topics

Every Kafka listener retries a failing message 3 times (1s apart) before publishing the raw record to a `<topic>.DLT` topic instead of blocking the partition or silently dropping it. An operator can inspect `order-created.DLT`, `inventory-reserved.DLT`, etc. and decide whether to replay or discard. Configured in each service's `KafkaConsumerConfig`.

## Caching: Redis cache-aside on `GET /orders/{id}`

`OrderQueryService` checks Redis first, falls back to Postgres on a miss, and populates the cache on the way back (30s TTL). Every status transition in `OrderEventConsumer` explicitly evicts that order's cache entry, so a client polling for status never sees a stale result.

## Load testing

`load-test/order-load-test.js` is a k6 script that hits `POST /orders` with ramping concurrent load (20 → 50 virtual users), asserting p95 < 200ms and p99 < 500ms. Run with `k6 run load-test/order-load-test.js` (setup steps are in the script's header comment). Verified result: `8487 requests, 0 failed, p(95)=136.68ms, p(99)=202.96ms, throughput=169.6 req/s` — all thresholds passed.

## Concurrency stress test

```
docker-compose up -d redis   # only Redis is needed for this test
cd inventory-service
mvn test -Dtest=InventoryConcurrencyTest
```

Fires 50 concurrent reservation attempts at a product with 10 units of stock, asserting exactly 10 succeed and 40 are correctly rejected — proving the lock prevents overselling under real concurrent load. Verified result: `50 concurrent requests for 10 units of stock -> 10 reserved, 40 rejected, in 2801 ms`.

## AWS deployment

The full stack has been deployed and verified end-to-end on a single AWS EC2 instance (Ubuntu, t3.medium) running the same `docker-compose.yml` used locally — Kafka, Postgres, Redis, and all three services as containers on one box. An order was placed and confirmed `COMPLETED` from an external client hitting the instance's public IP.

For a production-style deployment with managed services, Terraform in `/terraform` provisions RDS Postgres, ElastiCache Redis, MSK Serverless (Kafka), ECR repositories, and an ECS Fargate cluster — see `terraform/README.md`.

## Running it locally

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build and run each service (separate terminals)
cd order-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run

# 3. Seed a product and a customer account
curl -X POST localhost:8082/inventory/seed -H "Content-Type: application/json" \
  -d '{"productId": "sku-123", "quantity": 10}'

curl -X POST localhost:8083/accounts/seed -H "Content-Type: application/json" \
  -d '{"customerId": "cust-1", "balance": 500.00}'

# 4. Place an order
curl -X POST localhost:8081/orders -H "Content-Type: application/json" \
  -d '{
        "idempotencyKey": "demo-key-1",
        "customerId": "cust-1",
        "productId": "sku-123",
        "quantity": 2,
        "amount": 49.99
      }'

# 5. Check the order status - progresses to COMPLETED within a second or two
curl localhost:8081/orders/{id}
```

## Tech stack

Java 17, Spring Boot 3.3, Spring Kafka, Spring Data JPA, PostgreSQL, Redis, Docker + Docker Compose, JUnit 5, AWS EC2.

## Contributors

Team project by **Harsh Kokcha** and **Akshit Gupta**.
