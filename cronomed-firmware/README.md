# CronoMed – Firmware da caixa (ESP32)

Firmware em C++ (Arduino IDE) da caixa de medicamentos. Ele:

- recebe do servidor, por **MQTT**, o plano de horários (só números) e guarda na flash (**LittleFS**);
- dispara sozinho os alarmes (**LED** da cor do compartimento + **buzzer**) pelo relógio, mesmo **sem Wi-Fi**;
- detecta a abertura de cada compartimento (**reed switch**), para o alarme e envia o evento `BOX_OPENED`;
- guarda os eventos na flash (até 30 dias) e só apaga os que o servidor confirmar (`acks`);
- envia um **heartbeat** a cada 60 s com o nível da bateria.

> **Estado:** a lógica (plano, janela de alarme, fila de eventos, JSON) foi verificada no computador e o código
> compila contra as bibliotecas. **Ainda não foi testado numa placa real**: faça o teste de bancada abaixo.

## 1. Montagem (mapa de pinos oficial da caixa, em `config.h`)

| Componente | Pino ESP32 | Ligação |
|---|---|---|
| Reed switch compartimentos 1–6 | 13, 14, 16, 17, 26, 27 | pino ↔ reed ↔ GND (pull-up interno; tampa fechada = LOW) |
| LEDs compartimentos 1–4 | 18, 19, 23, 5 | pino → 220 Ω → LED → GND |
| LEDs compartimentos 5–6 | 32, 33 | pino → 1 kΩ → base do NPN BC548; LED + 220 Ω nos 5 V (HIGH = aceso) |
| Buzzer **passivo** | 25 | tom por PWM (LEDC) |
| Botão de reset de fábrica | 4 (e BOOT/GPIO 0 para testes) | botão ↔ GND; segurar 3 s apaga o Wi-Fi e volta ao pareamento |
| Bateria 18650 (medição) | 34 | divisor 100 kΩ/100 kΩ (`BATTERY_DIVIDER_RATIO 2.0`); alerta abaixo de 20 % |

**Comportamento do alarme:** LED pisca a cada 500 ms e o buzzer toca 150 ms a cada 1,5 s. Se a tampa não for aberta em
15 s, vira **urgente** (LED a cada 150 ms, bipes de 100 ms a cada 200 ms). Se a tampa **não for aberta em 60 s** (`ALARM_TIMEOUT_S`), o alarme para sozinho, a caixa envia o evento
`NOT_OPENED` ("tampa do compartimento não aberta") e o servidor marca a dose como **perdida** (`MISSED`) e alerta o titular
e os cuidadores. Abrir a tampa a tempo silencia; fechar de novo dá
2 piscadas + 2 bipes rápidos de confirmação.

## 2. Instalar no Arduino IDE

1. **Placa:** *Ferramentas → Placa → Gerenciador de placas* → instale **esp32 (Espressif Systems)**. Selecione *ESP32 Dev Module*.
2. **Bibliotecas** (*Sketch → Incluir biblioteca → Gerenciar bibliotecas*):
   - **ArduinoJson** (Benoit Blanchon), versão **7.x**
   - **PubSubClient** (Nick O'Leary)
3. Abra `cronomed-firmware/cronomed-firmware.ino` (a pasta do sketch precisa ter o mesmo nome do `.ino`).
4. Copie `secrets.example.h` para **`secrets.h`** e preencha Wi-Fi, IP do broker e a senha MQTT. O `secrets.h` não vai para o GitHub.
5. **Esquema de partição:** *Ferramentas → Partition Scheme → **Huge APP (3MB No OTA/1MB SPIFFS)*** (o Bluetooth aumenta o tamanho do programa).
6. Compile e envie. Abra o **Monitor Serial a 115200**.

### Wi-Fi: pareamento Bluetooth ou secrets.h
- **Pareamento Bluetooth (padrão do produto):** com `WIFI_SSID "nome-da-sua-rede"` (valor de exemplo) ou depois do reset de
  fábrica, a caixa anuncia `CronoMed-XXXX`. Na web (Chrome/Edge), *Dispositivos → Cadastrar caixa → Conectar via Bluetooth*:
  a página lê o MAC, cadastra a caixa na conta e envia Wi-Fi, IP do computador e o segredo. A caixa salva e reinicia.
- **secrets.h (desenvolvimento):** se o `secrets.h` tiver uma rede real e a caixa não tiver sido pareada, ela usa essa rede.
- Usuário/senha MQTT: `MQTT_USER`/`MQTT_PASSWORD` do `secrets.h` (o `iniciar-cronomed.ps1` cria o usuário `cronomed-box` com a
  senha `MQTT_BOX_PASSWORD` do `.env`). O ACL do broker libera só os tópicos do MAC usado como *client id*.

## 3. Cadastrar a caixa no sistema

> Pelo pareamento Bluetooth tudo isto é feito pela página da web. O passo a passo abaixo é o caminho manual.

No Monitor Serial, ao ligar, aparece:

```
MAC desta caixa (cadastre no sistema): AA:BB:CC:DD:EE:FF
Usuário MQTT: AABBCCDDEEFF
```

1. No Swagger do backend, `POST /api/v1/devices` com esse MAC e um nome. Associe o compartimento a um paciente
   (`PUT /api/v1/devices/{id}/compartments/{compartmentId}`) e cadastre um medicamento com horário.
2. No computador do backend, crie o usuário MQTT da caixa (o usuário é o MAC sem separadores):
   ```
   ./scripts/setup-mosquitto.sh add AABBCCDDEEFF <senha-mqtt-da-caixa>
   docker compose restart mosquitto
   ```
   Use a mesma senha em `MQTT_PASSWORD` do `secrets.h`.
3. O backend precisa estar com MQTT ligado: variáveis `MQTT_ENABLED=true`, `MQTT_BROKER_URL=tcp://localhost:1883`,
   `MQTT_USERNAME=cronomed-api` e `MQTT_PASSWORD=<MQTT_API_PASSWORD do .env>`.
4. A caixa e o computador precisam estar **na mesma rede**, e `MQTT_HOST` deve ser o IP do computador (não `localhost`).
   Libere a porta 1883 no firewall do Windows.

## 4. Teste de bancada

1. Ligue a caixa: o Monitor Serial mostra `[wifi]`, `[mqtt] conectado` e, em seguida, `[plano] aplicado: ... doses=N`.
2. Cadastre um medicamento com horário daqui a 2 minutos. Quando chegar a hora: o LED do compartimento pisca na
   cor dele e o buzzer apita. Depois de 15 segundos sem abrir, o alarme fica urgente.
3. Abra a tampa: o alarme para na hora e aparece `[eventos] abertura do compartimento N registrada`.
4. No backend, `GET /api/v1/patients/{id}/events` deve mostrar o evento; confirme a dose pelo app/Swagger.
5. **Teste offline:** desligue o Wi-Fi do roteador. O alarme deve continuar tocando no horário e a abertura deve
   ser guardada; ao religar, o evento é enviado e confirmado.

## 5. Como funciona (resumo dos arquivos)

| Arquivo | Função |
|---|---|
| `cronomed-firmware.ino` | `setup()` e `loop()`: junta os módulos |
| `config.h` | pinos, tempos e limites |
| `secrets.h` (você cria) | Wi-Fi e MQTT |
| `CronoNet.*` | Wi-Fi, hora (NTP), MQTT, heartbeat, envio de eventos e recebimento do plano |
| `Plan.*` | plano na flash; calcula qual dose está na janela de alarme |
| `Alarm.*` | LEDs, buzzer, alarme urgente e confirmação ao fechar |
| `Settings.*` | Wi-Fi/broker salvos na flash (Preferences) ou do `secrets.h` |
| `Provisioning.*` | pareamento Bluetooth e botão de reset de fábrica |
| `Sensors.*` | reed switches com debounce |
| `EventQueue.*` | fila de eventos na flash, com reenvio até o servidor confirmar |
| `Battery.*` | nível da bateria |

Regras importantes:
- A caixa só trabalha com **epoch UTC**. A hora vem do NTP; se não houver, usa a hora do servidor contida no plano.
- Abrir a tampa **nunca** baixa o estoque: isso só acontece quando o usuário confirma "Sim" no app (regra do servidor).
- Se a hora ainda não estava sincronizada no momento da abertura, o evento vai com `elapsedMs` e o servidor calcula a hora real.
- O servidor rejeita eventos inválidos em definitivo (por exemplo, compartimento sem paciente); a caixa os descarta em vez de reenviar.

## 6. Limitações conhecidas

- O `PubSubClient` publica com QoS 0. A entrega é garantida pela fila persistente + confirmações do servidor + reenvio a cada 15 s.
- Só MQTT (preferido pelo documento). O gateway HTTP do backend existe, mas não foi implementado no firmware.
- Sem atualização OTA. O pareamento Bluetooth não é criptografado além do que o BLE oferece: faça-o perto da caixa.
- Para produção, ative TLS (`MQTT_USE_TLS 1` + certificado da CA) e proteja o acesso físico à placa.
