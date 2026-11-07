// Pareamento por Bluetooth (BLE) com a pagina "Cadastrar caixa" da web (Web Bluetooth).
// A caixa anuncia "CronoMed-XXXX"; a web le o MAC, cadastra a caixa na API e envia Wi-Fi, IP do broker e segredo.
// Tambem trata o botao de reset de fabrica (GPIO 4, segurar 3 s).
#pragma once

#include <Arduino.h>

namespace Provisioning {

/** Sobe o servidor BLE e comeca a anunciar. Chame so quando Settings::provisioningNeeded(). */
void begin();

/** Chame no loop() enquanto estiver pareando. Reinicia a caixa quando o pareamento termina. */
void loop();

/** Prepara o pino do botao de reset. */
void beginButton();

/** Chame no loop(): se o botao ficar pressionado por RESET_BUTTON_HOLD_MS apaga o Wi-Fi e reinicia. */
void checkResetButton();

}  // namespace Provisioning
