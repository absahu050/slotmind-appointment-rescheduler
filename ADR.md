# Architecture Decision Records

## 1. Scheduling logic and booking conflicts

**Context.** Booking and replacement recommendations must follow the same availability rules. Concurrent staff actions must not double-book an open slot.

**Options.** Keep all logic in HTTP controllers; put orchestration in application services; or model scheduling entirely in the domain.

**Decision.** `SchedulingService` owns transactional booking and suggestion resolution. `SchedulingEngine` delegates candidate selection to advisor strategies. Slot rows are pessimistically locked when booked or accepted, and provider and slot status are rechecked inside the transaction.

**Tradeoffs.** The service is straightforward to trace through both HTTP and background paths. Pessimistic locking serializes updates to the same slot and can reduce throughput under heavy load, which is acceptable for this demo.

## 2. Runtime strategy selection

**Context.** The rule-based advisor must always be available. The optional AI advisor must not prevent the application from running when credentials or a gateway are unavailable.

**Options.** Hard-code the advisor; select one through a factory; or compose optional AI with a deterministic fallback.

**Decision.** `SchedulingEngine` tries the AI advisor when enabled and uses `RuleBasedSchedulingAdvisor` whenever AI is disabled or returns unusable output. AI recommendations are accepted only when their slot ID is in the currently open candidate set.

**Tradeoffs.** This keeps the core usable without network access and limits invalid model output. The OpenAI-compatible gateway request is synchronous and can add latency when enabled; asynchronous queueing or streaming can be added later.

## 3. Atomic acceptance

**Context.** Accepting a suggestion must book the replacement, release the prior booked slot, update the appointment, and resolve competing suggestions together.

**Options.** Update rows independently; rely only on optimistic versions; or use one database transaction with row locks.

**Decision.** Acceptance locks the appointment and replacement slot, rechecks that the new slot and provider are available, then updates the appointment, both slots, and related suggestion statuses inside one transaction. Entity versions provide an additional stale-write check.

**Tradeoffs.** The database owns consistency and a failed write rolls back the entire change. Locks are held through the transaction, so the operation should remain short and avoid remote AI calls.

## 4. Automatic rescheduling triggers

**Context.** Provider outages can affect many visits, while a cancelled slot normally affects one. The triggering HTTP request should return without waiting for recommendation generation.

**Options.** Run the loop synchronously; publish an in-process task; or add a durable message broker.

**Decision.** After the provider or slot status transaction commits, `RescheduleProcessor` runs asynchronously in the same application process. Both triggers use the same recommendation pipeline and persist a pending suggestion when a candidate exists. Duplicate pending suggestions for the same appointment and trigger are skipped.

**Tradeoffs.** This meets the demo's asynchronous behavior without requiring infrastructure. Tasks are not durable across process restarts; production use should replace the in-process executor with a persistent queue and an outbox.

## 5. Extensibility and deferred work

The advisor interface provides a place to add room or equipment constraints and patient preferences. This iteration does not implement waitlists, notifications, multi-clinic routing, provider capacity limits, patient self-service, or SSE reasoning streams. The default H2 database is intended for local demos; PostgreSQL is supported through environment configuration.
