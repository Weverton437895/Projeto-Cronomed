// Copie este arquivo para "secrets.h" e preencha. O secrets.h NAO vai para o GitHub (veja .gitignore).
#pragma once

// Wi-Fi: so e usado se a caixa NAO tiver sido pareada por Bluetooth (modo desenvolvimento).
// Deixe "nome-da-sua-rede" para forcar o pareamento Bluetooth.
#define WIFI_SSID "nome-da-sua-rede"
#define WIFI_PASSWORD "senha-da-rede"

// Broker MQTT (Mosquitto do docker-compose). Para testes em casa use o IP do computador na rede local
// (ex.: "192.168.0.10"), nunca "localhost". Em producao use TLS (porta 8883).
#define MQTT_HOST "192.168.0.10"
#define MQTT_PORT 1883
#define MQTT_USE_TLS 0

// Usuario e senha MQTT usados pela caixa.
//  - Recomendado (pareamento Bluetooth): usuario compartilhado das caixas, criado pelo iniciar-cronomed.ps1
//    com a senha MQTT_BOX_PASSWORD do .env do backend. O broker so deixa cada caixa ver os proprios topicos.
//  - Alternativa: deixe MQTT_USER "" e crie um usuario por caixa (usuario = MAC sem separadores):
//      ./scripts/setup-mosquitto.sh add AABBCCDDEEFF <senha>
#define MQTT_USER "cronomed-box"
#define MQTT_PASSWORD "mesma-senha-do-MQTT_BOX_PASSWORD-no-env"

// Opcional: certificado da CA do broker (necessario se MQTT_USE_TLS 1). Sem ele a conexao TLS e
// aceita sem validar o servidor (use so em desenvolvimento).
// #define MQTT_CA_CERT "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n"
