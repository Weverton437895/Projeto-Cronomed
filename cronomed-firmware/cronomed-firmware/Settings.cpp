#include "Settings.h"

#include <Preferences.h>

#include "config.h"
#include "secrets.h"

namespace {

Preferences g_prefs;
String g_ssid, g_pass, g_broker, g_secret;
bool g_stored = false;
bool g_forceBle = false;

bool secretsConfigured() {
  const char* s = WIFI_SSID;
  return s[0] != '\0' && strcmp(s, "nome-da-sua-rede") != 0;
}

}  // namespace

namespace Settings {

void begin() {
  g_prefs.begin("cronomed", true);
  String ssid = g_prefs.getString("ssid", "");
  String pass = g_prefs.getString("pass", "");
  String broker = g_prefs.getString("broker", "");
  g_secret = g_prefs.getString("secret", "");
  g_forceBle = g_prefs.getBool("forceBle", false);
  g_prefs.end();

  g_stored = ssid.length() > 0;
  if (g_stored) {
    g_ssid = ssid;
    g_pass = pass;
  } else if (secretsConfigured()) {
    g_ssid = WIFI_SSID;
    g_pass = WIFI_PASSWORD;
  } else {
    g_ssid = "";
    g_pass = "";
  }
  g_broker = broker.length() > 0 ? broker : String(MQTT_HOST);
}

const String& wifiSsid() { return g_ssid; }
const String& wifiPass() { return g_pass; }
const String& broker() { return g_broker; }
const String& deviceSecret() { return g_secret; }

bool hasStoredWifi() { return g_stored; }

bool provisioningNeeded() {
#if PROVISIONING_BLE
  if (g_stored) return false;
  return g_forceBle || g_ssid.length() == 0;
#else
  return false;
#endif
}

void saveProvisioned(const String& ssid, const String& pass, const String& broker, const String& secret) {
  g_prefs.begin("cronomed", false);
  g_prefs.putString("ssid", ssid);
  g_prefs.putString("pass", pass);
  g_prefs.putString("broker", broker);
  g_prefs.putString("secret", secret);
  g_prefs.putBool("forceBle", false);
  g_prefs.end();
}

void factoryReset() {
  g_prefs.begin("cronomed", false);
  g_prefs.remove("ssid");
  g_prefs.remove("pass");
  g_prefs.remove("broker");
  g_prefs.remove("secret");
  g_prefs.putBool("forceBle", true);
  g_prefs.end();
}

}  // namespace Settings
