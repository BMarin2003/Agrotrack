# Mock Alert Service — Diseño
**Fecha:** 2026-06-18
**HUs cubiertas:** HU-AL6, HU-AL7, HU-AL8
**Autor:** Bryan Marin

---

## Contexto

Firebase/FCM está desactivado (sin `google-services.json`). La lógica de detección en backend (RulesEngine, WatchdogService) y los canales de notificación en Android (`AlertNotificationHelper`) ya están implementados. El único eslabón faltante es quien genera alertas durante el desarrollo.

Este spec describe un servicio de simulación que corre en background, genera alertas orgánicas basadas en variación de temperatura realista, y recorre el mismo path que usará FCM en producción: `AlertDao.insert()` → `getCachedAlerts() Flow` → `AlertsScreen` + `AlertNotificationHelper.showAlert()`.

---

## Arquitectura

```
MockConfig.ENABLED = true
        │
        ▼
AgroTrackApp.onCreate()
   └── startService(MockAlertForegroundService)
              │
              ▼
   MockAlertForegroundService        ← ForegroundService (START_STICKY)
      └── coroutine loop (cada 15–45s random)
              │
              ▼
   MockTemperatureSimulator          ← state machine pura (sin dependencias Android)
      ├── currentTemp + drift + ruido
      ├── silence periods → sensor_offline
      └── spike events   → anomalous_reading
              │
           Alert? (nullable — null si tick no genera evento)
              │
       ┌──────┴──────┐
       ▼             ▼
  AlertDao       AlertNotificationHelper
  .insert()       .showAlert()
       │
       ▼
  getCachedAlerts() Flow  ←── AlertsViewModel ya lo observa
       │
       ▼
  AlertsScreen (UI actualizada en tiempo real)
```

**Principio clave:** el path que recorre cada alerta mock es idéntico al de producción. Al integrar Firebase, solo se agrega `AgroTrackFcmService` que llama `cacheAlert()` + `showAlert()` — todo lo demás queda validado por este mock.

---

## Archivos nuevos

### `data/mock/MockTemperatureSimulator.kt`

State machine pura sin dependencias Android. Fácil de testear unitariamente.

**Estado interno:**
```
currentTemp : Double  = 22.0°C
drift       : Double  = +0.3°C/tick
silentSince : Long?   = null
```

**Reglas por tick:**

```
1. MODO SILENCIO (si silentSince != null)
   ├── tiempo > 30_000ms → generar Alert tipo "sensor_offline"
   ├── 20% chance        → sensor vuelve (silentSince = null)
   └── else              → retornar null (sin alerta)

2. DRIFT + RUIDO
   currentTemp += drift + Random.nextDouble(-0.4, 0.4)
   Si currentTemp < -5°C o > 45°C → invertir drift

3. SPIKE (SPIKE_PROBABILITY = 2%)
   delta = Random.nextDouble(8.0, 12.0) con signo aleatorio
   currentTemp += delta
   → retornar Alert tipo "anomalous_reading"

4. UMBRAL (leer ThresholdConfig de MockData en cada tick)
   temp < config.minThreshold → Alert "threshold_exceeded" "bajo umbral mínimo"
   temp > config.maxThreshold → Alert "threshold_exceeded" "sobre umbral máximo"

5. INICIO DE SILENCIO (SILENCE_PROBABILITY = 5%)
   silentSince = System.currentTimeMillis()
   → retornar null (aún no es offline, falta que pasen 30s)
```

Constantes expuestas como `var` para poder ajustarlas en testing:
```kotlin
var SPIKE_PROBABILITY   = 0.02   // subir a 0.30 para forzar anomalías rápido
var SILENCE_PROBABILITY = 0.05   // subir a 0.50 para forzar offline rápido
```

Los umbrales se leen de `MockData.getThresholdConfig(sensorId = 1)` en cada tick — si el usuario los cambia en `ThresholdsScreen` el simulador los respeta de inmediato.

Firma pública:
```kotlin
fun tick(now: Long = System.currentTimeMillis()): Alert?
```

---

### `core/notification/MockAlertForegroundService.kt`

```kotlin
@AndroidEntryPoint
class MockAlertForegroundService : Service() {

    // Inyectar repository (no DAO directo) — Alert.toEntity() es privado en TelemetryRepositoryImpl
    @Inject lateinit var telemetryRepository: TelemetryRepository
    @Inject lateinit var notificationHelper: AlertNotificationHelper

    private val simulator = MockTemperatureSimulator()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(MOCK_SERVICE_NOTIF_ID, buildServiceNotification())
        scope.launch { runSimulationLoop() }
        return START_STICKY
    }

    private suspend fun runSimulationLoop() {
        while (true) {
            delay(Random.nextLong(15_000L, 45_000L))
            simulator.tick()?.let { alert ->
                telemetryRepository.cacheAlert(alert)   // cacheAlert ya existe en la interfaz
                notificationHelper.showAlert(alert)
                Log.d("MockAlertService", "tick → ${alert.type} temp=${simulator.currentTemp}")
            }
        }
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { scope.cancel() }

    private fun buildServiceNotification(): Notification {
        // Canal: "agrotrack_mock_service", IMPORTANCE_LOW (sin sonido)
        // Título: "Simulación de alertas activa"
        // Icono distinto a las alertas reales para no confundir
    }
}
```

`START_STICKY` hace que el sistema reinicie el servicio si lo mata por memoria — simula el comportamiento de FCM que tampoco depende del ciclo de vida de la Activity.

---

## Archivos modificados

### `MockData.kt`
Agregar lista mutable de alertas activas y helper para insertar:
```kotlin
val mockAlerts: MutableList<Alert> = mutableListOf()
fun addMockAlert(alert: Alert) { mockAlerts.add(0, alert) }
```

### `TelemetryRepositoryImpl.kt`
Agregar rama mock en `getActiveAlerts()`:
```kotlin
override suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>> = runCatching {
    if (MockConfig.ENABLED) return@runCatching MockData.mockAlerts.toList()
    // ... código existente
}
```

### `AgroTrackApp.kt`
```kotlin
override fun onCreate() {
    super.onCreate()
    if (MockConfig.ENABLED) {
        startService(Intent(this, MockAlertForegroundService::class.java))
    }
}
```

### `AndroidManifest.xml`
```xml
<!-- Solo necesario mientras MockConfig.ENABLED = true -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".core.notification.MockAlertForegroundService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

---

## Guía de testing en Android Studio

### Setup
```
1. Verificar MockConfig.ENABLED = true  (ya está en true)
2. Run > Run 'app'  (emulador API 26+ o dispositivo físico)
3. Conceder permiso POST_NOTIFICATIONS si Android 13+ lo solicita
```

### Por HU

**HU-AL6 — Temperatura bajo umbral mínimo:**
```
→ Ir a ThresholdsScreen del sensor "Temp A1" (S-001)
→ Configurar min = 25°C  (mayor que temp inicial 22°C)
→ Esperar 1–3 ticks (15–135s) hasta que drift baje la temp por debajo
→ Verificar: notificación "Temperatura bajo umbral mínimo" en bandeja
→ Verificar: alerta aparece en AlertsScreen
→ Para acelerar: ajustar drift en el simulator a -1.5 via debugger
```

**HU-AL7 — Sensor desconectado:**
```
→ Esperar comportamiento orgánico (~3–5 min con SILENCE_PROBABILITY=5%)
→ Para acelerar en testing: cambiar SILENCE_PROBABILITY = 0.50 en MockTemperatureSimulator
→ Verificar: notificación "Sensor desconectado" tras 30s de silencio
→ Verificar: alerta aparece en AlertsScreen
```

**HU-AL8 — Datos anómalos:**
```
→ Esperar comportamiento orgánico (~2–4 min con SPIKE_PROBABILITY=2%)
→ Para acelerar: cambiar SPIKE_PROBABILITY = 0.30
→ Verificar: notificación "Datos anómalos detectados"
→ Verificar: alerta aparece en AlertsScreen
```

### Logcat
```
Filtrar por tag: MockAlertService
Cada tick loguea: "tick → threshold_exceeded temp=28.3"
                  "tick → null temp=23.1"  (sin evento)
```

### Verificar el path completo (smoke test)
```
1. App corriendo → esperar primera alerta
2. Bajar app a background → esperar segunda alerta en background
3. Notificación aparece en bandeja del sistema ✓
4. Tap en notificación → abrir AlertsScreen ✓  (pendiente: deep link)
5. Botón "Resolver" en AlertsScreen → alerta desaparece ✓
6. Logcat sin excepciones ✓
```

---

## Transición a producción

Cuando `google-services.json` esté disponible:
1. Habilitar plugin Firebase en `build.gradle.kts`
2. Descomentar `AgroTrackFcmService.kt`
3. `AgroTrackFcmService.onMessageReceived()` llama `cacheAlert()` + `showAlert()` — mismo path
4. Cambiar `MockConfig.ENABLED = false`
5. El servicio mock no arranca — sin cambios en el resto del código

---

## Lo que este mock NO cubre

- Entrega de notificaciones con la app completamente desinstalada/forzada a cerrar (eso requiere FCM)
- Múltiples gateways simultáneos (el simulador trabaja con sensor id=1)
- Alertas coordinadas entre sensores (cada sensor tiene su propio simulador independiente)
