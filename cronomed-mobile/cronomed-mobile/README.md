# CronoMed Mobile (Android / Kotlin)

Aplicativo Android do CronoMed, a caixa organizadora de medicamentos. Escrito em **Kotlin**, com telas em
**XML**, **Firebase Authentication** (login) e **Cloud Firestore** (dados), seguindo o que foi ensinado
nas aulas de Kotlin/Android (Aula 3, Aula 4 e Firebase Auth).

> **Antes de rodar:** o projeto precisa do arquivo `google-services.json` do **seu** projeto Firebase.
> Ele não vem junto de propósito (identifica o seu projeto e está no `.gitignore`). Veja o passo a passo abaixo.

## O que o professor ensinou e onde aparece

| Conteúdo das aulas | Onde está no projeto |
|---|---|
| `Activity` + layout XML (`setContentView`, `findViewById`) | `ui/*Activity.kt` e `res/layout/activity_*.xml` |
| `Intent`, `startActivity`, `putExtra` / `getStringExtra` (mesma chave nos dois lados) | `DosesActivity` → `LembreteActivity` (`Notificacoes.EXTRA_DOSE`), `MedicamentosActivity` → `FormularioMedicamentoActivity` (`EXTRA_COMPARTIMENTO`), `BoasVindasActivity` → `ConviteActivity` (`EXTRA_CODIGO`) |
| `finish()` para não acumular telas na pilha | troca de abas (`BaseActivity.configurarNavegacao`), login/logout (`irParaLimpandoPilha`) |
| `FloatingActionButton` (inclusive o botão voltar) | `view_topo_voltar.xml` (voltar chama `finish()`), FAB "+" em Compartilhamento e Dispositivos |
| Padrão **DAO** (pacote `dao`, `companion object`) | `dao/UsuarioDao`, `PacienteDao`, `MedicamentoDao`, `DoseDao`, `AcessoDao`, `ConviteDao`, `DispositivoDao`, `ContaDao` |
| `Log.i / d / e / v / w` | `Erros.kt` (e), `LoginActivity` (i), `Sincronizador` (d), `AlarmeReceiver` (w, v) |
| Firebase: `google-services.json`, plugin `google-services`, dependências `firebase-auth` e `firebase-firestore` | `app/build.gradle.kts`, `build.gradle.kts` |
| `signInWithEmailAndPassword(...).addOnCompleteListener(this) {}` | `LoginActivity` |
| `createUserWithEmailAndPassword` | `CadastroActivity`, `ConviteActivity` |
| E-mail de redefinição de senha | `EsqueciSenhaActivity` (`sendPasswordResetEmail`, reenvio liberado após 60 s) |
| Excluir a própria conta (após reautenticar) | `SegurancaActivity` (`reauthenticate` + `ContaDao` + `user.delete()`) |
| Sair da conta e voltar ao login | `MenuActivity.sair()` (`signOut`) |

## Funcionalidades

- **Conta:** boas-vindas, login por e-mail, cadastro (CPF validado, senha com checklist de requisitos), recuperação de senha, biometria opcional ao abrir o app.
- **Doses:** lista do dia, adesão dos últimos 30 dias, aviso de estoque baixo, confirmação "Já tomei".
- **Lembrete na hora do remédio:** alarme (`AlarmeScheduler`), notificação em tela cheia, até **12 lembretes** repetidos a cada 2/5/10/15 min, recriados após reiniciar o celular. Funciona **sem internet** (lê uma cópia local da agenda).
- **Voz:** narração do lembrete (TextToSpeech) e resposta "sim/não" falada (SpeechRecognizer).
- **Medicamentos:** 6 compartimentos, cada um com **cor + forma + número** (para quem não distingue cores), horários, duração, estoque, instruções.
- **Estoque:** dias restantes, selos Em dia / Atenção / Acabando, reabastecer.
- **Histórico:** 7/30/90 dias, adesão, exportação em CSV (planilha).
- **Compartilhamento:** convite por link/código, níveis **Visualização** e **Gerenciamento**, pausar, alterar nível, remover; convidado cadastra-se como familiar, cuidador, enfermeiro(a) ou médico(a).
- **Dispositivos:** cadastro das caixas do paciente (nome + código), com bateria e última sincronização quando existirem.
- **Acessibilidade:** alto contraste, texto grande/extra grande, narração e comando por voz.
- **LGPD:** exclusão da conta e de todos os dados.

## Como rodar

### 1. Criar o projeto no Firebase (uma vez)

1. Acesse <https://console.firebase.google.com> → **Adicionar projeto** (ex.: `cronomed`).
2. Dentro do projeto → **Adicionar app → Android**. Em *Nome do pacote* digite exatamente **`br.com.cronomed`**.
3. Baixe o **`google-services.json`** e coloque em **`cronomed-mobile/app/google-services.json`** (na pasta `app`, ao lado do `build.gradle.kts`).
4. **Build → Authentication → Primeiros passos → E-mail/senha → Ativar.**
5. **Build → Firestore Database → Criar banco de dados** (modo produção, região `southamerica-east1` / São Paulo).
6. Em **Firestore → Regras**, cole todo o conteúdo do arquivo [`firestore.rules`](firestore.rules) e clique em **Publicar**.

### 2. Abrir e executar

1. Instale o **Android Studio** (versão Hedgehog ou mais nova; usa JDK 17, já incluso).
2. **File → Open** e escolha a pasta `cronomed-mobile`. Aguarde o *Gradle Sync*.
3. Crie um emulador (**Device Manager**, Android 7.0+; para testar voz e notificações use Android 13+ com Google Play) ou conecte um celular com depuração USB.
4. Clique em **Run ▶**.

### 3. Roteiro rápido de teste

1. **Criar conta** → preencha os dados → você cai em *Doses*.
2. **Remédios** → toque num compartimento livre → cadastre um remédio com um horário daqui a 2 ou 3 minutos e estoque 10.
3. Volte para **Doses** → permita as notificações → aguarde o horário: o aviso toca e repete.
4. Toque em **Já tomei** → o estoque diminui (aba *Estoque*) e a dose vai para o *Histórico*.
5. **Menu (☰) → Compartilhamento → +** → crie um convite e envie o link/código; em outro aparelho/emulador use **Tenho um convite**.
6. **Menu → Configurações** → teste alto contraste e tamanho do texto.

## Estrutura

```
app/src/main/java/br/com/cronomed/
  CronoMedApp.kt        Application: canal de notificação, tema claro
  model/                Dados do app (Usuario, Medicamento, Dose, Acesso, Convite, ...)
  dao/                  Acesso ao Firestore (padrão DAO)
  data/                 Sessão em memória, preferências, agenda local, sincronizador
  alarme/               AlarmManager, receivers (alarme e boot), notificações
  util/                 Validadores, máscaras, formatadores, erros, voz, biometria, CSV
  ui/                   Activities, adapters e a BaseActivity
app/src/main/res/       Layouts, ícones, temas, textos (pt-BR)
firestore.rules         Regras de segurança do banco
```

Modelo no Firestore: `usuarios/{uid}`, `pacientes/{uid-do-titular}` com as subcoleções `medicamentos/{1..6}`,
`doses/{compartimento_aaaaMMddHHmm}` e `dispositivos/{id}`, mais `acessos/{pacienteId_uidConvidado}` e `convites/{codigo}`.

## Limitações (leia antes de apresentar)

- **Este app usa Firebase, não o back-end Spring Boot do projeto.** Os dados do app ficam no Firestore; a API Spring Boot e o firmware do ESP32 não são chamados por ele. As telas só falam com as classes `dao`, então trocar a fonte de dados (por exemplo, para a API REST) é uma mudança concentrada nessa pasta, mas não está feita.
- **Login por CPF não existe.** O Firebase Auth só autentica por e-mail; autenticar por CPF com segurança exigiria um servidor (Cloud Functions). O CPF é coletado e validado no cadastro, mas o login é por e-mail.
- **Nova senha:** o e-mail do Firebase leva a uma página do próprio Firebase; não há tela de "nova senha" dentro do app.
- **Configuração de Wi-Fi da caixa por Bluetooth** não foi implementada (depende do firmware, que ainda não foi testado com hardware). O cadastro de caixa é manual.
- **Não foi compilado nem executado no ambiente em que foi escrito** (sem Android SDK). Foi conferido por scripts (XML válido, todo recurso/ID/texto referenciado existe, classes do manifesto existem, imports e parênteses), mas o primeiro *Gradle Sync* pode apontar ajustes pequenos. Se aparecer erro, copie a mensagem completa do *Build*.
- Alarmes no Android 12+ exigem a permissão "Alarmes e lembretes" (o app avisa); alguns fabricantes (Xiaomi, Samsung, etc.) também limitam apps em segundo plano: desative a otimização de bateria para o CronoMed.

## Segurança

- Não envie o `google-services.json` nem chaves para repositórios públicos (já está no `.gitignore`).
- As regras do Firestore impedem que um convidado se promova: o nível do acesso é copiado do convite que o titular criou.
- O código do convite é aleatório e longo; quem o possui pode ler o convite, por isso ele expira em 7 dias e só vale uma vez.
