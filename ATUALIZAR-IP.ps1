# Descobre o IP deste computador na rede Wi-Fi atual e grava em MQTT_HOST do secrets.h do firmware.
$ErrorActionPreference = "Continue"
$ips = @()
try {
    $ips = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notmatch "^(127\.|169\.254\.)" -and $_.InterfaceAlias -notmatch "vEthernet|WSL|Loopback|VirtualBox|VMware|Docker|Bluetooth" } |
        Select-Object -ExpandProperty IPAddress
} catch { }
if ($ips.Count -eq 0) { Write-Host "Nao consegui descobrir o IP. Rode ipconfig." -ForegroundColor Red; exit 1 }
$ip = $ips | Select-Object -First 1
Write-Host "IP deste computador: $ip" -ForegroundColor Green
$ips | ForEach-Object { Write-Host "  encontrado: $_" }
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$secrets = Join-Path $aqui "cronomed-firmware\cronomed-firmware\secrets.h"
Set-Content -Path (Join-Path $aqui "meu-ip.txt") -Value ($ips -join "`n") -Encoding ASCII
if (Test-Path $secrets) {
    $txt = [System.IO.File]::ReadAllText($secrets)
    $novo = [regex]::Replace($txt, '(?m)^#define MQTT_HOST ".*"', "#define MQTT_HOST `"$ip`"")
    [System.IO.File]::WriteAllText($secrets, $novo, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "secrets.h atualizado: MQTT_HOST = $ip" -ForegroundColor Green
} else { Write-Host "secrets.h nao encontrado em $secrets" -ForegroundColor Yellow }
