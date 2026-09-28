# Context efficiency

> Deterministic 34-turn fixture; estimates are A-stage heuristic units, not provider billing.

| Metadata | Value |
|---|---|
| Generated at | 2026-09-23T03:06:07.254182Z |
| Git commit | `60c5049` |
| Fixture | `m6-v1` |
| Command | `./scripts/generate-a-stage-reports.sh` |

| Metric | Legacy (last 30) | M1 Context Builder |
|---|---:|---:|
| Estimated prompt units | 335 | 79 |
| Required early fact present | false | true |
| Selected memories | 0 | 1 |
| Duplicate candidates removed | 0 | 1 |

Rejection reasons: `{EXPIRED=1, PROFILE_MISMATCH=1, DUPLICATE=1}`.
