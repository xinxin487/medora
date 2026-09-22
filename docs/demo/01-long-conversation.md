# Demo 1: long-conversation context and memory governance

## Purpose

Show that the legacy last-30-turn window loses an early preference, while M1 selects one normalized memory, removes duplicates/expired/irrelevant candidates, and stays within a smaller context budget.

## Setup

Set `FEATURE_CONTEXT_BUILDER=true`. For the interactive chat path, also configure a real `AI_CHAT_API_KEY`, start backend/frontend, seed the synthetic data, and sign in with the disposable demo account documented in `deploy/README.md`.

```bash
./scripts/seed-demo.sh
./scripts/generate-a-stage-reports.sh
```

The report command runs a deterministic 34-turn fixture. Turn 1 is `用户明确说明：偏好上午就诊`; turns 2–34 are numbered unrelated demo topics. This satisfies the repeatable long-conversation case without spending real model tokens.

## Reproducible evidence path

Open `docs/reports/context-efficiency.md` and verify:

- legacy last-30 view: required early fact is `false`;
- M1 Context Builder: required early fact is `true`;
- selected memory count is `1`;
- duplicate count is `1`;
- rejection reasons include `DUPLICATE`, `EXPIRED`, and `PROFILE_MISMATCH`;
- M1 estimated prompt units are lower than the legacy value.

## Interactive path

1. Open `http://localhost:3000/app/assistant` and create a conversation.
2. First send: `我明确偏好上午就诊，请以后安排时记住。`
3. Continue with 30 or more numbered neutral turns, for example `第 02 轮：记录一个无关演示话题` through `第 33 轮：记录一个无关演示话题`.
4. Final question: `结合我最早说过的偏好，我更适合上午还是下午就诊？`
5. Expected answer evidence: the answer refers to `上午`; it must not introduce a second conflicting preference.
6. Copy the latest `traceId` from `http://localhost:3000/admin/traces`, then inspect `http://localhost:3000/admin/context` or call `GET /api/v1/admin/context-diagnostics/{traceId}` with an ADMIN token.

The diagnostic view exposes budget and reason counts plus fingerprints, not raw conversation or memory text.
