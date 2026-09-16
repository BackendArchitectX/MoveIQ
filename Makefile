MVN ?= mvn

.PHONY: setup infra test backend frontend dev up demo down clean

setup:
	cd frontend && npm install

infra:
	docker compose up -d postgres redis kafka

test:
	cd backend && $(MVN) -B verify
	cd frontend && npm run build

backend:
	cd backend && $(MVN) spring-boot:run

frontend:
	cd frontend && npm run dev

dev: infra setup
	$(MAKE) -j2 backend frontend

up:
	docker compose up --build

demo: up

down:
	docker compose down

clean:
	cd backend && $(MVN) clean
	rm -rf frontend/dist
