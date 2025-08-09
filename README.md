# 🐳 Rinha Backend 2025

[![Docker Build & Push](https://github.com/scaputo88/rinha-2025/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/scaputo88/rinha-2025/actions)
[![DockerHub](https://img.shields.io/badge/DockerHub-scaputo88%2Frinha--2025-blue)](https://hub.docker.com/r/scaputo88/rinha-2025)

Implementação Java para a **Rinha de Backend 2025** — empacotada em Docker e pronta para rodar com o setup oficial dos *Payment Processors*.

---

## 🚀 Como rodar

### Usando o setup oficial:
```bash
docker compose -f docker-compose.payment-processor.yml up -d
docker compose up --build