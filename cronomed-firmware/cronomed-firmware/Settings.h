// Credenciais da caixa: Wi-Fi, IP do broker MQTT e segredo do dispositivo.
// Origem (em ordem): 1) salvas na flash pelo pareamento Bluetooth; 2) secrets.h (modo desenvolvimento).
#pragma once

#include <Arduino.h>

namespace Settings {

void begin();

const String& wifiSsid();
const String& wifiPass();
const String& broker();         // IP/host do Mosquitto
const String& deviceSecret();   // segredo devolvido pela API no cadastro da caixa (so guardado)

bool hasStoredWifi();           // true se o pareamento Bluetooth ja gravou uma rede
bool provisioningNeeded();      // true = nao ha Wi-Fi utilizavel: subir o pareamento Bluetooth

/** Grava o resultado do pareamento Bluetooth. */
void saveProvisioned(const String& ssid, const String& pass, const String& broker, const String& secret);

/** Reset de fabrica: apaga o Wi-Fi salvo e forca o pareamento Bluetooth no proximo boot. */
void factoryReset();

}  // namespace Settings
