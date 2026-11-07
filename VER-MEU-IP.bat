@echo off
chcp 65001 >nul
echo ==========================================
echo  IP deste computador (use o do Wi-Fi atual)
echo ==========================================
ipconfig | findstr /i "IPv4 adaptador Wireless Wi-Fi"
echo.
pause
