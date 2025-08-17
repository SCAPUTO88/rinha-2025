# Rinha Backend 2025

[![Docker Build & Push](https://github.com/scaputo88/rinha-2025/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/scaputo88/rinha-2025/actions)
[![DockerHub](https://img.shields.io/badge/DockerHub-scaputo88%2Frinha--2025-blue)](https://hub.docker.com/r/scaputo88/rinha-2025)

Implementação Java para a **Rinha de Backend 2025** — enxuta, sem JPA/Metrics/Guava, empacotada em Docker e pronta para rodar com o setup oficial dos Payment Processors.

---

## Como rodar

Pré-requisitos:
- Docker e Docker Compose
- Rede `payment-processor` disponível (criada pelo compose oficial dos processors) ou crie manualmente:

```bash
# Se você NÃO vai rodar o compose dos processors localmente
docker network create payment-processor || true
```

### Usando as imagens publicadas (recomendado)

As apps já estão referenciadas no `docker-compose.yml` como `scaputo88/rinha-2025:1.0.7`.

```bash
docker compose pull
docker compose up -d
```

Smoke test:
```bash
curl -s http://localhost:9999/payments/service-health
curl -s -X POST http://localhost:9999/payments \
  -H "Content-Type: application/json" \
  -d '{"correlationId":"11111111-1111-1111-1111-111111111111","amount":"10.00","processor":"DEFAULT"}'
curl -s http://localhost:9999/payments-summary
```

### Usando o setup oficial dos processors

No repositório oficial dos processors, suba-os primeiro para prover a rede `payment-processor`:
```bash
docker compose -f docker-compose.payment-processor.yml up -d
```
Depois, suba este projeto normalmente:
```bash
docker compose up -d
```

---

## Build & Publish (opcional)

Se quiser construir e publicar sua própria imagem:

```bash
# Login no Docker Hub
docker login

# Build e push para linux/amd64
docker buildx build --platform linux/amd64 -t scaputo88/rinha-2025:1.0.7 --push .

# (Opcional) latest
docker tag scaputo88/rinha-2025:1.0.7 scaputo88/rinha-2025:latest
docker push scaputo88/rinha-2025:latest
```

Ou usando Makefile (já configurado com VERSION=1.0.7):
```bash
make image-build
make image-push
```

---

## Endpoints

- POST `/payments`
  - Body: `{ correlationId: UUID, amount: string (ex: "10.00"), processor: "DEFAULT"|"FALLBACK" }`
  - Resposta imediata `202 Accepted` (processamento assíncrono)

- GET `/payments-summary`
  - Retorna agregados de sucesso/falha

- GET `/payments/service-health`
  - Health dos processors com cache anti-abuso

---

## Variáveis de Ambiente (principais)

- `PP_DEFAULT_URL` — URL do processor default (ex: `http://payment-processor-default:8080`)
- `PP_FALLBACK_URL` — URL do processor fallback
- `PP_ADMIN_TOKEN` — token para endpoint admin dos processors
- `WORKERS` — workers de processamento (padrão 2)
- `QUEUE_CAPACITY` — capacidade da fila (padrão 10000)
- `REPL_TIMEOUT_MS` — timeout de replicação entre peers (padrão 250)
- `PEERS` — URLs dos peers (ex: `http://app2:8080`)
- `JAVA_TOOL_OPTIONS` — flags de execução da JVM (heap/metaspace mínimas)

---

## Nginx

- O arquivo `nginx.conf` é montado como `/etc/nginx/conf.d/default.conf`.
- Portanto, ele NÃO deve conter blocos `events {}` ou `http {}` — somente `upstream` e `server`.
- Timeouts curtos e keepalive configurados para performance.

---

## Stack

- Java 17, Spring Boot Web (Undertow)
- Armazenamento em memória com replicação entre instâncias
- Sem JPA/Micrometer/Guava
- Docker Compose com 2 apps e 1 nginx

---

## Licença

MIT