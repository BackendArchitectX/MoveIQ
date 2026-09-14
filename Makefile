MVN ?= mvn

.PHONY: setup infra test backend frontend demo clean

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

demo: infra
	$(MAKE) -j2 backend frontend

clean:
	cd backend && $(MVN) clean
	rm -rf frontend/dist
