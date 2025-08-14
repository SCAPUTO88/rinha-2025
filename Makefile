# Configurações
VERSION ?= 1.0.6
IMAGE ?= scaputo88/rinha-2025
BRANCH ?= main

# Conveniências
DC := docker compose

.PHONY: help build up down logs ps image-build image-push release clean

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