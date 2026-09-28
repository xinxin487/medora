# Appointment load

> Deterministic replay of the M4 100-attempt/10-quota invariant fixture; this is not a hardware benchmark.

| Metadata | Value |
|---|---|
| Generated at | 2026-09-23T03:06:10.276734Z |
| Git commit | `60c5049` |
| Fixture | `m6-v1` |
| Command | `./scripts/generate-a-stage-reports.sh` |

| Metric | Value |
|---|---:|
| Attempts | 100 |
| Quota | 10 |
| Successes | 10 |
| Conflicts | 89 |
| Duplicates | 1 |
| Oversell | 0 |
| P50 / P95 (ms) | 50 / 95 |
| Final available / reserved / confirmed | 0 / 10 / 0 |
| Redis mode | DATABASE_FALLBACK |
