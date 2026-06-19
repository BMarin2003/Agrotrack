# Mock Alert Service — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar un ForegroundService que genera alertas orgánicas (threshold_exceeded, sensor_offline, anomalous_reading) sin Firebase para validar HU-AL6, HU-AL7 y HU-AL8 en Android Studio.

**Architecture:** `MockTemperatureSimulator` (Kotlin puro, sin dependencias Android) mantiene una state machine de temperatura con drift, ruido, spikes y períodos de silencio. `MockAlertForegroundService` corre un loop de coroutines, llama al simulador cada 15–45s y persiste las alertas via `TelemetryRepository.cacheAlert()` + `AlertNotificationHelper.showAlert()` — el mismo path que usará FCM en producción.

**Tech Stack:** Kotlin, Hilt (`@AndroidEntryPoint`), Coroutines (`SupervisorJob + Dispatchers.IO`), Android ForegroundService (`START_STICKY`), NotificationCompat, JUnit4.

## Global Constraints

- `MockConfig.ENABLED = true` en `data/mock/MockConfig.kt` — no modificar este valor en este plan
- El servicio solo arranca cuando `MockConfig.ENABLED = true`; cambiar a `false` para producción sin tocar nada más
- El simulador trabaja con `sensorId = 1`, `gatewayId = 1` (sensor "Temp A1")
- Package base: `com.corall.agrotrack`
- `minSdk = 28`, `targetSdk = 35`, `compileSdk = 36`
- JUnit4 para tests unitarios (`testImplementation(libs.junit)` ya en build.gradle.kts)
- No agregar Firebase ni dependencias externas nuevas

---

## Mapa de archivos

| Acción | Archivo |
|--------|---------|
| **Crear** | `app/src/main/java/com/corall/agrotrack/data/mock/MockTemperatureSimulator.kt` |
| **Crear** | `app/src/main/java/com/corall/agrotrack/core/notification/MockAlertForegroundService.kt` |
| **Crear** | `app/src/test/java/com/corall/agrotrack/data/mock/MockTemperatureSimulatorTest.kt` |
| **Modificar** | `app/src/main/java/com/corall/agrotrack/data/mock/MockData.kt` |
| **Modificar** | `app/src/main/java/com/corall/agrotrack/data/repository/TelemetryRepositoryImpl.kt` |
| **Modificar** | `app/src/main/java/com/corall/agrotrack/AgroTrackApp.kt` |
| **Modificar** | `app/src/main/AndroidManifest.xml` |

---

### Task 1: MockTemperatureSimulator

**Files:**
- Create: `app/src/main/java/com/corall/agrotrack/data/mock/MockTemperatureSimulator.kt`
- Create: `app/src/test/java/com/corall/agrotrack/data/mock/MockTemperatureSimulatorTest.kt`

**Interfaces:**
- Consumes: `MockData.getThresholdConfig(sensorId: Int): ThresholdConfig`, `Alert` domain model
- Produces:
  - `MockTemperatureSimulator.tick(now: Long): Alert?` — devuelve una alerta o null
  - `MockTemperatureSimulator.currentTemp: Double` — solo lectura, para logging
  - `MockTemperatureSimulator.SPIKE_PROBABILITY: Double` — var, para forzar en tests
  - `MockTemperatureSimulator.SILENCE_PROBABILITY: Double` — var, para forzar en tests

---

- [ ] **Paso 1 — Escribir los tests que fallan**

Crear el archivo `app/src/test/java/com/corall/agrotrack/data/mock/MockTemperatureSimulatorTest.kt`:

```kotlin
package com.corall.agrotrack.data.mock

import com.corall.agrotrack.domain.model.ThresholdConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockTemperatureSimulatorTest {

    private lateinit var sim: MockTemperatureSimulator

    @Before
    fun setUp() {
        sim = MockTemperatureSimulator()
        // Estado conocido: umbrales que no rompe la temp inicial (22.0°C)
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = 10.0, maxThreshold = 30.0, alertsEnabled = true)
        )
    }

    @Test
    fun `tick devuelve null cuando no hay evento`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        // temp inicial ~22.0, umbrales 10–30 → ningún evento
        val result = sim.tick(now = 1_000_000L)
        assertNull(result)
    }

    @Test
    fun `tick devuelve anomalous_reading cuando spike forzado`() {
        sim.SPIKE_PROBABILITY   = 1.0
        sim.SILENCE_PROBABILITY = 0.0
        val alert = sim.tick(now = 1_000_000L)
        assertNotNull(alert)
        assertEquals("anomalous_reading", alert!!.type)
        assertEquals("temperature", alert.metric)
        assertEquals(1, alert.sensorId)
        assertEquals(1, alert.gatewayId)
        assertFalse(alert.resolved)
    }

    @Test
    fun `tick devuelve threshold_exceeded bajo umbral minimo`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        // minThreshold muy alto → temp inicial siempre por debajo
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = 100.0, maxThreshold = 200.0, alertsEnabled = true)
        )
        val alert = sim.tick(now = 1_000_000L)
        assertNotNull(alert)
        assertEquals("threshold_exceeded", alert!!.type)
        assertTrue(alert.message.contains("mínimo", ignoreCase = true))
    }

    @Test
    fun `tick devuelve threshold_exceeded sobre umbral maximo`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        // maxThreshold muy bajo → temp inicial siempre por encima
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = -100.0, maxThreshold = -50.0, alertsEnabled = true)
        )
        val alert = sim.tick(now = 1_000_000L)
        assertNotNull(alert)
        assertEquals("threshold_exceeded", alert!!.type)
        assertTrue(alert.message.contains("máximo", ignoreCase = true))
    }

    @Test
    fun `tick no genera threshold cuando alertsEnabled es false`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = 100.0, maxThreshold = 200.0, alertsEnabled = false)
        )
        val alert = sim.tick(now = 1_000_000L)
        assertNull(alert)
    }

    @Test
    fun `tick devuelve null en modo silencio con menos de 30s`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 1.0
        val t0 = 1_000_000L
        sim.tick(now = t0)                    // inicia silencio
        val result = sim.tick(now = t0 + 10_000L)  // solo han pasado 10s
        assertNull(result)
    }

    @Test
    fun `tick devuelve sensor_offline en modo silencio con mas de 30s`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 1.0
        val t0 = 1_000_000L
        sim.tick(now = t0)                     // inicia silencio
        val alert = sim.tick(now = t0 + 31_000L)  // 31s después
        assertNotNull(alert)
        assertEquals("sensor_offline", alert!!.type)
        assertEquals(1, alert.sensorId)
        assertFalse(alert.resolved)
    }

    @Test
    fun `currentTemp es legible desde fuera`() {
        val tempAntes = sim.currentTemp
        sim.tick(now = 1_000_000L)
        // currentTemp puede haber cambiado por drift — solo verificamos que es Double
        assertTrue(sim.currentTemp.isFinite())
        // y que efectivamente cambió (drift + noise siempre != 0)
        // (este assertion es informativo, no crítico)
        assertNotEquals(tempAntes, sim.currentTemp)
    }
}
```

- [ ] **Paso 2 — Ejecutar los tests para verificar que fallan**

En Android Studio: clic derecho sobre `MockTemperatureSimulatorTest` → **Run 'MockTemperatureSimulatorTest'**

O desde terminal (en `agrotrack-app/`):
```
./gradlew :app:test --tests "com.corall.agrotrack.data.mock.MockTemperatureSimulatorTest"
```
Resultado esperado: **ERROR — class not found** (la clase aún no existe)

- [ ] **Paso 3 — Implementar MockTemperatureSimulator**

Crear `app/src/main/java/com/corall/agrotrack/data/mock/MockTemperatureSimulator.kt`:

```kotlin
package com.corall.agrotrack.data.mock

import com.corall.agrotrack.domain.model.Alert
import kotlin.random.Random

class MockTemperatureSimulator {

    var SPIKE_PROBABILITY   = 0.02
    var SILENCE_PROBABILITY = 0.05

    private val sensorId  = 1
    private val gatewayId = 1

    var currentTemp: Double = 22.0
        private set

    private var drift       = 0.3
    private var silentSince: Long? = null
    private var alertIdSeq  = 10_000L

    fun tick(now: Long = System.currentTimeMillis()): Alert? {
        // 1. MODO SILENCIO
        silentSince?.let { since ->
            return if (now - since > 30_000L) {
                if (Random.nextDouble() < 0.20) silentSince = null
                buildAlert(
                    type      = "sensor_offline",
                    metric    = null,
                    value     = null,
                    threshold = null,
                    message   = "Sensor $sensorId offline",
                )
            } else {
                null
            }
        }

        // 2. DRIFT + RUIDO
        currentTemp += drift + Random.nextDouble(-0.4, 0.4)
        if (currentTemp < -5.0 || currentTemp > 45.0) drift = -drift

        // 3. SPIKE
        if (Random.nextDouble() < SPIKE_PROBABILITY) {
            val sign  = if (Random.nextBoolean()) 1.0 else -1.0
            val delta = Random.nextDouble(8.0, 12.0) * sign
            currentTemp += delta
            return buildAlert(
                type      = "anomalous_reading",
                metric    = "temperature",
                value     = currentTemp,
                threshold = null,
                message   = "Lectura anómala: ${"%.1f".format(currentTemp)}°C",
            )
        }

        // 4. UMBRAL
        val config = MockData.getThresholdConfig(sensorId)
        if (config.alertsEnabled) {
            config.minThreshold?.let { min ->
                if (currentTemp < min) {
                    return buildAlert(
                        type      = "threshold_exceeded",
                        metric    = "temperature",
                        value     = currentTemp,
                        threshold = min,
                        message   = "temperature bajo umbral mínimo: ${"%.1f".format(currentTemp)} < $min",
                    )
                }
            }
            config.maxThreshold?.let { max ->
                if (currentTemp > max) {
                    return buildAlert(
                        type      = "threshold_exceeded",
                        metric    = "temperature",
                        value     = currentTemp,
                        threshold = max,
                        message   = "temperature sobre umbral máximo: ${"%.1f".format(currentTemp)} > $max",
                    )
                }
            }
        }

        // 5. INICIO DE SILENCIO
        if (Random.nextDouble() < SILENCE_PROBABILITY) {
            silentSince = now
        }

        return null
    }

    private fun buildAlert(
        type: String,
        metric: String?,
        value: Double?,
        threshold: Double?,
        message: String,
    ): Alert = Alert(
        id        = alertIdSeq++,
        sensorId  = sensorId,
        gatewayId = gatewayId,
        type      = type,
        metric    = metric,
        value     = value,
        threshold = threshold,
        message   = message,
        resolved  = false,
        createdAt = System.currentTimeMillis(),
    )
}
```

- [ ] **Paso 4 — Ejecutar los tests para verificar que pasan**

```
./gradlew :app:test --tests "com.corall.agrotrack.data.mock.MockTemperatureSimulatorTest"
```
Resultado esperado: **BUILD SUCCESSFUL** — 8 tests passed

- [ ] **Paso 5 — Commit**

```bash
git add app/src/main/java/com/corall/agrotrack/data/mock/MockTemperatureSimulator.kt \
        app/src/test/java/com/corall/agrotrack/data/mock/MockTemperatureSimulatorTest.kt
git commit -m "feat: MockTemperatureSimulator — state machine orgánica para alertas mock"
```

---

### Task 2: MockData + TelemetryRepositoryImpl mock path

**Files:**
- Modify: `app/src/main/java/com/corall/agrotrack/data/mock/MockData.kt`
- Modify: `app/src/main/java/com/corall/agrotrack/data/repository/TelemetryRepositoryImpl.kt:66-80`

**Interfaces:**
- Consumes: `Alert` domain model
- Produces:
  - `MockData.mockAlerts: MutableList<Alert>` — lista viva que observa `getCachedAlerts()` via Room
  - `MockData.addMockAlert(alert: Alert)` — prepend a la lista (más reciente primero)
  - `TelemetryRepositoryImpl.getActiveAlerts()` devuelve `MockData.mockAlerts.toList()` en mock mode

---

- [ ] **Paso 1 — Agregar mockAlerts a MockData.kt**

Abrir `app/src/main/java/com/corall/agrotrack/data/mock/MockData.kt`.

Agregar al final del objeto (antes del cierre `}`), después de `saveThresholdConfig`:

```kotlin
    val mockAlerts: MutableList<Alert> = mutableListOf()

    fun addMockAlert(alert: Alert) {
        mockAlerts.add(0, alert)
    }
```

Agregar el import al inicio del archivo:
```kotlin
import com.corall.agrotrack.domain.model.Alert
```

El archivo completo quedará:
```kotlin
package com.corall.agrotrack.data.mock

import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.model.ThresholdConfig

object MockData {

    val gateways: List<Gateway> = listOf(
        Gateway(
            id            = 1,
            name          = "Gateway Principal",
            identifier    = "GW-TWARM-001",
            location      = "Almacén Central",
            enable        = true,
            sensorCount   = 6,
            status        = GatewayStatus.Online,
            lastReadingAt = System.currentTimeMillis() - 15_000,
            battery       = 73.0,
        )
    )

    val sensors: List<Sensor> = listOf(
        Sensor(id = 1, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp A1",    identifier = "S-001", type = "temperature", unit = "°C", location = "Zona A – Sector 1", enable = true),
        Sensor(id = 2, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp A2",    identifier = "S-002", type = "temperature", unit = "°C", location = "Zona A – Sector 2", enable = true),
        Sensor(id = 3, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp B1",    identifier = "S-003", type = "temperature", unit = "°C", location = "Zona B – Sector 1", enable = true),
        Sensor(id = 4, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp B2",    identifier = "S-004", type = "temperature", unit = "°C", location = "Zona B – Sector 2", enable = true),
        Sensor(id = 5, gatewayId = 1, gatewayName = "Gateway Principal", name = "Humedad C1", identifier = "S-005", type = "humidity",    unit = "%",  location = "Zona C – Sector 1", enable = true),
        Sensor(id = 6, gatewayId = 1, gatewayName = "Gateway Principal", name = "Voltaje",    identifier = "S-006", type = "voltage",     unit = "V",  location = "Panel Principal",   enable = true),
    )

    fun latestReadings(gatewayId: Int): List<SensorReading> {
        if (gatewayId != 1) return emptyList()
        val now = System.currentTimeMillis()
        return listOf(
            SensorReading(
                id          = 1L,
                sensorId    = 1,
                gatewayId   = 1,
                sensorName  = "Temp A1",
                unit        = "°C",
                temperature = 24.5,
                voltage     = 3.82,
                battery     = 85.0,
                receivedAt  = now - 15_000,
                status      = SensorStatus.Normal,
            )
        )
    }

    fun lastReadingForSensor(sensorId: Int): SensorReading? {
        return latestReadings(gatewayId = 1).firstOrNull { it.sensorId == sensorId }
    }

    private val thresholdStore: MutableMap<Int, ThresholdConfig> = mutableMapOf(
        1 to ThresholdConfig(sensorId = 1, minThreshold = 10.0, maxThreshold = 30.0, alertsEnabled = true),
        2 to ThresholdConfig(sensorId = 2, minThreshold = 10.0, maxThreshold = 30.0, alertsEnabled = true),
        3 to ThresholdConfig(sensorId = 3, minThreshold =  8.0, maxThreshold = 28.0, alertsEnabled = true),
        4 to ThresholdConfig(sensorId = 4, minThreshold =  8.0, maxThreshold = 28.0, alertsEnabled = false),
    )

    fun getThresholdConfig(sensorId: Int): ThresholdConfig =
        thresholdStore.getOrDefault(sensorId, ThresholdConfig(sensorId, null, null, false))

    fun saveThresholdConfig(config: ThresholdConfig) {
        thresholdStore[config.sensorId] = config
    }

    val mockAlerts: MutableList<Alert> = mutableListOf()

    fun addMockAlert(alert: Alert) {
        mockAlerts.add(0, alert)
    }
}
```

- [ ] **Paso 2 — Agregar mock path en getActiveAlerts**

Abrir `app/src/main/java/com/corall/agrotrack/data/repository/TelemetryRepositoryImpl.kt`.

Localizar el método `getActiveAlerts` (línea ~66). Reemplazar:

```kotlin
    override suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>> = runCatching {
        val response = api.getActiveAlerts(gatewayId)

        if (!response.isSuccessful) {
            error("No se pudieron cargar las alertas")
        }

        val alerts = response.body().orEmpty().map { it.toDomain() }

        if (alerts.isNotEmpty()) {
            alertDao.insertAll(alerts.map { it.toEntity() })
        }

        alerts
    }
```

Por:

```kotlin
    override suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.mockAlerts.toList()

        val response = api.getActiveAlerts(gatewayId)

        if (!response.isSuccessful) {
            error("No se pudieron cargar las alertas")
        }

        val alerts = response.body().orEmpty().map { it.toDomain() }

        if (alerts.isNotEmpty()) {
            alertDao.insertAll(alerts.map { it.toEntity() })
        }

        alerts
    }
```

- [ ] **Paso 3 — Verificar que el proyecto compila**

En Android Studio: **Build → Make Project** (Ctrl+F9)
Resultado esperado: **BUILD SUCCESSFUL** sin errores de compilación.

- [ ] **Paso 4 — Commit**

```bash
git add app/src/main/java/com/corall/agrotrack/data/mock/MockData.kt \
        app/src/main/java/com/corall/agrotrack/data/repository/TelemetryRepositoryImpl.kt
git commit -m "feat: mockAlerts en MockData y rama mock en getActiveAlerts"
```

---

### Task 3: MockAlertForegroundService + Manifest + AgroTrackApp

**Files:**
- Create: `app/src/main/java/com/corall/agrotrack/core/notification/MockAlertForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/corall/agrotrack/AgroTrackApp.kt`

**Interfaces:**
- Consumes: `MockTemperatureSimulator.tick()`, `TelemetryRepository.cacheAlert(alert)`, `AlertNotificationHelper.showAlert(alert)`, `MockData.addMockAlert(alert)`
- Produces: servicio Android arrancado desde `AgroTrackApp.onCreate()` cuando `MockConfig.ENABLED = true`

---

- [ ] **Paso 1 — Crear MockAlertForegroundService.kt**

Crear `app/src/main/java/com/corall/agrotrack/core/notification/MockAlertForegroundService.kt`:

```kotlin
package com.corall.agrotrack.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.corall.agrotrack.data.mock.MockData
import com.corall.agrotrack.data.mock.MockTemperatureSimulator
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MockAlertForegroundService : Service() {

    @Inject lateinit var telemetryRepository: TelemetryRepository
    @Inject lateinit var notificationHelper: AlertNotificationHelper

    private val simulator = MockTemperatureSimulator()
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createMockServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(MOCK_SERVICE_NOTIF_ID, buildServiceNotification())
        scope.launch { runSimulationLoop() }
        return START_STICKY
    }

    private suspend fun runSimulationLoop() {
        while (true) {
            delay(Random.nextLong(15_000L, 45_000L))
            val alert = simulator.tick()
            if (alert != null) {
                MockData.addMockAlert(alert)
                telemetryRepository.cacheAlert(alert)
                notificationHelper.showAlert(alert)
                Log.d(TAG, "tick → ${alert.type} | temp=${"%.1f".format(simulator.currentTemp)}°C")
            } else {
                Log.d(TAG, "tick → null | temp=${"%.1f".format(simulator.currentTemp)}°C")
            }
        }
    }

    private fun createMockServiceChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MOCK_SERVICE,
                "Servicio de simulación",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notificación persistente del simulador (solo desarrollo)"
            }
        )
    }

    private fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_MOCK_SERVICE)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Simulación de alertas activa")
            .setContentText("Generando alertas mock para testing — solo en desarrollo")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG                  = "MockAlertService"
        private const val CHANNEL_MOCK_SERVICE = "agrotrack_mock_service"
        private const val MOCK_SERVICE_NOTIF_ID = 9999
    }
}
```

- [ ] **Paso 2 — Agregar permisos y declarar el servicio en AndroidManifest.xml**

Abrir `app/src/main/AndroidManifest.xml`.

Agregar los dos permisos después de `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`:

```xml
    <!-- Mock ForegroundService — remover cuando MockConfig.ENABLED = false -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Agregar la declaración del servicio dentro de `<application>`, después del comentario del FCM service:

```xml
        <!-- Mock alert simulator — solo desarrollo, remover al integrar FCM -->
        <service
            android:name=".core.notification.MockAlertForegroundService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />
```

El bloque `<application>` queda así:

```xml
    <application
        android:name=".AgroTrackApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AgroTrack"
        android:networkSecurityConfig="@xml/network_security_config"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.AgroTrack">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- FCM: descomentar tras agregar google-services.json -->
        <!--
        <service
            android:name=".service.AgroTrackFcmService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        -->

        <!-- Mock alert simulator — solo desarrollo, remover al integrar FCM -->
        <service
            android:name=".core.notification.MockAlertForegroundService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

    </application>
```

- [ ] **Paso 3 — Arrancar el servicio en AgroTrackApp.kt**

Reemplazar el contenido de `app/src/main/java/com/corall/agrotrack/AgroTrackApp.kt`:

```kotlin
package com.corall.agrotrack

import android.app.Application
import android.content.Intent
import com.corall.agrotrack.core.notification.AlertNotificationHelper
import com.corall.agrotrack.core.notification.MockAlertForegroundService
import com.corall.agrotrack.data.mock.MockConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AgroTrackApp : Application() {

    @Inject lateinit var notificationHelper: AlertNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        if (MockConfig.ENABLED) {
            startService(Intent(this, MockAlertForegroundService::class.java))
        }
    }
}
```

- [ ] **Paso 4 — Compilar**

En Android Studio: **Build → Make Project** (Ctrl+F9)
Resultado esperado: **BUILD SUCCESSFUL**

Si aparece error `Hilt cannot process ... MockAlertForegroundService` → verificar que el archivo tiene la anotación `@AndroidEntryPoint` y que está en el package `com.corall.agrotrack.core.notification`.

- [ ] **Paso 5 — Probar en emulador o dispositivo (smoke test)**

1. **Run → Run 'app'** (Shift+F10)
2. Si Android 13+: aceptar el permiso de notificaciones cuando la app lo solicite
3. Esperar 15–45 segundos
4. Verificar en **Logcat** (filtrar por `MockAlertService`):
   ```
   D/MockAlertService: tick → null | temp=22.3°C
   D/MockAlertService: tick → null | temp=22.6°C
   D/MockAlertService: tick → threshold_exceeded | temp=28.9°C
   ```
5. Verificar que aparece notificación en la bandeja del sistema con el canal correcto

**Para forzar alertas rápido sin esperar:**
En el simulador, cambiar temporalmente (solo para el smoke test, revertir después):
```kotlin
var SILENCE_PROBABILITY = 0.50  // en lugar de 0.05
var SPIKE_PROBABILITY   = 0.30  // en lugar de 0.02
```

O desde Android Studio → **Debugger**: poner un breakpoint en `runSimulationLoop`, evaluar `simulator.SPIKE_PROBABILITY = 0.99` en la consola.

- [ ] **Paso 6 — Verificar las 3 HUs**

**HU-AL6 (umbral mínimo):**
```
1. Ir a ThresholdsScreen del sensor "Temp A1"
2. Configurar minThreshold = 25°C (mayor que temp inicial 22°C)
3. Guardar
4. Esperar 1–3 ticks → notificación "Temperatura bajo umbral mínimo"
5. Ir a AlertsScreen → la alerta aparece en la lista
```

**HU-AL7 (sensor offline):**
```
1. Dejar app corriendo en background
2. Con SILENCE_PROBABILITY = 0.05 esperar ~3–5 min
3. Notificación "Sensor desconectado" en bandeja del sistema aunque la app esté en background
4. Ir a AlertsScreen → la alerta aparece
```

**HU-AL8 (dato anómalo):**
```
1. Con SPIKE_PROBABILITY = 0.02 esperar ~2–4 min
2. Notificación "Datos anómalos detectados"
3. Ir a AlertsScreen → la alerta aparece
```

- [ ] **Paso 7 — Commit final**

```bash
git add app/src/main/java/com/corall/agrotrack/core/notification/MockAlertForegroundService.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/corall/agrotrack/AgroTrackApp.kt
git commit -m "feat(HU-AL6,AL7,AL8): MockAlertForegroundService — simulación orgánica de alertas en background"
```

---

## Resumen de tests

| Test | Tipo | Cómo correr |
|------|------|-------------|
| `MockTemperatureSimulatorTest` (8 casos) | JUnit4 unit test | `./gradlew :app:test` |
| Smoke test: logcat + notificaciones | Manual | Run app en Android Studio |
| HU-AL6: umbral mínimo | Manual | ThresholdsScreen → configurar min=25°C |
| HU-AL7: sensor offline | Manual | Esperar / forzar `SILENCE_PROBABILITY=0.5` |
| HU-AL8: dato anómalo | Manual | Esperar / forzar `SPIKE_PROBABILITY=0.3` |

---

## Transición a producción (referencia)

Cuando esté disponible `google-services.json`:
1. Habilitar plugin Firebase en `app/build.gradle.kts`
2. Descomentar `AgroTrackFcmService` en Manifest
3. `AgroTrackFcmService.onMessageReceived()` llama `cacheAlert()` + `showAlert()` — mismo path que este mock
4. Cambiar `MockConfig.ENABLED = false` → el servicio no arranca, sin otros cambios
