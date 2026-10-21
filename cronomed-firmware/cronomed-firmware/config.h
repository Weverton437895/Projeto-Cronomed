// ============================================================================
// CronoMed - Firmware da caixa (ESP32)
// Configuracoes de hardware e comportamento. Segredos (Wi-Fi, MQTT) ficam em secrets.h.
// ============================================================================
#pragma once

#include <Arduino.h>

// ------------------------------- Hardware ---------------------------------
// CONFIRME OS PINOS COM QUEM MONTA O CIRCUITO. Evite os pinos de boot do ESP32 (0, 2, 5, 12, 15).

#define NUM_COMPARTMENTS 6

// Um reed switch por compartimento (compartimento 1 = primeiro pino). Ligacao: pino <-> reed <-> GND,
// com INPUT_PULLUP interno. Tampa fechada = ima proximo = reed fechado = pino em LOW.
static const uint8_t REED_PINS[NUM_COMPARTMENTS] = {13, 14, 27, 26, 32, 33};
#define REED_OPEN_LEVEL HIGH          // nivel lido quando a tampa esta ABERTA
#define REED_DEBOUNCE_MS 60

// Fita de LEDs enderecaveis (WS2812B): 1 LED por compartimento, na mesma ordem.
#define LED_PIN 4
#define LED_BRIGHTNESS 80             // 0-255

// Buzzer piezoeletrico (PWM).
#define BUZZER_PIN 25
#define BUZZER_FREQ_HZ 2400

// Bateria: divisor resistivo ate um pino ADC1 (32-39). Use -1 se a caixa so tiver alimentacao da rede.
#define BATTERY_PIN 34
#define BATTERY_DIVIDER_RATIO 2.0f    // ex.: dois resistores iguais => tensao real = leitura x 2
#define BATTERY_EMPTY_MV 3300
#define BATTERY_FULL_MV 4200

// ------------------------------- Alarmes ----------------------------------
#define ALARM_SLOW_PERIOD_MS 1500     // bipe normal
#define ALARM_FAST_PERIOD_MS 400      // bipe acelerado (RF20)
#define ALARM_BEEP_MS 150             // duracao de cada bipe
#define ALARM_INTENSIFY_AFTER_S 300   // apos 5 min sem abrir, o alarme acelera
#define DEFAULT_TOLERANCE_S 3600      // janela de tolerancia (RNF04: 1 hora), usada se o plano nao informar

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
