PYTHON ?= python3

.PHONY: setup data-check profile ingest test lint backend frontend demo

setup:
	cd backend && $(PYTHON) -m pip install -e ".[dev,data]"
	cd frontend && npm install

data-check:
	cd backend && $(PYTHON) -m app.data.manifest --check

profile:
	cd backend && $(PYTHON) -m app.data.profile

ingest:
	cd backend && $(PYTHON) -m app.data.ingest

test:
	cd backend && $(PYTHON) -m pytest -q
	cd frontend && npm run build

lint:
	cd backend && $(PYTHON) -m ruff check app tests

backend:
	cd backend && $(PYTHON) -m uvicorn app.main:app --reload --port 8000

frontend:
	cd frontend && npm run dev

demo: data-check
	$(MAKE) -j2 backend frontend
