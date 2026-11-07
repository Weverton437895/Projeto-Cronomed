@echo off
chcp 65001 >nul
title CronoMed - iniciar tudo
echo ==========================================================
echo  CronoMed: sobe PostgreSQL + Mosquitto + API (Docker) e o site (web)
echo  Antes: abra o Docker Desktop e espere ele ficar "Running".
echo ==========================================================
pushd "%~dp0cronomed-backend"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\iniciar-cronomed.ps1"
popd
echo.
echo ---- Abrindo o site (web) em outra janela ----
if not exist "%~dp0cronomed-web\node_modules" (
  start "CronoMed Web" cmd /k "cd /d %~dp0cronomed-web && npm install && npm run dev"
) else (
  start "CronoMed Web" cmd /k "cd /d %~dp0cronomed-web && npm run dev"
)
timeout /t 8 >nul
start http://localhost:5173
echo.
echo Pronto. Site: http://localhost:5173  ^|  API: http://localhost:8080/swagger-ui.html
echo Agora CONECTE a placa ESP32 no USB e siga o passo a passo.
pause
