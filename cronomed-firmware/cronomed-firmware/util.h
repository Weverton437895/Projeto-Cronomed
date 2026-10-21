#pragma once

#include <Arduino.h>
#include <time.h>
#include <esp_system.h>
#if __has_include(<esp_random.h>)
#include <esp_random.h>
#endif
#include "config.h"

namespace Util {

/** UUID v4 aleatorio (hardware RNG do ESP32). out precisa de 37 bytes. */
inline void makeUuid(char out[37]) {
  uint8_t b[16];
  for (int i = 0; i < 16; i += 4) {
    uint32_t r = esp_random();
    memcpy(&b[i], &r, 4);
  }
  b[6] = (b[6] & 0x0F) | 0x40;
  b[8] = (b[8] & 0x3F) | 0x80;
  snprintf(out, 37, "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
           b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]);
}

/** Epoch UTC atual, ou 0 se o relogio ainda nao foi sincronizado. */
inline uint32_t nowEpoch() {
  time_t t = time(nullptr);
  return (t >= (time_t)MIN_VALID_EPOCH) ? (uint32_t)t : 0;
}

/** Formata epoch em ISO-8601 UTC ("2026-11-10T12:00:00Z"). */
inline void isoUtc(uint32_t epoch, char* out, size_t len) {
  time_t t = (time_t)epoch;
  struct tm tmv;
  gmtime_r(&t, &tmv);
  strftime(out, len, "%Y-%m-%dT%H:%M:%SZ", &tmv);
}

/** Copia texto para um buffer fixo, sempre terminando em '\0'. */
inline void copyStr(char* dst, size_t dstLen, const char* src) {
  if (!src) { dst[0] = '\0'; return; }
  strncpy(dst, src, dstLen - 1);
  dst[dstLen - 1] = '\0';
}

}  // namespace Util
