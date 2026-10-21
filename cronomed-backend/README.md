# CronoMed – Backend

API REST + gateway MQTT do sistema de gerenciamento de medicação (FATEC Praia Grande – DSM4).
Java 21, Spring Boot 3.3, PostgreSQL (Flyway), JWT, MQTT (Mosquitto), FCM.

## Início rápido
```bash
cp .env.example .env && ./scripts/setup-mosquitto.sh
docker compose up -d --build
```
Swagger: http://localhost:8080/swagger-ui.html

Sem Docker: `mvn -B verify` (testes) e `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.

## Estrutura
`src/main/java/br/com/cronomed/<módulo>`: `auth`, `user`, `patient` (acesso/compartilhamento), `device` (+`mqtt`), `medication`,
`dose`, `stock`, `alert`, `dashboard`, `sync`, `notification`, `audit`, `config`, `common`.
Migrações em `src/main/resources/db/migration`.

## Decisões importantes
- Acesso por paciente (VIEW/CONFIRM/MANAGE/OWNER) verificado a cada requisição; revogar um compartilhamento vale imediatamente.
- Sem Redis: a blacklist de tokens e o ShedLock usam tabelas do PostgreSQL.
- Doses são pré-geradas para 7 dias; o ID da dose é determinístico, então a caixa e o servidor concordam sem coordenação.
- Log de auditoria imutável (trigger no banco).

Documentação: [`docs/GUIA-REPOSITORIOS-GITHUB-DOCKER.md`](docs/GUIA-REPOSITORIOS-GITHUB-DOCKER.md) · [`docs/API-E-MQTT.md`](docs/API-E-MQTT.md)
