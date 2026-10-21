#include "Plan.h"

#include <ArduinoJson.h>
#include <LittleFS.h>

#include "config.h"
#include "util.h"

namespace {

const char* PLAN_FILE = "/plan.json";
const char* PLAN_TMP = "/plan.tmp";
const char* DONE_FILE = "/done.txt";

PlanDose g_doses[MAX_DOSES];
size_t g_count = 0;
uint32_t g_colors[NUM_COMPARTMENTS + 1];   // indice 1..N
uint64_t g_version = 0;
uint32_t g_serverTime = 0;
uint32_t g_tolerance = DEFAULT_TOLERANCE_S;

void loadDoneFlags() {
  File f = LittleFS.open(DONE_FILE, "r");
  if (!f) return;
  while (f.available()) {
    String line = f.readStringUntil('\n');
    line.trim();
    if (line.length() != 36) continue;
    for (size_t i = 0; i < g_count; i++) {
      if (line.equals(g_doses[i].id)) {
        g_doses[i].done = true;
        break;
      }
    }
  }
  f.close();
}

/** Reescreve done.txt so com as doses que ainda existem no plano (evita crescer sem limite). */
void rewriteDoneFile() {
  File f = LittleFS.open(DONE_FILE, "w");
  if (!f) return;
  for (size_t i = 0; i < g_count; i++) {
    if (g_doses[i].done) f.printf("%s\n", g_doses[i].id);
  }
  f.close();
}

bool parse(const uint8_t* payload, size_t len) {
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload, len);
  if (err) {
    LOGF("[plano] JSON invalido: %s\n", err.c_str());
    return false;
  }
  if (!doc["doses"].is<JsonArrayConst>()) {
    LOGF("[plano] sem campo 'doses'\n");
    return false;
  }

  static PlanDose next[MAX_DOSES];   // static: 19 KB nao cabem na pilha da tarefa loop() (8 KB)
  size_t n = 0;
  for (JsonObjectConst d : doc["doses"].as<JsonArrayConst>()) {
    if (n >= MAX_DOSES) {
      LOGF("[plano] doses acima do limite (%d), o restante foi ignorado\n", MAX_DOSES);
      break;
    }
    const char* id = d["id"] | "";
    int c = d["c"] | 0;
    uint32_t t = d["t"] | 0u;
    if (strlen(id) != 36 || c < 1 || c > NUM_COMPARTMENTS || t == 0) continue;
    Util::copyStr(next[n].id, sizeof(next[n].id), id);
    next[n].comp = (uint8_t)c;
    next[n].t = t;
    next[n].done = false;
    n++;
  }

  for (int i = 0; i <= NUM_COMPARTMENTS; i++) g_colors[i] = 0xFFFFFF;
  for (JsonObjectConst c : doc["compartments"].as<JsonArrayConst>()) {
    int idx = c["index"] | 0;
    if (idx >= 1 && idx <= NUM_COMPARTMENTS) g_colors[idx] = c["rgb"] | 0xFFFFFFu;
  }

  memcpy(g_doses, next, sizeof(PlanDose) * n);
  g_count = n;
  g_version = doc["version"] | (uint64_t)0;
  g_serverTime = doc["serverTime"] | 0u;
  g_tolerance = doc["toleranceSeconds"] | (uint32_t)DEFAULT_TOLERANCE_S;
  if (g_tolerance == 0) g_tolerance = DEFAULT_TOLERANCE_S;
  loadDoneFlags();
  return true;
}

}  // namespace

namespace Plan {

bool begin() {
  for (int i = 0; i <= NUM_COMPARTMENTS; i++) g_colors[i] = 0xFFFFFF;
  if (!LittleFS.begin(true)) {   // true = formata se for a primeira vez
    LOGF("[plano] falha ao montar LittleFS\n");
    return false;
  }
  File f = LittleFS.open(PLAN_FILE, "r");
  if (!f) {
    LOGF("[plano] nenhum plano salvo ainda\n");
    return true;
  }
  size_t len = f.size();
  uint8_t* buf = (uint8_t*)malloc(len + 1);
  if (!buf) {
    f.close();
    return false;
  }
  f.read(buf, len);
  f.close();
  bool ok = parse(buf, len);
  free(buf);
  LOGF("[plano] carregado da flash: versao=%llu doses=%u\n", (unsigned long long)g_version, (unsigned)g_count);
  return ok;
}

bool apply(const uint8_t* payload, size_t len, bool persist) {
  uint64_t before = g_version;
  if (!parse(payload, len)) return false;
  if (g_version == before) {
    // Mesmo plano (o servidor reenvia o plano retido a cada reconexao): nao regrava a flash a toa.
    return true;
  }
  rewriteDoneFile();
  if (persist) {
    File f = LittleFS.open(PLAN_TMP, "w");
    if (f) {
      f.write(payload, len);
      f.close();
      LittleFS.remove(PLAN_FILE);
      LittleFS.rename(PLAN_TMP, PLAN_FILE);
    }
  }
  LOGF("[plano] aplicado: versao=%llu doses=%u tolerancia=%us\n", (unsigned long long)g_version,
       (unsigned)g_count, (unsigned)g_tolerance);
  return true;
}

uint64_t version() { return g_version; }
uint32_t serverTime() { return g_serverTime; }
uint32_t toleranceSeconds() { return g_tolerance; }
size_t doseCount() { return g_count; }

uint32_t colorFor(uint8_t comp) {
  if (comp < 1 || comp > NUM_COMPARTMENTS) return 0xFFFFFF;
  return g_colors[comp];
}

PlanDose* findActive(uint8_t comp, uint32_t now) {
  if (now == 0) return nullptr;
  PlanDose* best = nullptr;
  for (size_t i = 0; i < g_count; i++) {
    PlanDose& d = g_doses[i];
    if (d.comp != comp || d.done) continue;
    if (now >= d.t && now < d.t + g_tolerance) {
      if (!best || d.t < best->t) best = &d;
    }
  }
  return best;
}

void markDone(const char* doseId) {
  if (!doseId) return;
  for (size_t i = 0; i < g_count; i++) {
    if (strcmp(g_doses[i].id, doseId) == 0 && !g_doses[i].done) {
      g_doses[i].done = true;
      File f = LittleFS.open(DONE_FILE, "a");
      if (f) {
        f.printf("%s\n", doseId);
        f.close();
      }
      return;
    }
  }
}

}  // namespace Plan
