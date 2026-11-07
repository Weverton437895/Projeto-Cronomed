@echo off
title CronoMed - atualizar IP
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ATUALIZAR-IP.ps1"
echo.
pause
