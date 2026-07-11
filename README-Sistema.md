# PhoneCast + RadioCast — Sistema de Espejo de Pantalla

## Cómo funciona

```
Honor X (tu teléfono)           Radio chino (9")
┌────────────────────┐          ┌────────────────────┐
│   PhoneCast.apk    │  WiFi /  │   RadioCast.apk    │
│                    │  USB /   │                    │
│ Captura pantalla   │  Direct  │ Muestra la pantalla│
│ con MediaProjection│─────────▶│ en tiempo real     │
│                    │◀─────────│                    │
│                    │  Toques  │ Toca el radio =    │
│                    │          │ toca el teléfono   │
└────────────────────┘          └────────────────────┘
```

## Compilar ambos proyectos

```bash
# PhoneCast (para el Honor)
cd PhoneCast
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

# RadioCast (para el radio)
cd ../RadioCast
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Instalar

### PhoneCast en tu Honor:
```bash
adb install PhoneCast/app/build/outputs/apk/debug/app-debug.apk
# o pasar por cable USB / WhatsApp
```

### RadioCast en el radio:
- Copiar APK a USB → instalar desde explorador de archivos

## Usar por primera vez

### Configuración inicial (una sola vez):

1. Instala PhoneCast en el teléfono y RadioCast en el radio
2. En el Honor: Ajustes → Batería → PhoneCast → **Sin restricciones**
3. En el Honor: Ajustes → Accesibilidad → PhoneCast → **Activar**
4. Conecta el teléfono al radio por **USB** y activa **USB Tethering** en el Honor
5. Abre **PhoneCast** → acepta el permiso de captura de pantalla → se inicia solo
6. Abre **RadioCast** → detecta el teléfono automáticamente y se conecta solo

**¡Listo! No necesitas configurar IPs ni WiFi. Funciona automáticamente.**

## Cómo se conecta (orden automático)

| Método | Cómo se activa | Velocidad |
|--------|---------------|-----------|
| 1️⃣ **USB Tethering** | Teléfono conectado por USB con tethering ON | ✅ 480 Mbps |
| 2️⃣ **WiFi Hotspot** | Teléfono crea hotspot, radio se conecta | ✅ ~50 Mbps |
| 3️⃣ **NSD/mDNS** | Ambos en el mismo WiFi, detección automática | ✅ ~50 Mbps |
| 4️⃣ **IP Manual** | Ingresas la IP manualmente en el radio | ✅ Cualquiera |

RadioCast busca automáticamente en todos los métodos. **No toques nada.**

## Requisitos

- Puerto 8080 (video) y 8081 (toques) deben estar libres
- USB Tethering: el radio debe reconocer RNDIS (la mayoría sí)

## Permisos necesarios en el Honor

En Honor con Android 13/14:
1. Ajustes → Apps → PhoneCast → Permisos → Activar todos
2. Ajustes → Batería → PhoneCast → **Sin restricciones** (MUY IMPORTANTE)
3. Ajustes → Accesibilidad → PhoneCast → Activar (para toques)
4. La primera vez pedirá permiso de "Grabación de pantalla" — aceptar
5. Para USB Tethering: Ajustes → Red → Zona WiFi/Anclaje → Anclaje USB → ON

## Solución de problemas

| Problema | Solución |
|----------|----------|
| No conecta automático | RadioCast busca cada 3s. Espera o ingresa IP manual. |
| Pantalla lagosa | Normal al principio, mejora en 2-3 segundos |
| Se desconecta solo | Ajustes batería → PhoneCast → Sin restricciones |
| Toques no funcionan | Activar Accesibilidad de PhoneCast en Ajustes |
| Video borroso | Normal, usa 50% resolución para reducir lag |
| USB no detectado | Activar "USB Tethering" en el teléfono |

## Diagnóstico de conexión con RadioInfo

La app **RadioInfo** (en `RadioInfo/`) escanea todo el hardware/software del radio:

- Versión exacta de Android (API level, build, security patch)
- CPU, RAM, almacenamiento
- Estado WiFi (SSID, IP, gateway, señal)
- Sensores, Bluetooth, USB
- Apps instaladas (Android Auto, Maps, etc.)
- Test de conectividad a PhoneCast (ping + puertos 8080/8081)
- Guarda reporte de diagnóstico en Descargas

Para compilar:
```bash
cd RadioInfo
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
