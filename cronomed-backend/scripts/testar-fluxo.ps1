# CronoMed - teste de ponta a ponta (PowerShell, sem precisar de licenca do IntelliJ)
#
# Uso (com a API rodando em http://localhost:8080):
#   powershell -ExecutionPolicy Bypass -File .\scripts\testar-fluxo.ps1
#
# Cada execucao cria usuario, caixa (MAC) e paciente novos, entao pode rodar varias vezes.

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$senha = "Senha12345"
$script:passou = 0
$script:falhou = 0

function Chamar {
    param([string]$Metodo, [string]$Caminho, $Corpo = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Uri = "$BaseUrl$Caminho"
        Method = $Metodo
        Headers = $headers
        UseBasicParsing = $true
    }
    if ($Corpo -ne $null) {
        $json = $Corpo | ConvertTo-Json -Depth 10
        $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params["ContentType"] = "application/json; charset=utf-8"
    }
    try {
        $resp = Invoke-WebRequest @params
        $status = [int]$resp.StatusCode
        $texto = $resp.Content
    } catch {
        $r = $_.Exception.Response
        if ($r -eq $null) { throw }
        $status = [int]$r.StatusCode
        $texto = ""
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $texto = $_.ErrorDetails.Message
        } else {
            try {
                $leitor = New-Object System.IO.StreamReader($r.GetResponseStream())
                $texto = $leitor.ReadToEnd()
            } catch { $texto = "" }
        }
    }
    $obj = $null
    if ($texto) {
        try { $obj = ConvertFrom-Json -InputObject $texto } catch { $obj = $null }
    }
    return [pscustomobject]@{ Status = $status; Body = $obj; Raw = $texto }
}

function Verificar {
    param([string]$Nome, [bool]$Condicao, [string]$Detalhe = "")
    if ($Condicao) {
        Write-Host "  [OK]    $Nome" -ForegroundColor Green
        $script:passou++
    } else {
        Write-Host "  [FALHOU] $Nome  $Detalhe" -ForegroundColor Red
        $script:falhou++
    }
}

function Passo { param([string]$Titulo) Write-Host ""; Write-Host $Titulo -ForegroundColor Cyan }

$sufixo = [guid]::NewGuid().ToString("N").Substring(0, 8)
$email = "teste$sufixo@exemplo.com"
$mac = ((1..6 | ForEach-Object { "{0:X2}" -f (Get-Random -Minimum 0 -Maximum 256) }) -join ":")

Passo "0. Sem token deve ser recusado"
$r = Chamar GET "/api/v1/users/me"
Verificar "sem token -> 401" ($r.Status -eq 401) "veio $($r.Status)"

Passo "1. Cadastro"
$cadastro = @{ name = "Teste Automatico"; email = $email; password = $senha; profileType = "PACIENTE"; createSelfPatient = $true; acceptTerms = $true }
$r = Chamar POST "/api/v1/auth/register" $cadastro
Verificar "cadastro -> 201" ($r.Status -eq 201) "veio $($r.Status): $($r.Raw)"

Passo "2. E-mail repetido"
$r = Chamar POST "/api/v1/auth/register" $cadastro
Verificar "e-mail repetido -> 409" ($r.Status -eq 409) "veio $($r.Status)"

Passo "3. Login com senha errada"
$r = Chamar POST "/api/v1/auth/login" @{ email = $email; password = "senha-errada-123"; clientLabel = "ps1" }
Verificar "senha errada -> 401" ($r.Status -eq 401) "veio $($r.Status)"

Passo "4. Login correto"
$r = Chamar POST "/api/v1/auth/login" @{ email = $email; password = $senha; clientLabel = "ps1" }
Verificar "login -> 200 com tokens" ($r.Status -eq 200 -and $r.Body.accessToken -and $r.Body.refreshToken) "veio $($r.Status): $($r.Raw)"
if ($r.Status -ne 200) { Write-Host "Sem login nao ha como continuar." -ForegroundColor Red; exit 1 }
$token = $r.Body.accessToken
$refresh = $r.Body.refreshToken

Passo "5. Dados do usuario"
$r = Chamar GET "/api/v1/users/me" $null $token
Verificar "users/me -> 200 com o e-mail certo" ($r.Status -eq 200 -and $r.Body.email -eq $email) "veio $($r.Status)"

Passo "6. Pacientes"
$r = Chamar GET "/api/v1/patients" $null $token
$pacientes = @($r.Body)
Verificar "1 paciente OWNER" ($r.Status -eq 200 -and $pacientes.Count -eq 1 -and $pacientes[0].myAccess -eq "OWNER") "veio $($r.Status): $($r.Raw)"
$patientId = $pacientes[0].id

Passo "7. Registrar a caixa"
$r = Chamar POST "/api/v1/devices" @{ macAddress = $mac; name = "Caixa de teste" } $token
Verificar "caixa -> 201" ($r.Status -eq 201) "veio $($r.Status): $($r.Raw)"
Verificar "6 compartimentos e deviceSecret" (@($r.Body.device.compartments).Count -eq 6 -and [bool]$r.Body.deviceSecret)
$deviceId = $r.Body.device.id
$compartmentId = (@($r.Body.device.compartments) | Where-Object { $_.index -eq 1 } | Select-Object -First 1).id

Passo "8. MAC repetido"
$r = Chamar POST "/api/v1/devices" @{ macAddress = $mac; name = "Duplicada" } $token
Verificar "MAC repetido -> 409" ($r.Status -eq 409) "veio $($r.Status)"

Passo "9. Associar compartimento 1 ao paciente"
$r = Chamar PUT "/api/v1/devices/$deviceId/compartments/$compartmentId" @{ patientId = $patientId } $token
Verificar "compartimento associado -> 200" ($r.Status -eq 200 -and $r.Body.patientId -eq $patientId) "veio $($r.Status): $($r.Raw)"

Passo "10. Criar medicamento (estoque 30, 2 horarios por dia)"
$med = @{
    name = "Losartana"; measure = "comprimido"; dosage = "50 mg"; doseQuantity = 1
    stockAcquired = 30; minDaysAlert = 3; compartmentId = $compartmentId
    schedules = @(
        @{ time = "08:00"; daysOfWeek = @() },
        @{ time = "20:00"; daysOfWeek = @() }
    )
}
$r = Chamar POST "/api/v1/patients/$patientId/medications" $med $token
Verificar "medicamento -> 201" ($r.Status -eq 201) "veio $($r.Status): $($r.Raw)"
Verificar "estoque 30" ($r.Body.stockCurrent -eq 30) "veio $($r.Body.stockCurrent)"
Verificar "15 dias restantes" ($r.Body.daysRemaining -eq 15) "veio $($r.Body.daysRemaining)"
$medId = $r.Body.id

Passo "11. Doses geradas (proximas 48h)"
$r = Chamar GET "/api/v1/patients/$patientId/doses/upcoming?hours=48" $null $token
$doses = @($r.Body)
Verificar "ao menos 2 doses pendentes" ($r.Status -eq 200 -and $doses.Count -ge 2) "veio $($r.Status) com $($doses.Count)"
$doseId = $doses[0].id
$eventId = [guid]::NewGuid().ToString()

Passo "12. Confirmar a dose"
$r = Chamar POST "/api/v1/doses/$doseId/events" @{ eventId = $eventId; type = "CONFIRMED_YES" } $token
Verificar "confirmacao aplicada (APPLIED)" ($r.Status -eq 200 -and $r.Body.result -eq "APPLIED") "veio $($r.Status): $($r.Raw)"

Passo "13. Reenviar o MESMO evento (idempotencia)"
$r = Chamar POST "/api/v1/doses/$doseId/events" @{ eventId = $eventId; type = "CONFIRMED_YES" } $token
Verificar "evento repetido -> DUPLICATE" ($r.Status -eq 200 -and $r.Body.result -eq "DUPLICATE") "veio $($r.Status): $($r.Raw)"

Passo "14. Estoque deve ter baixado so uma vez (30 -> 29)"
$r = Chamar GET "/api/v1/medications/$medId" $null $token
Verificar "estoque 29" ($r.Status -eq 200 -and $r.Body.stockCurrent -eq 29) "veio $($r.Body.stockCurrent)"

Passo "15. Reabastecer 10 unidades"
$r = Chamar POST "/api/v1/medications/$medId/refill" @{ quantity = 10 } $token
Verificar "estoque 39" ($r.Status -eq 200 -and $r.Body.stockCurrent -eq 39) "veio $($r.Status): $($r.Raw)"

Passo "16. Painel do paciente"
$r = Chamar GET "/api/v1/patients/$patientId/dashboard" $null $token
Verificar "dashboard -> 200" ($r.Status -eq 200) "veio $($r.Status): $($r.Raw)"

Passo "17. Plano da caixa"
$r = Chamar GET "/api/v1/devices/$deviceId/plan" $null $token
Verificar "plano -> 200 com doses" ($r.Status -eq 200 -and @($r.Body.doses).Count -ge 1) "veio $($r.Status): $($r.Raw)"

Passo "18. Renovar token"
$r = Chamar POST "/api/v1/auth/refresh" @{ refreshToken = $refresh }
Verificar "refresh -> 200 com novo token" ($r.Status -eq 200 -and [bool]$r.Body.accessToken) "veio $($r.Status): $($r.Raw)"

Passo "19. Refresh antigo nao pode ser reutilizado"
$r = Chamar POST "/api/v1/auth/refresh" @{ refreshToken = $refresh }
Verificar "refresh reaproveitado -> 401" ($r.Status -eq 401) "veio $($r.Status)"

Write-Host ""
$cor = if ($script:falhou -eq 0) { "Green" } else { "Yellow" }
Write-Host ("Resultado: {0} verificacoes OK, {1} falharam." -f $script:passou, $script:falhou) -ForegroundColor $cor
if ($script:falhou -gt 0) { exit 1 }
