# Guia: repositórios, GitHub e Docker do CronoMed

## 1. Quais repositórios criar

Crie uma **organização** no GitHub (ex.: `cronomed-fatec`) e convide a equipe. Um repositório por produto, porque cada um tem
ferramenta, ciclo de build e responsáveis diferentes:

| Repositório | Conteúdo | Quem usa |
|---|---|---|
| `cronomed-backend` | Esta API (Spring Boot), migrações SQL, Dockerfile, docker-compose, CI | Backend |
| `cronomed-web` | Site (React) | Front-end |
| `cronomed-mobile` | App Android (Kotlin) | Mobile |
| `cronomed-firmware` | Código do ESP32 (PlatformIO/Arduino) | IoT |
| `cronomed-docs` | Documentação do projeto, diagramas, atas, o PDF de requisitos | Todos |
| `cronomed-infra` *(opcional, depois)* | Compose/Terraform de produção, monitoramento | DevOps |

Regra de ouro: **os times conversam pelos contratos** de `docs/API-E-MQTT.md` (copie para `cronomed-docs`).
O Swagger (`/swagger-ui.html`) é a fonte da verdade da API.

## 2. Primeiros passos com o repositório do backend

```bash
cd cronomed-backend
git init -b main
git add . && git commit -m "feat: backend inicial do CronoMed"
git remote add origin git@github.com:<sua-org>/cronomed-backend.git
git push -u origin main
git switch -c develop && git push -u origin develop
```

## 3. Organização de branches (Git Flow simplificado)

- `main`: sempre estável; cada push gera a imagem Docker. **Protegida**.
- `develop`: integração do dia a dia.
- `feature/<tarefa>`: uma por tarefa (ex.: `feature/rf12-relatorio-csv`), criada a partir de `develop`.
- `hotfix/<problema>`: correção urgente a partir de `main`.

Fluxo: `feature/*` → Pull Request → `develop` → (fim da sprint) PR → `main` + tag `v0.1.0`.

Em *Settings → Branches*, proteja `main` e `develop`: exigir Pull Request, 1 aprovação e o check **CI / build-test** verde.
Mensagens de commit: `feat:`, `fix:`, `docs:`, `test:`, `chore:`.

## 4. CI (GitHub Actions)

O arquivo `.github/workflows/ci.yml` já está pronto:
- em todo PR/push para `develop` ou `main`: `mvn -B verify` (compila e roda os testes);
- em push para `main`: constrói a imagem e publica em `ghcr.io/<org>/cronomed-backend` (usa o `GITHUB_TOKEN`, sem segredos extras).

Segredos (JWT, senhas) **nunca** vão no Git: ficam em *Settings → Secrets and variables → Actions* e no `.env` do servidor.

## 5. Rodando tudo com Docker

```bash
cp .env.example .env            # edite senhas; gere o JWT: openssl rand -base64 48
./scripts/setup-mosquitto.sh    # cria o usuário da API no broker MQTT
docker compose up -d --build    # postgres + mosquitto + api
curl localhost:8081/actuator/health
```
- API: `http://localhost:8080` · Swagger: `/swagger-ui.html` (perfil `dev`).
- Banco: o Flyway cria as tabelas sozinho na primeira subida.
- Adicionar uma caixa ao broker: `./scripts/setup-mosquitto.sh add AABBCCDDEEFF <senha>` e `docker compose restart mosquitto`.
- Monitoramento opcional: `docker compose --profile monitoring up -d` (Prometheus em `localhost:9090`).
- Parar: `docker compose down` (os dados ficam nos volumes; `down -v` apaga tudo).

Desenvolvimento local sem Docker para a API: suba só `docker compose up -d postgres mosquitto` e rode `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
(defina `JWT_SECRET` no ambiente).

## 6. Produção (resumo)

Use o perfil `prod`, HTTPS no proxy reverso (Nginx/Caddy), `COOKIE_SECURE=true`, MQTT com TLS (porta 8883, trecho comentado em
`docker/mosquitto/mosquitto.conf`), porta 8081 fechada para a internet e backups do volume do PostgreSQL.
