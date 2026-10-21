#include "EventQueue.h"

#include <ArduinoJson.h>
#include <LittleFS.h>
#include <Preferences.h>

#include "config.h"
#include "util.h"

namespace {

struct DeviceEvent {
  char id[37];
  uint8_t comp;
  char doseId[37];
  uint32_t epoch;    // horario UTC do evento; 0 se o relogio ainda nao estava sincronizado
  uint32_t bootId;   // numero da inicializacao em que ocorreu (para usar elapsedMs)
  uint32_t ms;       // millis() no momento do evento
  uint32_t seq;
};

const char* EVENTS_FILE = "/events.json";

DeviceEvent g_events[MAX_EVENTS];
size_t g_count = 0;
uint32_t g_bootId = 0;
uint32_t g_seq = 0;
Preferences g_prefs;

void save() {
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  for (size_t i = 0; i < g_count; i++) {
    JsonObject o = arr.add<JsonObject>();
    o["id"] = g_events[i].id;
    o["c"] = g_events[i].comp;
    o["d"] = g_events[i].doseId;
    o["e"] = g_events[i].epoch;
    o["b"] = g_events[i].bootId;
    o["m"] = g_events[i].ms;
    o["s"] = g_events[i].seq;
  }
  File f = LittleFS.open(EVENTS_FILE, "w");
  if (!f) return;
  serializeJson(doc, f);
  f.close();
}

void load() {
  File f = LittleFS.open(EVENTS_FILE, "r");
  if (!f) return;
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, f);
  f.close();
  if (err || !doc.is<JsonArray>()) return;
  for (JsonObjectConst o : doc.as<JsonArrayConst>()) {
    if (g_count >= MAX_EVENTS) break;
    DeviceEvent& e = g_events[g_count];
    Util::copyStr(e.id, sizeof(e.id), o["id"] | "");
    if (strlen(e.id) != 36) continue;
    e.comp = o["c"] | 0;
    Util::copyStr(e.doseId, sizeof(e.doseId), o["d"] | "");
    e.epoch = o["e"] | 0u;
    e.bootId = o["b"] | 0u;
    e.ms = o["m"] | 0u;
    e.seq = o["s"] | 0u;
    g_count++;
  }
}

}  // namespace

namespace EventQueue {

bool begin() {
  g_prefs.begin("cronomed", false);
  g_bootId = g_prefs.getUInt("boot", 0) + 1;
  g_prefs.putUInt("boot", g_bootId);
  g_seq = g_prefs.getUInt("seq", 0);
  load();
  LOGF("[eventos] boot #%u, %u evento(s) pendente(s)\n", (unsigned)g_bootId, (unsigned)g_count);
  return true;
}

void pushOpening(uint8_t comp, const char* doseId) {
  if (g_count >= MAX_EVENTS) {
    // Fila cheia: descarta o mais antigo para sempre guardar os eventos recentes.
    memmove(&g_events[0], &g_events[1], sizeof(DeviceEvent) * (MAX_EVENTS - 1));
    g_count = MAX_EVENTS - 1;
    LOGF("[eventos] fila cheia, o evento mais antigo foi descartado\n");
  }
  DeviceEvent& e = g_events[g_count];
  Util::makeUuid(e.id);
  e.comp = comp;
  Util::copyStr(e.doseId, sizeof(e.doseId), doseId);
  e.epoch = Util::nowEpoch();
  e.bootId = g_bootId;
  e.ms = millis();
  e.seq = ++g_seq;
  g_prefs.putUInt("seq", g_seq);
  g_count++;
  save();
  LOGF("[eventos] abertura do compartimento %u registrada (dose=%s)\n", comp, e.doseId[0] ? e.doseId : "-");
}

size_t size() { return g_count; }

bool buildPayload(String& out, size_t maxEvents) {
  if (g_count == 0) return false;
  JsonDocument doc;
  JsonArray arr = doc["events"].to<JsonArray>();
  size_t n = g_count < maxEvents ? g_count : maxEvents;
  for (size_t i = 0; i < n; i++) {
    const DeviceEvent& e = g_events[i];
    JsonObject o = arr.add<JsonObject>();
    o["id"] = e.id;
    o["compartment"] = e.comp;
    if (e.doseId[0]) o["doseId"] = e.doseId;
    o["type"] = "BOX_OPENED";
    if (e.epoch != 0) {
      char iso[24];
      Util::isoUtc(e.epoch, iso, sizeof(iso));
      o["occurredAt"] = iso;
    } else if (e.bootId == g_bootId) {
      // Relogio nao sincronizado na hora do evento: informa o tempo decorrido, que o servidor converte
      // para o horario real usando o relogio dele.
      o["elapsedMs"] = (uint32_t)(millis() - e.ms);
    }
    o["seq"] = e.seq;
  }
  out = "";
  serializeJson(doc, out);
  return true;
}

void removeById(const char* id) {
  for (size_t i = 0; i < g_count; i++) {
    if (strcmp(g_events[i].id, id) == 0) {
      memmove(&g_events[i], &g_events[i + 1], sizeof(DeviceEvent) * (g_count - i - 1));
      g_count--;
      save();
      return;
    }
  }
}

void prune(uint32_t nowEpoch) {
  if (nowEpoch == 0) return;
  bool changed = false;
  size_t w = 0;
  for (size_t r = 0; r < g_count; r++) {
    bool expired = g_events[r].epoch != 0 && nowEpoch > g_events[r].epoch &&
                   (nowEpoch - g_events[r].epoch) > EVENT_RETENTION_S;
    if (expired) {
      changed = true;
      continue;
    }
    if (w != r) g_events[w] = g_events[r];
    w++;
  }
  g_count = w;
  if (changed) save();
}

}  // namespace EventQueue
