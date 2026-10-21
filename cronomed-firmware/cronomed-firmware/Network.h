// Wi-Fi, hora (NTP) e MQTT. Nada aqui bloqueia o loop: sem rede, os alarmes locais continuam normalmente.
#pragma once

#include <Arduino.h>

namespace Network {

void begin();

/** Chame a cada iteracao do loop(). Cuida de reconexoes, heartbeat, envio de eventos e recebimento do plano. */
void loop();

/** MAC no formato dos topicos: maiusculo, sem separadores (ex.: AABBCCDDEEFF). */
String topicId();

/** MAC no formato que deve ser cadastrado no sistema (ex.: AA:BB:CC:DD:EE:FF). */
String macAddress();

bool mqttConnected();

}  // namespace Network
