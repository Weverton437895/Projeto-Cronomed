# CronoMed Web

Portal web e landing page do **CronoMed** (caixa inteligente de medicamentos + aplicativo + painel). Projeto da FATEC Praia Grande, curso de Desenvolvimento de Software Multiplataforma.

Este projeto é o front-end do repositório `Projeto-Cronomed` (pasta `cronomed-web`) e consome a API do `cronomed-backend`.

## Tecnologias

| Item | Escolha |
|---|---|
| Biblioteca | React 18 (responsivo: celular, tablet e desktop) |
| Build / servidor de desenvolvimento | Vite 5 |
| Rotas | react-router-dom 6 |
| Dados da API | @tanstack/react-query 5 |
| Voz | Web Speech API (SpeechSynthesis e SpeechRecognition; só online, navegadores Chrome/Edge/Safari) |
| Fonte | Roboto (pacote `@fontsource/roboto`, funciona offline) |
| Testes | Vitest |
| IDE sugerida | Visual Studio Code (há `.vscode/extensions.json`) |

## Como rodar na sua máquina

Pré-requisitos: **Node.js 20 ou superior** e o **backend rodando** (veja o README do `cronomed-backend`).

```bash
# 1. Suba o backend (porta 8080) com COOKIE_SECURE=false no .env dele, para o login funcionar em http://localhost
# 2. Neste projeto:
npm install
npm run dev
```

Abra **http://localhost:5173**. O Vite encaminha tudo que começa com `/api` para `http://localhost:8080`, então o navegador e o backend ficam no mesmo "site" e os cookies de login (HttpOnly) funcionam sem configurar CORS.

Se o backend estiver em outro endereço, copie `.env.example` para `.env` e ajuste `VITE_API_PROXY`.

### Primeiro uso

1. **Criar minha conta** → cadastro em duas etapas (dados e senha → contato). O portal cria também o seu perfil de paciente.
2. **Dispositivos → Cadastrar caixa**: informe o MAC do ESP32. A **senha da caixa aparece uma única vez**; ela vai em `secrets.h` (`DEVICE_SECRET`) no firmware.
3. **Medicamentos**: escolha um compartimento e cadastre nome, dosagem, quantidade por dose, estoque e horários.
4. **Próximas Doses**: na hora da dose abre o lembrete "A dose foi tomada?" (toque ou voz).
5. **Compartilhamento → Convidar pessoa**: a pessoa precisa ter conta (use `/convite`, o cadastro de convidado).

### Comandos

| Comando | O que faz |
|---|---|
| `npm run dev` | Servidor de desenvolvimento (porta 5173) |
| `npm run build` | Gera a versão de produção em `dist/` |
| `npm run preview` | Serve o `dist/` localmente |
| `npm test` | Testes unitários (Vitest) |

### Docker

```bash
docker build -t cronomed-web .
docker run -p 3000:80 -e BACKEND_URL=http://host.docker.internal:8080 cronomed-web
```

O nginx entrega o site e repassa `/api` ao backend. Em produção, use HTTPS e `COOKIE_SECURE=true` no backend.

## Telas

| Rota | Tela | Quem acessa |
|---|---|---|
| `/` | Landing page (contato envia para `POST /public/contact`) | todos |
| `/entrar` · `/cadastro` · `/esqueci-senha` · `/redefinir-senha` | Autenticação | visitante |
| `/convite` | Cadastro de convidado em 3 etapas | visitante |
| `/app/doses` | Próximas doses, adesão, caixa, lembrete com voz | titular e convidados |
| `/app/medicamentos` | Seis compartimentos + formulário | gerenciar |
| `/app/estoque` | Barras de estoque e reabastecimento | titular e convidados |
| `/app/historico` | Filtros, tabela paginada e exportação CSV | titular e convidados |
| `/app/compartilhamento` | Pessoas, nível de acesso, convite (LGPD) | titular |
| `/app/dispositivos` | Cadastro, renomear e desvincular caixas | titular |
| `/app/configuracoes` | Acessibilidade, alertas, exportar/excluir dados | titular |
| `/app/cadastro` · `/app/seguranca` | Alterar cadastro e senha | todos |

Itens do menu que o seu nível de acesso não permite aparecem com cadeado.

## Acessibilidade

- Alto contraste (≥ 7:1), 3 tamanhos de texto, navegação por teclado, foco visível e rótulos para leitor de tela.
- Cada compartimento usa **cor + forma + número** (nunca só a cor).
- Narração e comando de voz (opcionais, em Configurações).

## Como o front fala com o backend

- `src/lib/api.js` concentra todas as chamadas (`/api/v1`). Envia `X-Client-Type: web` e `credentials: include`; os tokens ficam em cookies HttpOnly (o JavaScript não os lê).
- Ao receber `401`, tenta **uma** renovação (`/auth/refresh`) e repete a chamada; se falhar, volta ao login.
- Cada resposta de dose leva um `eventId` (UUID) → o servidor ignora repetições (idempotência).
- Edição de medicamento envia `version`; se outra pessoa alterou, o servidor responde `409 STALE_DATA` e o portal recarrega.

## Limitações conhecidas (o backend ainda não tem)

- **CPF, celular e data de nascimento**: ficam salvos só no navegador (tela "Alterar cadastro"). Login por CPF mostra aviso; use o e-mail.
- **Pausar acesso** de um convidado: o backend só tem alterar nível e remover.
- **Wi-Fi e firmware da caixa**: o backend não expõe esses dados; a tela mostra bateria, status online e última sincronização.
- **Duração/data de início do tratamento**: não existem na API.
- **Ativação de conta por e-mail** e notificações push: dependem de servidor de e-mail/FCM.
- Os depoimentos da landing são marcadores (`[Depoimento]`), como no protótipo.

## Estrutura

```
src/
  lib/          api, formatação, validadores, voz, regras de dose, preferências
  context/      auth, paciente selecionado, preferências, avisos
  hooks/        consultas da API (react-query)
  components/   ícones, formulários, modal, layout do app, guardas de rota
  pages/        uma tela por arquivo
  styles/       tokens (identidade visual), base, layout, telas, landing
```

## Segurança

Nunca versione `.env`, `secrets.h` ou senhas. Este projeto não guarda segredos: só endereços.
