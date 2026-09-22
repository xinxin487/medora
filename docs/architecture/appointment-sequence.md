# A2UI appointment and waitlist sequence

```mermaid
sequenceDiagram
    actor User
    participant Agent as Booking Agent
    participant Gateway as ToolGateway
    participant Prepare as AppointmentHoldService
    participant PG as PostgreSQL (source of truth)
    participant Redis as RedisSlotLock / ZSets
    participant Surface as AppointmentSurfaceFactory
    participant UI as A2uiSurfaceCard
    participant Action as POST /api/v1/a2ui/actions
    participant Confirm as A2uiActionService
    participant Command as AppointmentCommandService
    participant Waitlist as WaitlistPromotionHandler

    User->>Agent: Ask to prepare a real returned slot
    Agent->>Gateway: local.prepareAppointment
    Gateway->>Prepare: prepare(user, slot, idempotencyKey)
    Prepare->>Redis: acquire slot-lock:{slotPublicId}
    Prepare->>PG: SELECT slot FOR UPDATE; available -1; reserved +1; insert hold
    Prepare-->>Surface: pending hold (2-minute expiry)
    Surface-->>UI: SSE a2ui createSurface/updateDataModel
    UI-->>User: Countdown + Confirm button
    User->>Action: surfaceId + actionId + one-time token + Idempotency-Key
    Action->>Confirm: validate owner/surface/token/expiry
    Confirm->>PG: lock action + hold + slot
    Confirm->>Command: confirmHeld(...)
    Command->>PG: reserved -1; insert CONFIRMED appointment
    Confirm-->>UI: CONSUMED + appointmentId

    User->>Command: POST /api/v1/appointments/{appointmentId}/cancel
    Command->>PG: appointment CANCELLED; available +1
    Command-->>Waitlist: after-commit SlotReleased
    Waitlist->>Redis: ZPOPMIN waitlist:{slotInternalId}
    Waitlist->>PG: verify WAITING candidate; create OFFERED hold
    Waitlist-->>UI: recoverable A2UI confirmation surface
```

The Redis lock and ZSets are optimizations. PostgreSQL row locks, constraints, and state transitions enforce inventory truth when Redis is empty or unavailable.
