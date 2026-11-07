# CronoMed - prepara e sobe o back-end (PostgreSQL + Mosquitto + API) para testar com a caixa ESP32.
#
# Uso (PowerShell, dentro da pasta cronomed-backend OU informando o caminho):
#   powershell -ExecutionPolicy Bypass -File .\iniciar-cronomed.ps1
#   powershell -ExecutionPolicy Bypass -File .\iniciar-cronomed.ps1 -Mac AA:BB:CC:DD:EE:FF
#   powershell -ExecutionPolicy Bypass -File .\iniciar-cronomed.ps1 -Backend C:\dev\Projeto-Cronomed\cronomed-backend
#
# O que faz: cria o .env com senhas aleatorias (so se ainda nao existir/estiver em branco), cria o usuario
# MQTT da API e (se informar -Mac) o da caixa, sobe o Docker Compose, espera a API ficar pronta, tenta liberar
# a porta 1883 no firewall e mostra o IP que vai em MQTT_HOST do secrets.h.

param(
    [string]$Backend = "",
    [string]$Mac = "",
    [string]$SenhaMqttCaixa = ""
)

$ErrorActionPreference = "Continue"

function Passo($t) { Write-Host ""; Write-Host "==> $t" -ForegroundColor Cyan }
function Ok($t)    { Write-Host "    OK: $t" -ForegroundColor Green }
function Aviso($t) { Write-Host "    ATENCAO: $t" -ForegroundColor Yellow }
function Falha($t) { Write-Host "    ERRO: $t" -ForegroundColor Red; exit 1 }

function Novo-Segredo([int]$bytes) {
    $b = New-Object byte[] $bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($b)
    return (($b | ForEach-Object { $_.ToString("x2") }) -join "")
}

# ---------- 1. localizar a pasta do back-end
Passo "Localizando a pasta do back-end"
if ($Backend -eq "") {
    if (Test-Path ".\docker-compose.yml") { $Backend = (Get-Location).Path }
    elseif (Test-Path ".\cronomed-backend\docker-compose.yml") { $Backend = (Resolve-Path ".\cronomed-backend").Path }
    else { Falha "Rode dentro da pasta cronomed-backend ou use -Backend <caminho>." }
}
if (-not (Test-Path (Join-Path $Backend "docker-compose.yml"))) { Falha "docker-compose.yml nao encontrado em $Backend" }
Set-Location $Backend
Ok $Backend
if ($Backend -match '[^\x00-\x7F]') { Aviso "O caminho tem acentos. O Docker costuma funcionar, mas se der erro, mova a pasta para algo como C:\dev." }

# ---------- 2. Docker
Passo "Verificando o Docker"
try { docker info *> $null } catch { }
if ($LASTEXITCODE -ne 0) { Falha "Docker nao esta rodando. Abra o Docker Desktop, espere iniciar e rode de novo." }
Ok "Docker ativo"

# ---------- 3. .env
Passo "Preparando o arquivo .env"
if (-not (Test-Path ".env")) {
    if (-not (Test-Path ".env.example")) { Falha ".env.example nao encontrado." }
    Copy-Item ".env.example" ".env"
    Ok ".env criado a partir do .env.example"
}
$linhas = Get-Content ".env"
function Valor-Env($chave) {
    foreach ($l in $script:linhas) { if ($l -match "^\s*$chave\s*=(.*)$") { return $Matches[1].Trim() } }
    return ""
}
function Definir-Env($chave, $valor) {
    $achou = $false
    $nova = @()
    foreach ($l in $script:linhas) {
        if ($l -match "^\s*$chave\s*=") { $nova += "$chave=$valor"; $achou = $true } else { $nova += $l }
    }
    if (-not $achou) { $nova += "$chave=$valor" }
    $script:linhas = $nova
    Set-Content -Path ".env" -Value $script:linhas -Encoding ASCII
}
function Precisa-Gerar($v) { return ($v -eq "" -or $v -like "troque*") }

$volumeBanco = (docker volume ls -q 2>$null) -match "pgdata"
if (Precisa-Gerar (Valor-Env "DB_PASSWORD")) {
    if ($volumeBanco) { Aviso "Ja existe um volume de banco antigo. Se a API nao conectar, rode: docker compose down -v (apaga os dados) e execute este script de novo." }
    Definir-Env "DB_PASSWORD" (Novo-Segredo 16); Ok "DB_PASSWORD gerada"
}
if (Precisa-Gerar (Valor-Env "JWT_SECRET")) { Definir-Env "JWT_SECRET" (Novo-Segredo 48); Ok "JWT_SECRET gerado (96 caracteres)" }
if (Precisa-Gerar (Valor-Env "MQTT_API_PASSWORD")) { Definir-Env "MQTT_API_PASSWORD" (Novo-Segredo 16); Ok "MQTT_API_PASSWORD gerada" }
if (Precisa-Gerar (Valor-Env "MQTT_BOX_PASSWORD")) { Definir-Env "MQTT_BOX_PASSWORD" (Novo-Segredo 16); Ok "MQTT_BOX_PASSWORD gerada (copie para MQTT_PASSWORD no secrets.h)" }
if ((Valor-Env "MQTT_BOX_USER") -eq "") { Definir-Env "MQTT_BOX_USER" "cronomed-box" }
Definir-Env "COOKIE_SECURE" "false"
Definir-Env "SPRING_PROFILES_ACTIVE" "dev"
$apiUser = Valor-Env "MQTT_API_USER"; if ($apiUser -eq "") { $apiUser = "cronomed-api" }
$apiPass = Valor-Env "MQTT_API_PASSWORD"

# ---------- 4. usuarios MQTT
Passo "Criando usuarios do Mosquitto"
$pastaMq = Join-Path $Backend "docker\mosquitto"
if (-not (Test-Path $pastaMq)) { Falha "Pasta docker\mosquitto nao encontrada." }
$passwd = Join-Path $pastaMq "passwd"

$boxUser = Valor-Env "MQTT_BOX_USER"; $boxPass = Valor-Env "MQTT_BOX_PASSWORD"
$macLimpo = ""
if ($Mac -ne "") {
    $macLimpo = ($Mac -replace "[:\-\s]", "").ToUpper()
    if ($macLimpo -notmatch "^[0-9A-F]{12}$") { Falha "MAC invalido: $Mac (esperado 6 pares hexadecimais, ex.: AA:BB:CC:DD:EE:FF)" }
    if ($SenhaMqttCaixa -eq "") { $SenhaMqttCaixa = Novo-Segredo 12 }
}
# O arquivo e gerado dentro do container (em /tmp) e copiado pela saida padrao: evita o erro
# "Unable to open file for writing. File exists" que acontece ao gravar direto numa pasta do OneDrive.
$cmd = "mosquitto_passwd -b -c /tmp/p '$apiUser' '$apiPass' && mosquitto_passwd -b /tmp/p '$boxUser' '$boxPass'"
if ($macLimpo -ne "") { $cmd += " && mosquitto_passwd -b /tmp/p '$macLimpo' '$SenhaMqttCaixa'" }
$cmd += " && cat /tmp/p"
$saida = docker run --rm eclipse-mosquitto:2 sh -c $cmd
if ($LASTEXITCODE -ne 0 -or -not $saida) { Falha "Nao consegui gerar os usuarios MQTT. Veja a mensagem acima." }
$conteudo = (($saida | Where-Object { $_ -match "^[^:\s]+:" }) -join "`n") + "`n"
[System.IO.File]::WriteAllText($passwd, $conteudo, (New-Object System.Text.UTF8Encoding($false)))
Ok "usuarios criados: $apiUser, $boxUser" 
if ($macLimpo -ne "") { Ok "usuario da caixa: $macLimpo" }

# ---------- 5. subir
Passo "Subindo PostgreSQL, Mosquitto e API (a primeira vez demora alguns minutos)"
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Falha "docker compose falhou. Veja a mensagem acima." }
docker compose restart mosquitto | Out-Null

Passo "Esperando a API ficar pronta"
$pronto = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/swagger-ui.html" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $pronto = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}
if (-not $pronto) {
    Aviso "A API nao respondeu em 3 minutos. Veja: docker compose logs api"
} else { Ok "API respondendo em http://localhost:8080/swagger-ui.html" }
docker compose ps

# ---------- 6. firewall
Passo "Liberando a porta 1883 (MQTT) no firewall do Windows"
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($admin) {
    netsh advfirewall firewall delete rule name="CronoMed MQTT 1883" *> $null
    netsh advfirewall firewall add rule name="CronoMed MQTT 1883" dir=in action=allow protocol=TCP localport=1883 | Out-Null
    Ok "regra criada"
} else {
    Aviso "Sem permissao de administrador. Abra o PowerShell como administrador e rode:"
    Write-Host '    netsh advfirewall firewall add rule name="CronoMed MQTT 1883" dir=in action=allow protocol=TCP localport=1883'
}

# ---------- 7. IP
Passo "IP deste computador na rede (use em MQTT_HOST)"
$ips = @()
try {
    $ips = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notmatch "^(127\.|169\.254\.)" -and $_.InterfaceAlias -notmatch "vEthernet|WSL|Loopback|VirtualBox|VMware|Docker" } |
        Select-Object -ExpandProperty IPAddress
} catch { }
if ($ips.Count -eq 0) { Aviso "Nao consegui descobrir. Rode ipconfig e use o IPv4 do Wi-Fi/Ethernet." } else { $ips | ForEach-Object { Write-Host "    $_" -ForegroundColor Green } }

# ---------- resumo
Write-Host ""
Write-Host "================ PRONTO ================" -ForegroundColor Green
Write-Host "Swagger : http://localhost:8080/swagger-ui.html"
Write-Host "Web     : em outro terminal, cronomed-web -> npm install ; npm run dev  (http://localhost:5173)"
Write-Host ""
Write-Host "Caixa (ESP32) - confira no secrets.h do firmware:" -ForegroundColor Cyan
Write-Host "  MQTT_USER     = $boxUser"
Write-Host "  MQTT_PASSWORD = $boxPass"
Write-Host "  MQTT_HOST     = IP deste computador (acima) - so se NAO parear pelo Bluetooth"
Write-Host "Com o pareamento Bluetooth (web: Dispositivos -> Cadastrar caixa) o IP e o Wi-Fi vao pela pagina."
if ($macLimpo -ne "") {
    Write-Host "Usuario individual da caixa tambem criado: $macLimpo"
}
Write-Host ""
