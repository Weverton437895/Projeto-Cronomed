#include "Provisioning.h"

#include "Alarm.h"
#include "CronoNet.h"
#include "config.h"
#include "Settings.h"

#if PROVISIONING_BLE
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <WiFi.h>
#endif

namespace {

#if PROVISIONING_BLE

// UUIDs do protocolo de pareamento (os mesmos usados em cronomed-web/src/pages/AddDevice.jsx).
const char* SERVICE_UUID = "19b10000-e8f2-537e-4f6c-d104768a1214";
const char* CHAR_MAC_UUID = "19b10000-e8f2-537e-4f6c-d104768a1215";
const char* CHAR_SSID_UUID = "19b10001-e8f2-537e-4f6c-d104768a1214";
const char* CHAR_PASS_UUID = "19b10002-e8f2-537e-4f6c-d104768a1214";
const char* CHAR_SECRET_UUID = "19b10003-e8f2-537e-4f6c-d104768a1214";
const char* CHAR_BROKER_UUID = "19b10004-e8f2-537e-4f6c-d104768a1214";

String g_ssid, g_pass, g_broker, g_secret;
bool g_haveSsid = false;
volatile bool g_done = false;
uint32_t g_doneAt = 0;
bool g_started = false;

class WriteCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String uuid = String(c->getUUID().toString().c_str());
    String value = String(c->getValue().c_str());   // pode ser vazio (ex.: rede Wi-Fi aberta, sem senha)

    if (uuid.equalsIgnoreCase(CHAR_SSID_UUID)) {
      g_ssid = value;
      g_haveSsid = value.length() > 0;
      LOGF("[ble] rede Wi-Fi recebida: %s\n", g_ssid.c_str());
    } else if (uuid.equalsIgnoreCase(CHAR_PASS_UUID)) {
      g_pass = value;
      LOGF("[ble] senha do Wi-Fi recebida\n");
    } else if (uuid.equalsIgnoreCase(CHAR_BROKER_UUID)) {
      g_broker = value;
      LOGF("[ble] broker recebido: %s\n", g_broker.c_str());
    } else if (uuid.equalsIgnoreCase(CHAR_SECRET_UUID)) {
      g_secret = value;
      LOGF("[ble] segredo do dispositivo recebido\n");
      // O segredo e o ultimo item enviado pela web: dispara a gravacao.
      if (g_haveSsid) {
        Settings::saveProvisioned(g_ssid, g_pass, g_broker, g_secret);
        g_done = true;
        g_doneAt = millis();
      }
    }
  }
};

WriteCallbacks g_callbacks;

#endif

uint32_t g_pressStart = 0;

}  // namespace

namespace Provisioning {

void begin() {
#if PROVISIONING_BLE
  WiFi.mode(WIFI_STA);   // necessario para o MAC ser lido corretamente
  String mac = CronoNet::macAddress();
  String bleName = "CronoMed-" + mac.substring(12, 14) + mac.substring(15, 17);
  LOGF("============================================\n");
  LOGF("MODO DE PAREAMENTO BLUETOOTH\n");
  LOGF("MAC desta caixa: %s\n", mac.c_str());
  LOGF("Nome Bluetooth: %s\n", bleName.c_str());
  LOGF("Abra a pagina 'Cadastrar caixa' da web (Chrome/Edge).\n");
  LOGF("============================================\n");

  BLEDevice::init(bleName.c_str());
  BLEServer* server = BLEDevice::createServer();
  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* macChar = service->createCharacteristic(CHAR_MAC_UUID, BLECharacteristic::PROPERTY_READ);
  macChar->setValue(mac.c_str());

  const char* writeUuids[] = {CHAR_SSID_UUID, CHAR_PASS_UUID, CHAR_BROKER_UUID, CHAR_SECRET_UUID};
  for (const char* u : writeUuids) {
    BLECharacteristic* ch = service->createCharacteristic(u, BLECharacteristic::PROPERTY_WRITE);
    ch->setCallbacks(&g_callbacks);
  }
  service->start();

  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
  g_started = true;
#endif
}

void loop() {
#if PROVISIONING_BLE
  if (g_started && g_done && millis() - g_doneAt > 1500) {
    LOGF("[ble] pareamento concluido. Reiniciando para conectar ao Wi-Fi...\n");
    BLEDevice::deinit(true);
    ESP.restart();
  }
#endif
}

void beginButton() {
  pinMode(RESET_BUTTON_PIN, INPUT_PULLUP);
#if RESET_BUTTON_ALT_PIN >= 0
  pinMode(RESET_BUTTON_ALT_PIN, INPUT_PULLUP);
#endif
}

void checkResetButton() {
  bool pressed = digitalRead(RESET_BUTTON_PIN) == LOW;
#if RESET_BUTTON_ALT_PIN >= 0
  pressed = pressed || digitalRead(RESET_BUTTON_ALT_PIN) == LOW;
#endif
  if (pressed) {
    if (g_pressStart == 0) g_pressStart = millis() ? millis() : 1;
    if (millis() - g_pressStart >= RESET_BUTTON_HOLD_MS) {
      LOGF("[reset] botao segurado: apagando Wi-Fi salvo e voltando ao pareamento Bluetooth\n");
      Alarm::blip(300);
      Settings::factoryReset();
      delay(300);
      ESP.restart();
    }
  } else {
    g_pressStart = 0;
  }
}

}  // namespace Provisioning
