# AgroTrack IoT Features — Design Spec
**Fecha:** 2026-06-11  
**Proyecto:** AgroTrack Backend (ElysiaJS + Bun + Supabase)  
**Contexto:** App móvil nativa Kotlin para operadores y técnicos en fases de acopio agrónomo

---

## Problema pendiente: Conexión MQTT

El broker `mqtt.coralldar.com` rechaza la conexión del backend con `rc=5` (Not Authorized). El broker usa ACL que vincula las credenciales al `clientId` del dispositivo físico. El backend no puede suscribirse con esas mismas credenciales. **Bloqueado** hasta que el administrador del broker (Corall D&R o Twarm) provea una cuenta suscriptora separada o configure el ACL.

**Mientras tanto:** se continúa con el mock script (`NODE_ENV` distinto a `production`).

---

## Alcance

7 tareas agrupadas en:
- 4 tests de validación (archivo nuevo `tests/iot.features.test.ts`)
- 3 endpoints nuevos o extensiones de servicios existentes

---

## Sistema de Alertas — 4 Niveles

| `type` | Cuándo se dispara | Severidad |
|---|---|---|
| `threshold_exceeded` | Lectura supera min/max configurado en `iot.thresholds` | Operacional |
| `anomalous_reading` | Salto físicamente imposible (ΔT > 8°C entre lecturas a 5s) o fuera de rango físico (-20°C / 40°C) | Inusual |
| `sensor_degraded` | ≥ 5 lecturas `anomalous_reading` en ventana deslizante de 10 minutos | Mantenimiento |
| `sensor_offline` | Sin heartbeat del sensor por > 30 segundos | Crítico |

Todos los tipos se persisten en `iot.save_alert` y se transmiten por WebSocket via `broadcastToGateway`. El app Kotlin recibe los mensajes WS y muestra la alerta en pantalla.

---

## Tarea 1 — Validación: Batería bajo umbral

**Archivo:** `tests/iot.features.test.ts`  
**Tipo:** Test unitario puro (sin HTTP, sin Supabase)

**Comportamiento verificado:**
- `rulesEngine.evaluate()` recibe `{ sensor_id: 1, gateway_id: 1, battery: 10 }`
- Existe un threshold configurado: `{ metric: 'battery', min_value: 15, max_value: null }`
- `execProcedure('iot.save_alert')` se llama con `type: 'threshold_exceeded'`, `metric: 'battery'`, `value: 10`, `threshold: 15`
- `broadcastToGateway` se llama con `type: 'alert'`

**Mocks requeridos:** `execProcedure` (devuelve thresholds y confirma save), `broadcastToGateway`

---

## Tarea 2 — Validación: Temperatura bajo umbral mínimo

**Archivo:** `tests/iot.features.test.ts`  
**Tipo:** Test unitario puro

**Comportamiento verificado:**
- `rulesEngine.evaluate()` recibe `{ sensor_id: 1, gateway_id: 1, temperature: -3 }`
- Threshold: `{ metric: 'temperature', min_value: 2, max_value: null }`
- `execProcedure('iot.save_alert')` llamado con `type: 'threshold_exceeded'`, `metric: 'temperature'`
- No se dispara alerta cuando `temperature: 5` (dentro del umbral)

---

## Tarea 3 — Validación: Notificación cuando sensor se desconecta

**Archivo:** `tests/iot.features.test.ts`  
**Tipo:** Test unitario con control de tiempo simulado

**Comportamiento verificado:**
- `watchdogService.heartbeat(1)` registra el sensor
- Se fuerza que `Date.now()` retorne un valor 31 segundos después
- Se llama `check()` directamente (método expuesto para tests)
- `execProcedure('iot.save_alert')` llamado con `type: 'sensor_offline'`, `sensor_id: 1`
- Segunda llamada a `check()` sin nuevo heartbeat no dispara alerta duplicada

**Cambio necesario en `watchdog.service.ts`:** exponer `check()` como método público para tests.

---

## Tarea 4 — Validación: Desactivar alertas sin perder configuración

**Archivo:** `tests/iot.features.test.ts`  
**Tipo:** Test de integración HTTP contra servidor Elysia en memoria

**Comportamiento verificado:**
1. `PUT /sensors/1` con `{ enable: false }` → retorna 200
2. `GET /thresholds?sensor_id=1` → sigue devolviendo los umbrales configurados
3. `GET /sensors/1` → `enable: false` en el resultado

**Assertion clave:** deshabilitar un sensor no elimina sus thresholds.

---

## Tarea 5 — Interfaz: Timestamp de última lectura de un sensor

**Endpoint:** `GET /telemetry/sensor/:sensor_id/last`  
**Archivo:** `src/modules/iot/telemetry.api.ts` (extensión)  
**Permiso:** `PERMISSIONS.iot.view_telemetry`

**Respuesta:**
```json
{
  "sensor_id": 1,
  "temperature": 3.14,
  "voltage": 3.82,
  "battery": 68.5,
  "received_at": "2026-06-11T20:00:00.000Z"
}
```

**Implementación:** nuevo stored procedure `iot.get_last_reading_by_sensor(sensor_id)` que retorna la lectura más reciente de `iot.readings` para ese sensor.

---

## Tarea 6 — Interfaz: Detección y notificación de datos anómalos

**Archivo:** `src/services/rules.engine.ts` (extensión)

### Detección de `anomalous_reading`

Criterios (cualquiera activa la alerta):
- `|T_actual - T_anterior| > 8°C` entre lecturas consecutivas del mismo sensor
- `T < -20°C` o `T > 40°C` (fuera de rango físico operativo)

**Estado necesario:** `Map<sensor_id, number>` con la última temperatura válida por sensor (cache en memoria).

### Detección de `sensor_degraded`

- `Map<sensor_id, number[]>` con timestamps de anomalías recientes
- Si en los últimos 10 minutos hay ≥ 5 entradas → dispara `sensor_degraded`
- Al disparar, se vacía el array para evitar alertas repetidas del mismo periodo

### Flujo en `evaluate()`

```
1. Evaluar thresholds → threshold_exceeded (existente)
2. Evaluar anomalía pura → anomalous_reading (nuevo)
3. Si anomalous_reading → actualizar ventana deslizante
4. Si ventana >= 5 en 10min → sensor_degraded
5. Actualizar cache de última lectura válida
```

**Broadcast WebSocket:** ambos tipos nuevos usan `broadcastToGateway` con `type: 'alert'` igual que los existentes.

---

## Tarea 7 — Interfaz: Configurar red WiFi del gateway

**Endpoint:** `PUT /gateways/:id/wifi`  
**Archivo:** `src/modules/iot/sensors.api.ts` (extensión)  
**Permiso:** `PERMISSIONS.iot.manage_gateways`

**Body:**
```json
{
  "ssid": "RedCampo-2G",
  "password": "...",
  "security": "WPA2"
}
```

**Respuesta:** `{ ok: true, gateway_id: N, ssid: "RedCampo-2G" }`

**Implementación:** stored procedure `iot.update_gateway_wifi(id, ssid, security)` que guarda en la tabla `iot.gateways`. La contraseña NO se persiste en BD (se envía solo al gateway cuando haya integración MQTT real). Por ahora el endpoint valida y confirma la configuración.

**Nota:** cuando se integre MQTT real, este endpoint publicará al topic `twarm/{gateway_uid}/config/wifi`.

---

## Archivos afectados

| Archivo | Cambio |
|---|---|
| `tests/iot.features.test.ts` | Nuevo — 4 suites de tests |
| `src/services/rules.engine.ts` | Extensión — anomaly detection + sliding window |
| `src/services/watchdog.service.ts` | Cambio menor — exponer `check()` como público |
| `src/modules/iot/telemetry.api.ts` | Extensión — `GET /telemetry/sensor/:id/last` |
| `src/modules/iot/sensors.api.ts` | Extensión — `PUT /gateways/:id/wifi` |

**Stored procedures nuevos requeridos (Supabase):**
- `iot.get_last_reading_by_sensor(sensor_id int)` → SELECT más reciente de `iot.readings` por sensor
- `iot.update_gateway_wifi(id int, ssid text, security text)` → UPDATE de `iot.gateways`

Ambos deben agregarse a `migration.sql` (o en un archivo `migration-v2.sql` separado si se prefiere no tocar el original).

---

## Lo que NO está en este scope

- Integración FCM / push notifications del SO
- Autenticación de WiFi en el gateway (requiere MQTT real)
- Anomaly detection para métricas distintas a temperatura (voltage, battery)
- Frontend / app Kotlin
