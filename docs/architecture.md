# Architecture

MoveIQ separates three planes:

```text
DATA PLANE
MoveInSync raw CSV → validation → normalization → Parquet → data trust

INTELLIGENCE PLANE
context metrics → deterministic detection → situations → evidence → hypotheses → decisions

CONTROL PLANE
action proposal → human approval → fresh revalidation → idempotent execution → outcome verification
```

Raw MoveInSync files remain immutable under `data/moveinsync/raw/`. PostgreSQL is reserved for operational workflow state.
