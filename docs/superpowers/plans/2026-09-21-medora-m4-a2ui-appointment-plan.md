# Medora M4 A2UI Appointment and Waitlist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require an A2UI user confirmation for Agent bookings while preserving inventory under concurrency, expiry, cancellation, Redis failure, and waitlist promotion.

**Architecture:** `prepareAppointment` creates a database-backed temporary hold and a server-authored A2UI surface. A one-time action converts the hold to a confirmed appointment; Redis accelerates locking/expiry/waitlist ordering, while PostgreSQL transactions, locks, and constraints guarantee correctness.

**Tech Stack:** Spring Boot/JPA, PostgreSQL, Redis StringRedisTemplate/Lua, Flyway, SSE, Next.js/React, A2UI v0.9.1 subset, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Agent code cannot invoke the final confirmation service.
- Business-critical A2UI data comes from server snapshots, never resubmitted UI fields.
- PostgreSQL remains correct when Redis is empty or unavailable.
- Use one lock order: idempotency/action row, slot row, appointment/hold row, waitlist row.
- Only a successful state transition may change inventory counters.

## Review Focus

- Confirm-vs-expire and cancel-vs-promote races must change quota once.
- Forged, expired, consumed, wrong-user, and wrong-surface tokens must be rejected.
- 100 concurrent attempts for 10 slots must never make available/reserved negative or occupy more than 10.
- Redis outage/flush must not oversell and waitlist index must rebuild.
- Frontend refresh must restore pending surfaces without re-preparing a hold.

---

### Task 1: Add hold, action, waitlist, and inventory persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__appointment_hold_a2ui_waitlist.sql`
- Modify: `backend/src/main/java/com/medora/appointment/domain/AppointmentEntity.java`
- Modify: `backend/src/main/java/com/medora/masterdata/domain/ScheduleSlotEntity.java`
- Create: `backend/src/main/java/com/medora/appointment/hold/AppointmentHoldEntity.java`
- Create: `backend/src/main/java/com/medora/appointment/hold/AppointmentHoldRepository.java`
- Create: `backend/src/main/java/com/medora/a2ui/domain/A2uiActionEntity.java`
- Create: `backend/src/main/java/com/medora/a2ui/domain/A2uiActionRepository.java`
- Create: `backend/src/main/java/com/medora/appointment/waitlist/WaitlistEntryEntity.java`
- Create: `backend/src/main/java/com/medora/appointment/waitlist/WaitlistEntryRepository.java`
- Test: `backend/src/test/java/com/medora/appointment/hold/AppointmentStateMigrationTest.java`

**Interfaces:**
- Consumes: existing appointment and slot schema.
- Produces: hold states PENDING_CONFIRMATION/CONFIRMED/CANCELLED/EXPIRED; waitlist states WAITING/OFFERED/CONFIRMED/CANCELLED/EXPIRED; one-time action rows.

- [ ] **Step 1: Write migration/invariant tests**

Assert old CONFIRMED/CANCELLED appointments remain valid; duplicate action ID/token hash and duplicate active waitlist membership fail; quota check constraints reject negative available/reserved and `available+reserved>total`.

- [ ] **Step 2: Verify failure**

Run: `cd backend && mvn -Dtest=AppointmentStateMigrationTest test`

Expected: FAIL before V20.

- [ ] **Step 3: Implement V20 and domain transitions**

Add tables/columns and check/unique indexes. `ScheduleSlotEntity` exposes `holdOne`, `confirmHeldOne`, and `releaseHeldOne`. Entities expose named transitions that validate current state; remove service-level status setters.

- [ ] **Step 4: Run migration and domain tests**

Run: `cd backend && mvn -Dtest=AppointmentStateMigrationTest,AppointmentCommandServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V20__appointment_hold_a2ui_waitlist.sql backend/src/main/java/com/medora/appointment backend/src/main/java/com/medora/a2ui/domain backend/src/main/java/com/medora/masterdata/domain/ScheduleSlotEntity.java backend/src/test/java/com/medora/appointment/hold
git commit -m "feat: add appointment hold and waitlist states"
```

### Task 2: Implement Redis lock with database fallback and hold service

**Files:**
- Create: `backend/src/main/java/com/medora/appointment/hold/SlotLock.java`
- Create: `backend/src/main/java/com/medora/appointment/hold/RedisSlotLock.java`
- Create: `backend/src/main/resources/redis/release-slot-lock.lua`
- Create: `backend/src/main/java/com/medora/appointment/hold/AppointmentHoldService.java`
- Modify: `backend/src/main/java/com/medora/masterdata/repository/ScheduleSlotRepository.java`
- Test: `backend/src/test/java/com/medora/appointment/hold/RedisSlotLockTest.java`
- Test: `backend/src/test/java/com/medora/appointment/hold/AppointmentHoldServiceTest.java`

**Interfaces:**
- Consumes: `(userId,slotId,idempotencyKey,source,now)`.
- Produces: `PreparedAppointment(holdId,snapshot,expiresAt)`; Redis lock failures fall back to the existing DB row lock.

- [ ] **Step 1: Write owner-token lock tests**

Using Redis Testcontainers, assert only one owner acquires `slot-lock:{slotId}`, a different owner cannot release it, the Lua script releases the matching owner, and TTL prevents an abandoned lock from remaining forever.

- [ ] **Step 2: Write hold idempotency and fallback tests**

Assert repeated idempotency key returns the same hold; full/closed slot creates none; Redis exception still calls `findByPublicIdForUpdate`; a successful hold decrements available and increments reserved once.

- [ ] **Step 3: Implement lock and transactional hold**

Use `SET NX PX` with random owner token and compare-delete Lua. In one database transaction, lock the slot row, recheck status/quota, create a two-minute hold snapshot, and update counters. Treat Redis acquisition as admission optimization, not correctness proof.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=RedisSlotLockTest,AppointmentHoldServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/appointment/hold backend/src/main/resources/redis backend/src/main/java/com/medora/masterdata/repository/ScheduleSlotRepository.java backend/src/test/java/com/medora/appointment/hold
git commit -m "feat: add safe temporary slot holds"
```

### Task 3: Implement one-time actions and deterministic confirmation

**Files:**
- Create: `backend/src/main/java/com/medora/a2ui/service/ConfirmationTokenService.java`
- Create: `backend/src/main/java/com/medora/a2ui/service/A2uiActionService.java`
- Create: `backend/src/main/java/com/medora/a2ui/api/A2uiActionRequest.java`
- Create: `backend/src/main/java/com/medora/a2ui/api/A2uiActionResponse.java`
- Create: `backend/src/main/java/com/medora/a2ui/controller/A2uiActionController.java`
- Modify: `backend/src/main/java/com/medora/appointment/service/AppointmentCommandService.java`
- Test: `backend/src/test/java/com/medora/a2ui/service/A2uiActionServiceTest.java`

**Interfaces:**
- Consumes: `surfaceId`, `actionId`, raw confirmation token, and idempotency key from the authenticated user.
- Produces: confirmed/cancelled/expired action result; repeated identical confirmation returns the first confirmed appointment.

- [ ] **Step 1: Write token abuse tests**

Cover correct token, wrong token, wrong user, wrong surface, expired token, consumed token, cancel action, and concurrent duplicate confirmation. Verify no invalid case invokes `AppointmentCommandService`.

- [ ] **Step 2: Verify failure**

Run: `cd backend && mvn -Dtest=A2uiActionServiceTest test`

Expected: FAIL for missing action service.

- [ ] **Step 3: Implement secure token storage and confirmation**

Generate 32 random bytes with `SecureRandom`, return Base64URL without padding, and persist SHA-256 only. Lock the action and slot rows, verify binding/expiry/state, create the confirmed appointment from the server snapshot, decrement reserved only, consume the action, and commit once.

- [ ] **Step 4: Add controller contract**

Expose `POST /api/v1/a2ui/actions`. Ignore/reject unknown JSON properties. Map invalid/expired/consumed/conflict cases to stable business error codes; never return the stored token hash.

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -Dtest=A2uiActionServiceTest,AppointmentCommandServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/a2ui backend/src/main/java/com/medora/appointment/service/AppointmentCommandService.java backend/src/test/java/com/medora/a2ui
git commit -m "feat: add one time appointment confirmation"
```

### Task 4: Add idempotent expiry and reconciliation

**Files:**
- Create: `backend/src/main/java/com/medora/appointment/hold/HoldExpiryIndex.java`
- Create: `backend/src/main/java/com/medora/appointment/hold/RedisHoldExpiryIndex.java`
- Create: `backend/src/main/java/com/medora/appointment/hold/AppointmentHoldExpiryJob.java`
- Create: `backend/src/main/java/com/medora/appointment/hold/InventoryReconciliationService.java`
- Test: `backend/src/test/java/com/medora/appointment/hold/AppointmentHoldExpiryJobTest.java`
- Test: `backend/src/test/java/com/medora/appointment/hold/InventoryReconciliationServiceTest.java`

**Interfaces:**
- Consumes: Redis sorted expiry IDs and DB query for overdue PENDING_CONFIRMATION holds.
- Produces: exactly-once logical transition to EXPIRED, quota restoration, and `SlotReleased` event.

- [ ] **Step 1: Write confirm-vs-expire race test**

Run two threads against one real PostgreSQL hold using barriers. Assert final state is either CONFIRMED or EXPIRED, available/reserved counters match that state, and total occupied quota changes once.

- [ ] **Step 2: Write Redis-loss reconciliation test**

Delete the Redis expiry key, advance the fixed clock, run the DB scan, and assert the hold expires and quota returns. Run the scan twice and assert no second change.

- [ ] **Step 3: Implement expiry and reconciliation**

Use Redis ZSet for fast due selection and `SELECT ... FOR UPDATE SKIP LOCKED` (or bounded pessimistic batches) for DB truth. Publish `SlotReleased` only after transaction commit. Reconciliation compares aggregate hold/appointment counts with slot counters and reports discrepancies; A stage must not silently rewrite confirmed business data.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=AppointmentHoldExpiryJobTest,InventoryReconciliationServiceTest test`

Expected: PASS under repeated runs.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/appointment/hold backend/src/test/java/com/medora/appointment/hold
git commit -m "feat: expire holds and reconcile inventory"
```

### Task 5: Emit and render the A2UI v0.9.1 subset

**Files:**
- Create: `backend/src/main/java/com/medora/a2ui/protocol/A2uiMessage.java`
- Create: `backend/src/main/java/com/medora/a2ui/protocol/A2uiCatalogValidator.java`
- Create: `backend/src/main/java/com/medora/a2ui/service/AppointmentSurfaceFactory.java`
- Modify: `backend/src/main/java/com/medora/chat/sse/StreamEventPublisher.java`
- Modify: `backend/src/main/java/com/medora/agent/tool/tools/BookAppointmentTool.java` (rename class/file to `PrepareAppointmentTool.java`)
- Modify: `backend/src/main/java/com/medora/agent/tool/ToolNames.java`
- Modify: `frontend/lib/sse/chat-stream.ts`
- Create: `frontend/lib/a2ui/types.ts`
- Create: `frontend/lib/a2ui/reducer.ts`
- Create: `frontend/lib/a2ui/action-client.ts`
- Create: `frontend/components/a2ui/a2ui-surface.tsx`
- Modify: `frontend/app/app/assistant/page.tsx`
- Test: `backend/src/test/java/com/medora/a2ui/protocol/A2uiCatalogValidatorTest.java`

**Interfaces:**
- Consumes: `PreparedAppointment` and one-time action.
- Produces: `createSurface`, `updateComponents`, `updateDataModel`, and `deleteSurface` messages; frontend action submits only IDs/token/idempotency key.

- [ ] **Step 1: Write backend catalog rejection tests**

Accept only Text, Card, Column, Row, Button, Badge, Divider. Reject unknown types, duplicate component IDs, missing root, external URLs, inline HTML, script-like properties, and an action absent from the stored server action.

- [ ] **Step 2: Implement server-authored appointment surface**

The Agent selects only the verified hold ID. `AppointmentSurfaceFactory` loads snapshot/action and constructs the component tree/data model. It never accepts a model-generated component tree for booking.

- [ ] **Step 3: Extend SSE and frontend reducer**

Add `{ event:"a2ui", data:A2uiMessage }` to `ChatStreamEvent`. The reducer maintains surfaces by ID and applies only the four allowed message types. The React renderer maps the seven allow-listed components to native components and never uses `dangerouslySetInnerHTML`.

- [ ] **Step 4: Add action UX**

Show countdown from server expiry, disable buttons during submission, use a stable UUID idempotency key per action, handle confirmed/expired/conflict updates, and reload pending surfaces through `GET /api/v1/a2ui/surfaces?conversationId=...` after refresh.

- [ ] **Step 5: Run backend and frontend checks**

Run: `cd backend && mvn -Dtest=A2uiCatalogValidatorTest,A2uiActionServiceTest test`

Run: `cd frontend && npm run build`

Expected: both PASS; TypeScript rejects unhandled A2UI message/component variants.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/a2ui backend/src/main/java/com/medora/chat/sse/StreamEventPublisher.java backend/src/main/java/com/medora/agent/tool frontend/lib frontend/components/a2ui frontend/app/app/assistant/page.tsx backend/src/test/java/com/medora/a2ui
git commit -m "feat: add a2ui appointment confirmation surfaces"
```

### Task 6: Implement durable waitlist and automatic offers

**Files:**
- Create: `backend/src/main/java/com/medora/appointment/waitlist/WaitlistService.java`
- Create: `backend/src/main/java/com/medora/appointment/waitlist/WaitlistIndex.java`
- Create: `backend/src/main/java/com/medora/appointment/waitlist/RedisWaitlistIndex.java`
- Create: `backend/src/main/java/com/medora/appointment/waitlist/WaitlistPromotionHandler.java`
- Create: `backend/src/main/java/com/medora/appointment/waitlist/WaitlistRebuildJob.java`
- Create: `backend/src/main/java/com/medora/appointment/controller/WaitlistController.java`
- Test: `backend/src/test/java/com/medora/appointment/waitlist/WaitlistPromotionTest.java`

**Interfaces:**
- Consumes: join/cancel request and after-commit `SlotReleased(slotId)`.
- Produces: WAITING entry, FIFO ZSet index, OFFERED hold/A2UI surface, and rebuildable Redis state.

- [ ] **Step 1: Write FIFO, stale-entry, and offer-expiry tests**

Join users A/B/C with fixed timestamps. Cancel A and corrupt B's Redis member while DB marks B cancelled. Release one slot; assert C receives OFFERED, no one is confirmed automatically, and offer expiry triggers the next valid candidate.

- [ ] **Step 2: Write Redis rebuild test**

Flush Redis, run rebuild, and assert all and only DB WAITING entries return in priority/join order.

- [ ] **Step 3: Implement DB-first waitlist**

Persist first, then update ZSet. Score uses bounded priority in the high-order portion and epoch milliseconds for FIFO ordering. Promotion uses `ZPOPMIN`, rechecks DB under lock, and creates a hold/action/surface through existing services.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=WaitlistPromotionTest test`

Expected: PASS for cancellation, stale Redis, repeated event, offer expiry, and rebuild.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/appointment/waitlist backend/src/main/java/com/medora/appointment/controller/WaitlistController.java backend/src/test/java/com/medora/appointment/waitlist
git commit -m "feat: add durable appointment waitlist promotion"
```

### Task 7: Prove concurrency and end-to-end confirmation

**Files:**
- Create: `backend/src/test/java/com/medora/appointment/concurrency/AppointmentConcurrencyIT.java`
- Create: `backend/src/test/java/com/medora/appointment/concurrency/ConfirmationExpiryRaceIT.java`
- Create: `backend/src/test/java/com/medora/a2ui/A2uiAppointmentFlowIT.java`
- Create: `scripts/run-appointment-load-test.sh`

**Interfaces:**
- Consumes: full M3/M4 stack with PostgreSQL and Redis Testcontainers.
- Produces: reproducible no-oversell and confirmation-flow evidence.

- [ ] **Step 1: Implement the 100-for-10 integration test**

Use 100 concurrent authenticated attempts, unique users/keys, and one 10-quota slot. Await all futures with a hard timeout. Assert `available+reserved+confirmed==10`, no negative counters, and at most ten active holds/confirmed appointments.

- [ ] **Step 2: Add duplicate and Redis-down scenarios**

Run 20 concurrent confirmations with one action/idempotency key; assert one appointment. Stop Redis and repeat a smaller contention case; assert DB locking still prevents oversell.

- [ ] **Step 3: Add E2E HTTP/SSE flow**

Send chat request through a stub ADK tool call, capture the A2UI surface, post its confirmation action, and assert the resulting appointment appears in the list endpoint and the surface is deleted/updated.

- [ ] **Step 4: Run M4 integration suite**

Run: `cd backend && mvn -Dtest='*AppointmentConcurrencyIT,*ConfirmationExpiryRaceIT,*A2uiAppointmentFlowIT' test`

Expected: PASS repeatedly without deadlock or timeout.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/medora/appointment/concurrency backend/src/test/java/com/medora/a2ui/A2uiAppointmentFlowIT.java scripts/run-appointment-load-test.sh
git commit -m "test: prove appointment consistency under concurrency"
```

## M4 Completion Gate

Enable M1–M4 flags, run the full appointment/A2UI suite and frontend build. Accept only when direct Agent booking is impossible, one-time confirmation is enforced, no oversell occurs with or without Redis, expired holds restore quota once, waitlist offers are FIFO/rebuildable, and a browser refresh restores the pending surface.
