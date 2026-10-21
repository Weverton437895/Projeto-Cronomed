# Contratos de integração (Web, App e Firmware)

Base: `http://localhost:8080/api/v1`. Swagger UI: `http://localhost:8080/swagger-ui.html` (perfil `dev`).
Erros seguem sempre o formato `{ "code": "...", "message": "...", "details": [...] }`.

## 1. Autenticação (Web e App)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/register` | Cadastro (perfil titular/cuidador/profissional) |
| POST | `/auth/login` | Login. Envie `X-Client-Type: web` para receber **cookies HttpOnly**; sem o header (app) os tokens vêm no corpo |
| POST | `/auth/refresh` | Troca o refresh token (rotação: o antigo deixa de valer) |
| POST | `/auth/logout` | Revoga o access token (jti) e o refresh token |
| POST | `/auth/password/forgot` | Envia OTP de 6 dígitos por e-mail (válido 10 min) |
| POST | `/auth/password/reset` | Redefine a senha com o OTP; invalida todas as sessões |

- Access token: 15 min. Refresh token: 7 dias, opaco, guardado como hash.
- Todas as demais rotas exigem `Authorization: Bearer <accessToken>` (ou o cookie, no Web).
- Trocar/redefinir a senha incrementa `tokenVersion` e invalida os access tokens antigos na hora.

## 2. Recursos principais

- **Usuário**: `GET/PUT/DELETE /users/me`, `PUT /users/me/password`, `PUT /users/me/push-token`.
- **Pacientes**: `POST/GET /patients`, `GET/PUT/DELETE /patients/{id}`.
- **Compartilhamento** (apenas o titular concede): `POST/GET /patients/{id}/grants`, `PUT/DELETE /patients/{id}/grants/{grantId}`.
  Níveis: `VIEW` < `CONFIRM` < `MANAGE`. A revogação vale já na próxima requisição.
- **Caixas**: `POST/GET /devices`, `GET/PUT /devices/{id}`, `POST /devices/{id}/revoke`,
  `PUT /devices/{id}/compartments/{cid}` (associar paciente), `GET /devices/{id}/plan`.
  O `deviceSecret` aparece **uma única vez** no `POST /devices`.
- **Medicamentos**: `GET/POST /patients/{pid}/medications`, `GET/PUT/DELETE /medications/{id}`, `POST /medications/{id}/refill`.
  O Web deve enviar o campo `version` no `PUT`; divergência → `409 STALE_DATA`.
- **Doses**: `GET /patients/{pid}/doses/upcoming`, `POST /doses/{doseId}/events` (confirmar/recusar/adiar).
  O `eventId` (UUID gerado pelo cliente) é a chave de idempotência: reenviar é seguro.
- **Painel**: `GET /patients/{pid}/dashboard|doses|events|alerts|report.csv`.
- **Público**: `POST /public/contact`.

## 3. Sincronização offline (App)

`POST /sync/push` envia uma lista de mudanças/eventos feitos offline. Cada item retorna um status:
`APPLIED`, `DUPLICATE`, `STALE`, `REJECTED` ou `RETRY` (apenas os `RETRY` devem ser reenviados).
`GET /sync/pull?since=<instante ISO-8601>` devolve o que mudou no servidor. Regra de conflito: vence o
timestamp mais recente; empate → o app. Use sempre `elapsedMs` (tempo decorrido) quando o relógio do celular
puder estar errado: o servidor calibra o horário real.

## 4. Firmware (ESP32)

### 4.1 Cadastro de cada caixa
1. O titular registra a caixa (`POST /devices` com o MAC) e recebe o `deviceSecret`.
2. Grave MAC + secret na NVS da caixa. Crie o usuário MQTT da caixa no broker (veja o guia de Docker).

### 4.2 MQTT (preferencial)
Prefixo `cronomed/devices`; `{id}` = MAC maiúsculo **sem** separadores (ex.: `AABBCCDDEEFF`), que também é o usuário MQTT.

| Tópico | Sentido | Conteúdo |
|---|---|---|
| `cronomed/devices/{id}/events` | caixa → API | `{"events":[{"id":"<uuid>","compartment":1-6,"doseId":"<uuid>","type":"BOX_OPENED\|CONFIRMED_YES\|CONFIRMED_NO\|POSTPONED","occurredAt":"ISO","elapsedMs":123,"seq":42}]}` |
| `cronomed/devices/{id}/heartbeat` | caixa → API | `{"battery":87}` (a cada ~60 s) |
| `cronomed/devices/{id}/plan` | API → caixa (**retained**) | plano numérico, abaixo |
| `cronomed/devices/{id}/acks` | API → caixa | `{"ackedIds":["<uuid>"],"rejected":[{"id":"<uuid>","reason":"..."}]}` |

QoS 1. A caixa guarda os eventos na LittleFS e **só apaga os que aparecerem em `ackedIds`**.
Abrir a tampa (`BOX_OPENED`) nunca baixa o estoque; só `CONFIRMED_YES` baixa.

Plano (somente números, sem nomes de remédios):
```json
{"version": 12, "serverTime": 1790000000, "toleranceSeconds": 3600,
 "compartments": [{"index": 1, "rgb": 29362}],
 "doses": [{"id": "<uuid>", "c": 1, "t": 1790003600}]}
```
`t` é epoch UTC. A caixa dispara os alarmes sozinha pelo RTC (sincronizado por NTP), mesmo sem internet.

### 4.3 HTTP (alternativa quando não houver MQTT)
`POST /device-auth/token` `{macAddress, secret}` → JWT de caixa (24 h). Depois, com `Authorization: Bearer`:
`GET /device-gateway/plan`, `POST /device-gateway/events` (mesmo corpo de `events`), `POST /device-gateway/heartbeat`.
