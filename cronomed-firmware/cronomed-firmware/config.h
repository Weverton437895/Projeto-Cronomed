// ============================================================================
// CronoMed - Firmware da caixa (ESP32)
// Configuracoes de hardware e comportamento. Segredos (Wi-Fi, MQTT) ficam em secrets.h.
// ============================================================================
#pragma once

#include <Arduino.h>

// ------------------------------- Hardware ---------------------------------
// Mapa de pinos OFICIAL da caixa (conferido com o esquema de ligacao da equipe de hardware).
// Evite os pinos de boot do ESP32 (0, 2, 12, 15) para novas ligacoes.

#define NUM_COMPARTMENTS 6

// Um reed switch por compartimento (compartimento 1 = primeiro pino). Ligacao: pino <-> reed <-> GND,
// com INPUT_PULLUP interno. Tampa fechada = ima proximo = reed fechado = pino em LOW.
static const uint8_t REED_PINS[NUM_COMPARTMENTS] = {13, 14, 16, 17, 26, 27};
#define REED_OPEN_LEVEL HIGH          // nivel lido quando a tampa esta ABERTA
#define REED_DEBOUNCE_MS 60

// Um LED comum por compartimento (mesma ordem dos reeds).
//  C1-C4 (18, 19, 23, 5): pino -> resistor 220 ohm -> LED -> GND.
//  C5-C6 (32, 33): pino -> 1k -> base do transistor NPN BC548; o LED (com 220 ohm) fica ligado nos 5 V.
// Nos dois casos, nivel HIGH no pino = LED aceso.
static const uint8_t LED_PINS[NUM_COMPARTMENTS] = {18, 19, 23, 5, 32, 33};
#define LED_ON_LEVEL HIGH

// Buzzer passivo (precisa de tom por PWM/LEDC, nao basta ligar o pino).
#define BUZZER_PIN 25
#define BUZZER_FREQ_HZ 2400

// Botao de reset de fabrica: GPIO 4 -> botao -> GND (INPUT_PULLUP). Segurado por 3 s apaga o Wi-Fi
// salvo e volta ao modo de pareamento Bluetooth.
#define RESET_BUTTON_PIN 4
#define RESET_BUTTON_ALT_PIN 0        // botao BOOT da placa DevKit tambem serve (para testes na bancada); -1 desliga
#define RESET_BUTTON_HOLD_MS 3000

// Bateria Li-Ion 18650: divisor 100k/100k ate o GPIO 34 (ADC1). Use -1 se a caixa so tiver alimentacao da rede.
#define BATTERY_PIN 34
#define BATTERY_DIVIDER_RATIO 2.0f    // dois resistores iguais => tensao real = leitura x 2
#define BATTERY_EMPTY_MV 3300
#define BATTERY_FULL_MV 4200
#define BATTERY_LOW_PERCENT 20        // abaixo disso o servidor gera o alerta de bateria fraca

// ------------------------------- Alarmes ----------------------------------
// Alarme normal: LED pisca a cada 500 ms e o buzzer toca 150 ms a cada 1,5 s.
// Se a tampa nao for aberta em 15 s, vira URGENTE: LED a cada 150 ms e bipes de 100 ms a cada 200 ms.
#define ALARM_SLOW_PERIOD_MS 1500     // periodo do bipe normal
#define ALARM_BEEP_MS 150             // duracao do bipe normal
#define ALARM_LED_SLOW_HALF_MS 500    // meio periodo do pisca normal
#define ALARM_FAST_PERIOD_MS 200      // periodo do bipe urgente
#define ALARM_FAST_BEEP_MS 100        // duracao do bipe urgente
#define ALARM_LED_FAST_HALF_MS 150    // meio periodo do pisca urgente
#define ALARM_INTENSIFY_AFTER_S 15    // segundos ate o alarme virar urgente
#define ALARM_TIMEOUT_S 60            // sem abrir a tampa em 60 s: o alarme para e a dose e dada como perdida
#define CONFIRM_FLASH_MS 80           // confirmacao ao fechar a tampa: 2 piscadas + 2 bipes de 80 ms
#define DEFAULT_TOLERANCE_S 3600      // janela de tolerancia (RNF04: 1 hora), usada se o plano nao informar

// ------------------------------- Pareamento -------------------------------
// 1 = se a caixa nao tiver Wi-Fi salvo, sobe o pareamento por Bluetooth (pagina "Cadastrar caixa" da web).
// Exige o esquema de particao "Huge APP (3MB No OTA/1MB SPIFFS)" na Arduino IDE (Tools > Partition Scheme).
#define PROVISIONING_BLE 1

// ------------------------------- Rede / MQTT ------------------------------
#define MQTT_TOPIC_PREFIX "cronomed/devices"   // mesmo valor de cronomed.mqtt.topic-prefix no backend
#define MQTT_BUFFER_BYTES 32768                // o plano de 7 dias pode ser grande
#define MQTT_RECONNECT_MS 5000
#define WIFI_RETRY_MS 10000
#define HEARTBEAT_MS 60000
#define EVENT_RETRY_MS 15000                   // reenvio de eventos ainda nao confirmados
#define EVENT_BATCH_MAX 20
#define NTP_SERVER_1 "pool.ntp.org"
#define NTP_SERVER_2 "time.google.com"

// ------------------------------- Armazenamento ----------------------------
#define MAX_DOSES 400
#define MAX_EVENTS 200
#define EVENT_RETENTION_S (30UL * 24UL * 3600UL)   // RNF12: ate 30 dias offline

// Epoch minimo para considerar o relogio valido (1 jan 2024).
#define MIN_VALID_EPOCH 1704067200UL

#define LOGF(...) do { Serial.printf(__VA_ARGS__); } while (0)
