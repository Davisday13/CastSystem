param(
    [switch]$RadioInfo,
    [string]$PhoneIP = ""
)

$ErrorActionPreference = "SilentlyContinue"
$script:report = @()

function log($m) { $script:report += $m; Write-Host $m }

# ─── 1. Detectar ADB ───────────────────────────────────────
log "╔══════════════════════════════════════════════╗"
log "║  CastSystem - Diagnóstico de Conexión       ║"
log "╚══════════════════════════════════════════════╝"
log ""

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    $sdk = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdk) { $adb = $sdk }
}
if (-not $adb) {
    log "✗ ADB no encontrado. Instala Android SDK platform-tools."
    log "  Descarga: https://developer.android.com/studio/releases/platform-tools"
    exit 1
}
log "✓ ADB encontrado: $adb"

# ─── 2. Dispositivos conectados ────────────────────────────
log "`n── Dispositivos ADB ──────────────────────────"
$devices = & $adb devices
log $devices

$lines = $devices -split "`n" | Where-Object { $_ -match "device$" -and $_ -notmatch "List of devices" }
$serialNumbers = @()
foreach ($line in $lines) {
    $parts = $line -split "`t"
    $serialNumbers += $parts[0].Trim()
}

if ($serialNumbers.Count -eq 0) {
    log "✗ No hay dispositivos conectados por ADB."
    log "  Conecta el radio por USB y activa Depuración USB."
    if ($RadioInfo) { exit 1 }
}

# ─── 3. Si hay radio conectado por ADB ─────────────────────
if ($serialNumbers.Count -gt 0) {
    $radioSerial = $serialNumbers[0]
    log "✓ Radio detectado: $radioSerial"

    # Obtener info del sistema
    log "`n── SISTEMA (ADB) ────────────────────────────"
    $props = @(
        "ro.product.manufacturer", "ro.product.model", "ro.product.device",
        "ro.build.version.release", "ro.build.version.sdk",
        "ro.build.version.security_patch", "ro.build.display.id",
        "ro.product.cpu.abi", "ro.serialno"
    )
    foreach ($p in $props) {
        $val = & $adb -s $radioSerial shell getprop $p 2>$null
        if ($val) { log "$p = $val".Trim() }
    }

    # RAM y almacenamiento
    log "`n── MEMORIA ─────────────────────────────────"
    $mem = & $adb -s $radioSerial shell cat /proc/meminfo 2>$null
    $mem | Select-String "MemTotal" | ForEach-Object { log "  $_".Trim() }

    $storage = & $adb -s $radioSerial shell df -h /data 2>$null
    if ($storage) { log "  $storage".Trim() }

    # WiFi
    log "`n── WiFi ────────────────────────────────────"
    $wifi = & $adb -s $radioSerial shell dumpsys wifi 2>$null
    $ssid = $wifi | Select-String "mWifiInfo" -Context 0,3
    if ($ssid) { log "  $ssid".Trim() }
    $ip = $wifi | Select-String "ipAddress"
    if ($ip) { log "  $ip".Trim() }

    # Todas las IPs
    log "`n── INTERFACES DE RED ───────────────────────"
    $ifconfig = & $adb -s $radioSerial shell ifconfig 2>$null
    if (-not $ifconfig) { $ifconfig = & $adb -s $radioSerial shell ip addr 2>$null }
    log $ifconfig

    # Gateway
    $gw = & $adb -s $radioSerial shell ip route 2>$null
    if ($gw) { log "`n── RUTAS ─────────────────────────────"; log "  $gw".Trim() }

    # Apps instaladas relevantes
    log "`n── APPS INSTALADAS ─────────────────────────"
    $apps = @(
        "com.google.android.projection.gearhead",
        "com.google.android.gms",
        "com.android.chrome",
        "com.google.android.apps.maps",
        "com.google.android.youtube"
    )
    foreach ($a in $apps) {
        $ver = & $adb -s $radioSerial shell dumpsys package $a 2>$null | Select-String "versionName"
        if ($ver) {
            log "  ✓ $a - $ver".Trim()
        } else {
            log "  ✗ $a - No instalado"
        }
    }
}

# ─── 4. Test de conectividad al PhoneCast ──────────────────
log "`n── TEST DE CONEXIÓN AL PHONECAST ────────────"

# Si no se especificó IP, buscar gateway
if (-not $PhoneIP) {
    if ($serialNumbers.Count -gt 0) {
        $route = & $adb -s $radioSerial shell ip route 2>$null
        $match = $route | Select-String "default via"
        if ($match) {
            $PhoneIP = ($match -split " ")[2]
        }
    }
}

$targets = @()
if ($PhoneIP) {
    $targets += $PhoneIP
}
$targets += "192.168.43.1", "192.168.43.129", "192.168.42.1", "192.168.42.129", "192.168.0.1", "192.168.1.1"

$found = $false
foreach ($ip in $targets) {
    if ($ip -eq "" -or $ip -eq "0.0.0.0") { continue }
    $unique = @{}
    $unique[$ip] = $true
}

$unique.Keys | ForEach-Object {
    if ($found) { return }
    $ip = $_
    log "  Probando: $ip ..."
    if ($serialNumbers.Count -gt 0) {
        $ping = & $adb -s $radioSerial shell ping -c 1 -W 2 $ip 2>$null
    } else {
        $ping = ping -n 1 -w 2000 $ip 2>$null
    }
    if ($LASTEXITCODE -eq 0 -or $ping -match "1 packets received") {
        log "    ✓ Ping OK a $ip"
        # Test puerto 8080
        if ($serialNumbers.Count -gt 0) {
            $port8080 = & $adb -s $radioSerial shell timeout 2 nc -z $ip 8080 2>$null
            if ($LASTEXITCODE -eq 0) {
                log "    ✓ Puerto 8080 (video) ABIERTO"
                $found = $true
            } else {
                log "    ✗ Puerto 8080 (video) CERRADO/Timeout"
            }
            $port8081 = & $adb -s $radioSerial shell timeout 2 nc -z $ip 8081 2>$null
            if ($LASTEXITCODE -eq 0) {
                log "    ✓ Puerto 8081 (touch) ABIERTO"
                $found = $true
            } else {
                log "    ✗ Puerto 8081 (touch) CERRADO/Timeout"
            }
        }
        if ($found) {
            log "`n  >>> PhoneCast parece activo en: $ip <<<"
        }
    } else {
        log "    ✗ Sin respuesta de $ip"
    }
}

if (-not $found) {
    log "`n  ⚠ No se detectó PhoneCast en ninguna IP."
    log "  Asegúrate de:"
    log "  1. PhoneCast esté abierto y transmitiendo en el teléfono"
    log "  2. Ambos estén en la misma red (WiFi, USB Tethering o Hotspot)"
    log "  3. No haya firewall bloqueando puertos 8080/8081"
}

# ─── 5. Guardar reporte ────────────────────────────────────
$reportPath = "$PSScriptRoot\diagnostico_radio.txt"
$script:report -join "`n" | Out-File -FilePath $reportPath -Encoding utf8
log "`n── Reporte guardado en: $reportPath ─────────"

if ($RadioInfo -and $serialNumbers.Count -gt 0) {
    $apk = "$PSScriptRoot\RadioInfo\app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apk) {
        log "`n── Instalando RadioInfo en el radio..."
        & $adb -s $radioSerial install -r $apk 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            log "✓ RadioInfo instalado. Ábrelo en el radio para ver más detalles."
        } else {
            log "✗ Error instalando RadioInfo"
        }
    }
}
