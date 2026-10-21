// Copie este arquivo para "secrets.h" e preencha. O secrets.h NAO vai para o GitHub (veja .gitignore).
#pragma once

#define WIFI_SSID "nome-da-sua-rede"
#define WIFI_PASSWORD "senha-da-rede"

// Broker MQTT (Mosquitto do docker-compose). Para testes em casa use o IP do computador na rede local
// (ex.: "192.168.0.10"), nunca "localhost". Em producao use TLS (porta 8883).
#define MQTT_HOST "192.168.0.10"
#define MQTT_PORT 1883
#define MQTT_USE_TLS 0

// Usuario MQTT da caixa = MAC sem separadores (ex.: AABBCCDDEEFF). A senha e criada no broker com
//   ./scripts/setup-mosquitto.sh add AABBCCDDEEFF <senha>
#define MQTT_PASSWORD "senha-mqtt-desta-caixa"

// Opcional: certificado da CA do broker (necessario se MQTT_USE_TLS 1). Sem ele a conexao TLS e
// aceita sem validar o servidor (use so em desenvolvimento).
// #define MQTT_CA_CERT "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n"
