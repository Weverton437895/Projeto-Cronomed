#!/usr/bin/env bash
# Cria o arquivo de senhas do Mosquitto com o usuario da API.
# Para cada caixa IoT, adicione um usuario com o MAC sem separadores:
#   ./scripts/setup-mosquitto.sh add AABBCCDDEEFF <senha-da-caixa>
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && set -a && . ./.env && set +a
PASSWD=docker/mosquitto/passwd

if [ "${1:-}" = "add" ]; then
  docker run --rm -v "$PWD/docker/mosquitto:/mosquitto/config" eclipse-mosquitto:2 \
    mosquitto_passwd -b /mosquitto/config/passwd "$2" "$3"
  echo "Usuario $2 adicionado. Reinicie o broker: docker compose restart mosquitto"
  exit 0
fi

: "${MQTT_API_PASSWORD:?defina MQTT_API_PASSWORD no .env}"
touch "$PASSWD"
docker run --rm -v "$PWD/docker/mosquitto:/mosquitto/config" eclipse-mosquitto:2 \
  mosquitto_passwd -b -c /mosquitto/config/passwd "${MQTT_API_USER:-cronomed-api}" "$MQTT_API_PASSWORD"
echo "Arquivo $PASSWD criado."
