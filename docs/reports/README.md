# A-stage evidence reports

Regenerate all reports from checked-in deterministic fixtures:

```bash
./scripts/generate-a-stage-reports.sh
```

Every JSON/Markdown pair records its generation time, Git commit, fixture version, feature flags, exact command, environment mode, and metrics. The generator rejects sensitive field names such as authentication credentials, user contact fields, raw messages, and raw document contents.

Reports:

- `context-efficiency`: compares the legacy last-30-turn window with M1 compression and governed memory selection. Token values are heuristic prompt units, not a provider invoice.
- `rag-evaluation`: runs the checked-in layered-RAG ranking fixture. Perfect fixture results are regression evidence, not a production accuracy claim.
- `appointment-load`: replays the M4 100-attempt/10-quota result and fails if oversell or inventory-balance invariants are violated. Latencies are deterministic fixture values, not a machine benchmark.

Only synthetic fixture `m6-v1` is used. No report generator reads real user conversations, credentials, uploaded documents, or patient data.
