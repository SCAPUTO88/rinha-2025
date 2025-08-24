# Configurações
VERSION ?= 1.1.4
IMAGE ?= scaputo88/rinha-2025
BRANCH ?= main

# Conveniências
DC := docker compose

.PHONY: help build up down logs ps image-build image-push release clean pull restart logs-api smoke purge

help:
	@echo "Targets:"
	@echo "  build        - builda os serviços do docker compose"
	@echo "  up           - sobe os serviços em background"
	@echo "  down         - derruba os serviços e volumes"
	@echo "  logs         - segue logs do nginx (gateway)"
	@echo "  ps           - mostra status dos containers"
	@echo "  image-build  - builda a imagem Docker (tag $(VERSION) e latest)"
	@echo "  image-push   - publica as tags no DockerHub"
	@echo "  release      - builda, publica imagem e cria tag git v$(VERSION)"
	@echo "  clean        - remove imagens dangling e cache"
	@echo "  pull         - docker compose pull das imagens"
	@echo "  restart      - reinicia nginx e backends"
	@echo "  logs-api     - segue logs dos backends"
	@echo "  smoke        - executa smoke test (health, um POST e summary)"
	@echo "  purge        - limpa dados no Redis via endpoint /purge-payments"

build:
	$(DC) build

up:
	$(DC) up -d

down:
	$(DC) down -v

logs:
	$(DC) logs -f nginx

ps:
	$(DC) ps

pull:
	$(DC) pull

restart:
	$(DC) restart nginx backend-api-1 backend-api-2 || true

logs-api:
	$(DC) logs -f backend-api-1 backend-api-2

image-build:
	docker build -t $(IMAGE):$(VERSION) -t $(IMAGE):latest .

image-push:
	docker push $(IMAGE):$(VERSION)
	docker push $(IMAGE):latest

release: image-build image-push
	git tag -a v$(VERSION) -m "Release v$(VERSION)"
	git push origin v$(VERSION)

clean:
	docker image prune -f

smoke:
	@echo "==> Health"
	curl -s http://localhost:9999/payments/service-health | jq . || true
	@echo "==> POST /payments"
	curl -s -X POST http://localhost:9999/payments \
	  -H 'Content-Type: application/json' \
	  -d '{"correlationId":"11111111-1111-1111-1111-111111111111","amount":19.9}' | jq . || true
	@echo "==> GET /payments-summary"
	curl -s 'http://localhost:9999/payments-summary' | jq . || true

purge:
	curl -s -X POST http://localhost:9999/purge-payments | jq . || true