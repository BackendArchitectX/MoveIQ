# Data

`data/moveinsync/` is the immutable source-data boundary.

Generated outputs stay separate:
- `data/normalized/` — typed Parquet
- `data/quarantine/` — invalid or suspicious rows
- `data/profiles/` — deterministic quality reports
- `data/fixtures/` — tiny CI/demo fixtures derived from official data
