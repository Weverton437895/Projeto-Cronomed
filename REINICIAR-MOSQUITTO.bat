@echo off
title CronoMed - reiniciar Mosquitto
cd /d "%~dp0cronomed-backend"
docker compose restart mosquitto
echo.
echo Mosquitto reiniciado. Aperte o botao EN da placa para ela reconectar.
pause
