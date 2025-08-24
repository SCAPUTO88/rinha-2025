# Rinha Backend 2025 — Java/Spring + Redis

[![Docker Build & Push](https://github.com/scaputo88/rinha-2025/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/scaputo88/rinha-2025/actions)
[![DockerHub](https://img.shields.io/badge/DockerHub-scaputo88%2Frinha--2025-blue)](https://hub.docker.com/r/scaputo88/rinha-2025)

Implementação Java para a Rinha de Backend 2025, focada em:

- __Simplicidade e performance__: Undertow, tempo limite curto para I/O externo, processamento assíncrono.
- __Consistência pragmática__: gravação em Redis a cada transação; agregação por janela temporal.
- __Observabilidade mínima__: logs essenciais, erros tratados no controller.

---

## Arquitetura

- __App__: Java 17, Spring Boot 3.5.4, Web com Undertow.
- __Persistência__: Redis 7 (Spring Data Redis com Lettuce).
- __Gateway__: Nginx balanceando 2 instâncias do backend.
- __Processors__: Serviços externos "default" e "fallback" (rede externa `payment-processor`).

Processamento:
- `POST /payments` insere uma tarefa assíncrona em um `ExecutorService` (fixed thread pool). O request retorna 202 imediatamente.
- A tarefa tenta o processor `default`; em falha/timeout (250ms), tenta o `fallback`.
- Cada pagamento é __persistido no Redis__ imediatamente, independente do resultado no processor, para fins de contagem.

---

## Modelo de Dados (Redis)

- ZSET `payments_zset`:
  - __score__: epoch millis (timestamp do pagamento)
  - __member__: `processor|amount|fee|usedFallback|uuid`
  - O `uuid` garante unicidade do member (evita sobrescrita em ZSET e perda de eventos).
- Hash `payments_summary` (reservado para futuros totais cumulativos).
- O resumo atual é calculado varrendo o ZSET por `from..to`.

Arquivos relevantes:
- `src/main/java/.../repository/RedisPaymentRepository.java`
- `src/main/java/.../service/PaymentService.java`
- `src/main/java/.../service/ProcessorClient.java`
- `src/main/java/.../service/HealthCheckService.java`
- `src/main/java/.../controller/PaymentController.java`

---

## Endpoints

- __POST `/payments`__
  - Body: `{ "correlationId": "UUID", "amount": number }`
  - Resposta: `202 Accepted` (processamento assíncrono)

- __GET `/payments-summary`__
  - Query opcional: `?from=ISO_INSTANT&to=ISO_INSTANT`
  - Retorno: `PaymentSummary` com campos `default_total_amount`, `default_total_fee`, `default_total_requests`, `fallback_total_amount`, `fallback_total_fee`, `fallback_total_requests`.

- __POST `/purge-payments`__
  - Limpa dados do Redis (ZSET e hash).

- __GET `/payments/service-health`__
  - Consulta saúde dos processors com __cache com backoff__ para evitar abuso (`HealthCheckService`).

---

## Variáveis de Ambiente

- `PAYMENT_PROCESSOR_URL_DEFAULT` (ex.: `http://payment-processor-default:8080`)
- `PAYMENT_PROCESSOR_URL_FALLBACK` (ex.: `http://payment-processor-fallback:8080`)
- `PP_ADMIN_TOKEN` (default `123`) — para `/admin/payments-summary` nos processors
- `PP_TIMEOUT_MS` (default `250`) — timeout de conexão/leitura do `ProcessorClient`
- `SPRING_DATA_REDIS_HOST` (default `localhost` no Boot)
- `SPRING_DATA_REDIS_PORT` (default `6379`)

Observação: O `MAX_PARALLELISM` existe no compose, mas a versão atual usa `Executors.newFixedThreadPool(Math.max(4, availableProcessors))` diretamente.

---

## Como Rodar

Pré-requisitos:
- Docker e Docker Compose
- Rede `payment-processor` disponível (criada pelo compose oficial dos processors) ou crie manualmente:

```bash
# Caso NÃO rode os processors localmente
docker network create payment-processor || true
```

### Usando as imagens publicadas

O `docker-compose.yml` referencia `scaputo88/rinha-2025:1.1.4`.

```bash
docker compose pull
docker compose up -d
```

Smoke test:
```bash
curl -s http://localhost:9999/payments/service-health | jq .
curl -s -X POST http://localhost:9999/payments \
  -H 'Content-Type: application/json' \
  -d '{"correlationId":"11111111-1111-1111-1111-111111111111","amount":19.9}'
curl -s 'http://localhost:9999/payments-summary' | jq .
```

### Subindo com os processors oficiais

No repositório oficial dos processors:
```bash
docker compose -f docker-compose.payment-processor.yml up -d
```
Depois, neste projeto:
```bash
docker compose up -d
```

### Build local da imagem (opcional)

```bash
# Build local
docker build -t rinha-2025:local .
# Alterar compose para usar image: rinha-2025:local
```

Ou via Makefile:
```bash
make image-build VERSION=1.1.4
make image-push VERSION=1.1.4
```

---

## Decisões de Performance

- __Undertow__ em vez de Tomcat.
- __Timeout curto (250ms)__ para chamadas aos processors para evitar acumular conexões lentas.
- __Processamento assíncrono__ com pool fixo (melhor throughput sob carga).
- __Cache com backoff__ em `/payments/service-health` (reduz batidas repetitivas).
- __Member único no ZSET__ (evita perda de eventos e garante agregação correta em janelas).

Trade-offs: Como o processamento é assíncrono e há serviços externos, pequenas __inconsistências temporais__ podem ocorrer durante a execução do teste, convergindo após alguns segundos.

---

## Troubleshooting

- __Resumo não soma ou fica em 1 evento__:
  - Garanta que a imagem em execução inclui o fix do ZSET com `uuid` (versão ≥ 1.1.4).
  - Purge e reexecute: `curl -X POST http://localhost:9999/purge-payments`.
- __Muitas timeouts para processors__:
  - Verifique rede `payment-processor` e se os services estão UP.
  - Ajuste `PP_TIMEOUT_MS` se necessário.
- __Inspecionar Redis rapidamente__:
  - `redis-cli ZRANGEBYSCORE payments_zset -inf +inf LIMIT 0 5`

---

## Licença

MIT