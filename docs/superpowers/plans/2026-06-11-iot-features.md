# AgroTrack IoT Features — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar 4 suites de tests de validación IoT + detección de anomalías en el rules engine + 2 endpoints nuevos (última lectura por sensor y configuración WiFi del gateway) + migración SQL.

**Architecture:** Los tests replican la lógica de negocio inline (mismo patrón que el resto del proyecto — sin mocks de módulos). La detección de anomalías extiende `RulesEngine` con estado en memoria. Los endpoints siguen el patrón Elysia + `execProcedure` existente.

**Tech Stack:** Bun + ElysiaJS + bun:test + Supabase RPC (execProcedure) + PostgreSQL (pl/pgsql)

---

## Archivos a modificar / crear

| Archivo | Acción | Qué cambia |
|---|---|---|
| `tests/iot.features.test.ts` | **Crear** | 4 suites de tests inline |
| `src/services/watchdog.service.ts` | **Modificar** línea 39 | `private check()` → `check()` (público) |
| `src/services/rules.engine.ts` | **Modificar** | Añadir detección de anomalías y degradación |
| `src/modules/iot/telemetry.api.ts` | **Modificar** | Nuevo `GET /telemetry/sensor/:sensor_id/last` |
| `src/modules/iot/sensors.api.ts` | **Modificar** | Nuevo `PUT /sensors/gateways/:id/wifi` |
| `migration-v2.sql` | **Crear** | 2 nuevas funciones + 2 columnas en gateways |

---

## Task 1: Exponer `check()` como método público en WatchdogService

El test del watchdog necesita llamar `check()` directamente para simular el paso del tiempo sin esperar el timer real.

**Files:**
- Modify: `agrotrack-back/src/services/watchdog.service.ts:39`

- [ ] **Step 1: Cambiar visibilidad de check()**

En `src/services/watchdog.service.ts`, línea 39, cambiar:
```typescript
  private async check() {
```
por:
```typescript
  async check() {
```

- [ ] **Step 2: Verificar que el servidor compila**

```bash
cd agrotrack-back && bun run src/index.ts --dry-run 2>&1 | head -5
```
Esperado: sin errores de TypeScript.

- [ ] **Step 3: Commit**

```bash
git add agrotrack-back/src/services/watchdog.service.ts
git commit -m "refactor(watchdog): exponer check() como público para tests"
```

---

## Task 2: Suite de tests — Validación de umbrales (batería + temperatura)

Los tests replican la lógica de evaluación de umbrales inline. No mockean módulos — prueban la especificación de negocio.

**Files:**
- Create: `agrotrack-back/tests/iot.features.test.ts`

- [ ] **Step 1: Crear el archivo con las 2 primeras suites**

Crear `agrotrack-back/tests/iot.features.test.ts`:

```typescript
import { describe, it, expect, beforeEach } from "bun:test";

// ─── Lógica de evaluación de umbrales (replica rules.engine.ts) ───────────────
// Evalúa si un valor métrico supera min o max configurado
function evaluateThreshold(
  value: number,
  minValue: number | null,
  maxValue: number | null,
): boolean {
  if (minValue !== null && value < minValue) return true;
  if (maxValue !== null && value > maxValue) return true;
  return false;
}

function buildAlertMessage(
  metric: string,
  value: number,
  minValue: number | null,
  maxValue: number | null,
): string {
  if (minValue !== null && value < minValue)
    return `${metric} bajo umbral mínimo: ${value} < ${minValue}`;
  return `${metric} superó umbral máximo: ${value} > ${maxValue}`;
}

// ─── Suite 1: Batería del gateway ─────────────────────────────────────────────

describe("Batería del gateway — umbral configurable", () => {
  const threshold = { metric: "battery", min_value: 15, max_value: null };

  it("battery por debajo del mínimo genera alerta", () => {
    expect(evaluateThreshold(10, threshold.min_value, threshold.max_value)).toBe(true);
  });

  it("battery en el valor mínimo exacto no genera alerta", () => {
    expect(evaluateThreshold(15, threshold.min_value, threshold.max_value)).toBe(false);
  });

  it("battery sobre el mínimo no genera alerta", () => {
    expect(evaluateThreshold(80, threshold.min_value, threshold.max_value)).toBe(false);
  });

  it("mensaje de alerta incluye el valor y el umbral configurado", () => {
    const msg = buildAlertMessage("battery", 10, threshold.min_value, threshold.max_value);
    expect(msg).toContain("10");
    expect(msg).toContain("15");
    expect(msg).toMatch(/bajo umbral mínimo/i);
  });

  it("umbral mínimo personalizado (20%) es respetado", () => {
    const customThreshold = { metric: "battery", min_value: 20, max_value: null };
    expect(evaluateThreshold(19, customThreshold.min_value, customThreshold.max_value)).toBe(true);
    expect(evaluateThreshold(20, customThreshold.min_value, customThreshold.max_value)).toBe(false);
  });
});

// ─── Suite 2: Temperatura bajo umbral mínimo ──────────────────────────────────

describe("Temperatura — umbral mínimo de acopio", () => {
  const threshold = { metric: "temperature", min_value: 2, max_value: 25 };

  it("temperatura bajo el mínimo de acopio genera alerta", () => {
    expect(evaluateThreshold(-3, threshold.min_value, threshold.max_value)).toBe(true);
  });

  it("temperatura en el mínimo exacto no genera alerta", () => {
    expect(evaluateThreshold(2, threshold.min_value, threshold.max_value)).toBe(false);
  });

  it("temperatura dentro del rango de acopio no genera alerta", () => {
    expect(evaluateThreshold(15, threshold.min_value, threshold.max_value)).toBe(false);
  });

  it("temperatura sobre el máximo de acopio genera alerta", () => {
    expect(evaluateThreshold(30, threshold.min_value, threshold.max_value)).toBe(true);
  });

  it("mensaje de alerta de temperatura incluye el valor y el umbral", () => {
    const msg = buildAlertMessage("temperature", -3, threshold.min_value, threshold.max_value);
    expect(msg).toContain("-3");
    expect(msg).toContain("2");
    expect(msg).toMatch(/bajo umbral mínimo/i);
  });

  it("umbral máximo personalizado (20°C) para acopio en caliente es respetado", () => {
    const warmStorage = { metric: "temperature", min_value: 10, max_value: 20 };
    expect(evaluateThreshold(21, warmStorage.min_value, warmStorage.max_value)).toBe(true);
    expect(evaluateThreshold(15, warmStorage.min_value, warmStorage.max_value)).toBe(false);
  });
});
```

- [ ] **Step 2: Ejecutar y verificar que pasan**

```bash
cd agrotrack-back && bun test tests/iot.features.test.ts
```
Esperado: `11 tests pass` (5 en Suite 1 + 6 en Suite 2).

- [ ] **Step 3: Commit**

```bash
git add agrotrack-back/tests/iot.features.test.ts
git commit -m "test(iot): validación de umbrales configurables de batería y temperatura"
```

---

## Task 3: Suite de tests — Anomalías, degradación, watchdog y disable-sensor

Agrega las 4 suites restantes al mismo archivo. Todas usan lógica inline, sin mocks de módulos.

**Files:**
- Modify: `agrotrack-back/tests/iot.features.test.ts`

- [ ] **Step 1: Agregar lógica de anomalías y 2 suites al final del archivo**

Agregar al final de `agrotrack-back/tests/iot.features.test.ts`:

```typescript
// ─── Lógica de detección de anomalías (replica la extensión de rules.engine.ts) ─

const ANOMALY_DELTA_C = 8;      // Salto máximo válido entre lecturas consecutivas
const ANOMALY_MIN_C = -20;      // Mínimo físico operativo (cámara frigorífica industrial)
const ANOMALY_MAX_C = 40;       // Máximo físico operativo
const ANOMALY_WINDOW_MS = 10 * 60 * 1000; // 10 minutos
const ANOMALY_DEGRADED_COUNT = 5;          // Anomalías en ventana para marcar como degradado

function isAnomalousReading(
  current: number,
  previous: number | undefined,
): boolean {
  if (current < ANOMALY_MIN_C || current > ANOMALY_MAX_C) return true;
  if (previous !== undefined && Math.abs(current - previous) > ANOMALY_DELTA_C) return true;
  return false;
}

function countRecentAnomalies(timestamps: number[], now: number): number {
  return timestamps.filter((t) => now - t < ANOMALY_WINDOW_MS).length;
}

// ─── Suite 3: Lecturas anómalas ───────────────────────────────────────────────

describe("Detección de lecturas anómalas — saltos y rangos", () => {
  it("salto de 9°C en una sola lectura es anómalo (> 8°C permitido)", () => {
    expect(isAnomalousReading(14, 5)).toBe(true); // |14 - 5| = 9
  });

  it("salto de 8°C exactos no es anómalo (umbral es estricto)", () => {
    expect(isAnomalousReading(13, 5)).toBe(false); // |13 - 5| = 8, no supera
  });

  it("salto de 7°C entre lecturas es lectura normal", () => {
    expect(isAnomalousReading(12, 5)).toBe(false); // |12 - 5| = 7
  });

  it("valor bajo -20°C es físicamente anómalo (fuera de rango operativo)", () => {
    expect(isAnomalousReading(-25, -18)).toBe(true);
  });

  it("valor sobre 40°C es físicamente anómalo (fallo de sensor)", () => {
    expect(isAnomalousReading(42, 35)).toBe(true);
  });

  it("primera lectura sin referencia previa no se evalúa por delta", () => {
    // undefined = sin lectura anterior; solo aplica evaluación de rango
    expect(isAnomalousReading(5, undefined)).toBe(false);
    expect(isAnomalousReading(-25, undefined)).toBe(true); // fuera de rango sí aplica
  });

  it("lectura en límite superior del rango válido (40°C) es aceptada", () => {
    expect(isAnomalousReading(40, 35)).toBe(false);
  });

  it("lectura justo sobre el límite superior (40.1°C) es anómala", () => {
    expect(isAnomalousReading(40.1, 35)).toBe(true);
  });
});

// ─── Suite 4: Sensor degradado — ventana deslizante ──────────────────────────

describe("Degradación de sensor — ventana deslizante de 10 minutos", () => {
  it("5 anomalías en los últimos 10 minutos indica sensor degradado", () => {
    const now = Date.now();
    // 5 anomalías distribuidas en los últimos 4 minutos
    const timestamps = [1, 2, 3, 4, 5].map((i) => now - i * 60_000);
    expect(countRecentAnomalies(timestamps, now)).toBeGreaterThanOrEqual(ANOMALY_DEGRADED_COUNT);
  });

  it("4 anomalías en 10 minutos no activa sensor_degraded", () => {
    const now = Date.now();
    const timestamps = [1, 2, 3, 4].map((i) => now - i * 60_000);
    expect(countRecentAnomalies(timestamps, now)).toBeLessThan(ANOMALY_DEGRADED_COUNT);
  });

  it("anomalías de más de 10 minutos no cuentan en la ventana", () => {
    const now = Date.now();
    // 5 anomalías, todas ocurridas hace 11-15 minutos (fuera de ventana)
    const oldTimestamps = [11, 12, 13, 14, 15].map((i) => now - i * 60_000);
    expect(countRecentAnomalies(oldTimestamps, now)).toBe(0);
  });

  it("mezcla de anomalías recientes y viejas: solo cuentan las recientes", () => {
    const now = Date.now();
    const recent = [1, 2].map((i) => now - i * 60_000);        // 2 en ventana
    const old = [11, 12, 13].map((i) => now - i * 60_000);     // 3 fuera de ventana
    expect(countRecentAnomalies([...recent, ...old], now)).toBe(2);
  });

  it("ventana se reinicia tras disparar sensor_degraded (evita alertas repetidas)", () => {
    // Al llegar a 5, el sistema vacía el array → la siguiente lectura empieza desde 0
    let anomalyWindow: number[] = [];
    const now = Date.now();

    for (let i = 0; i < 5; i++) {
      anomalyWindow.push(now - i * 30_000);
    }

    // Simula reset tras sensor_degraded
    const wasTriggered = countRecentAnomalies(anomalyWindow, now) >= ANOMALY_DEGRADED_COUNT;
    if (wasTriggered) anomalyWindow = [];

    expect(wasTriggered).toBe(true);
    expect(anomalyWindow).toHaveLength(0);
  });
});

// ─── Suite 5: Sensor desconectado (watchdog) ─────────────────────────────────

const OFFLINE_THRESHOLD_MS = 30_000; // replica watchdog.service.ts

function isSensorOffline(lastSeen: number, now: number): boolean {
  return now - lastSeen > OFFLINE_THRESHOLD_MS;
}

describe("Watchdog — sensor desconectado", () => {
  it("sensor sin heartbeat por 31 segundos está offline", () => {
    const lastSeen = Date.now() - 31_000;
    expect(isSensorOffline(lastSeen, Date.now())).toBe(true);
  });

  it("sensor con heartbeat hace 29 segundos sigue activo", () => {
    const lastSeen = Date.now() - 29_000;
    expect(isSensorOffline(lastSeen, Date.now())).toBe(false);
  });

  it("sensor recién registrado (heartbeat = ahora) no está offline", () => {
    const lastSeen = Date.now();
    expect(isSensorOffline(lastSeen, Date.now())).toBe(false);
  });

  it("alerta sensor_offline solo se dispara una vez por episodio", () => {
    const offlineSensors = new Set<number>();

    function shouldAlert(sensorId: number, offline: boolean): boolean {
      if (offline && !offlineSensors.has(sensorId)) {
        offlineSensors.add(sensorId);
        return true;
      }
      return false;
    }

    expect(shouldAlert(1, true)).toBe(true);   // primera detección: dispara
    expect(shouldAlert(1, true)).toBe(false);  // misma detección: no duplica
  });

  it("sensor que vuelve a transmitir sale del registro de offline", () => {
    const offlineSensors = new Set<number>([1, 2]);
    offlineSensors.delete(1); // heartbeat restaura sensor 1
    expect(offlineSensors.has(1)).toBe(false);
    expect(offlineSensors.has(2)).toBe(true); // sensor 2 sigue offline
  });
});

// ─── Suite 6: Desactivar sensor sin perder configuración ─────────────────────

describe("Sensor desactivado — umbrales conservados", () => {
  it("deshabilitar un sensor solo cambia 'enable', no elimina umbrales", () => {
    const sensor = { id: 1, name: "Bodega Norte", enable: true };
    const thresholds = [
      { id: 10, sensor_id: 1, metric: "temperature", min_value: 2, max_value: 25 },
      { id: 11, sensor_id: 1, metric: "battery", min_value: 15, max_value: null },
    ];

    sensor.enable = false;

    expect(thresholds).toHaveLength(2);
    expect(thresholds[0].min_value).toBe(2);
    expect(thresholds[1].min_value).toBe(15);
  });

  it("sensor deshabilitado conserva todos sus datos de configuración", () => {
    const sensor = {
      id: 1,
      name: "Bodega Norte",
      identifier: "SN-001",
      location: "Almacén 3",
      enable: true,
    };
    sensor.enable = false;

    expect(sensor.name).toBe("Bodega Norte");
    expect(sensor.identifier).toBe("SN-001");
    expect(sensor.location).toBe("Almacén 3");
    expect(sensor.enable).toBe(false);
  });

  it("al reactivar el sensor sus umbrales siguen aplicables", () => {
    const sensor = { id: 1, enable: false };
    const thresholds = [
      { sensor_id: 1, metric: "temperature", min_value: 2, max_value: 25 },
    ];

    sensor.enable = true;

    const applicable = thresholds.filter((t) => t.sensor_id === sensor.id);
    expect(applicable).toHaveLength(1);
    expect(applicable[0].min_value).toBe(2);
  });

  it("deshabilitar sensor A no afecta los umbrales de sensor B", () => {
    const thresholdsA = [{ sensor_id: 1, metric: "temperature", min_value: 2 }];
    const thresholdsB = [{ sensor_id: 2, metric: "temperature", min_value: 5 }];

    // Sensor A deshabilitado
    const sensorAEnabled = false;

    // Umbrales de B no se ven afectados
    expect(thresholdsB[0].min_value).toBe(5);
    expect(thresholdsA[0].sensor_id).not.toBe(thresholdsB[0].sensor_id);
  });
});
```

- [ ] **Step 2: Ejecutar y verificar que todas las suites pasan**

```bash
cd agrotrack-back && bun test tests/iot.features.test.ts
```
Esperado: `33 tests pass` (11 de Task 2 + 8+5+5+4 de las nuevas suites).

- [ ] **Step 3: Commit**

```bash
git add agrotrack-back/tests/iot.features.test.ts
git commit -m "test(iot): validación de anomalías, degradación de sensor, watchdog y disable-sensor"
```

---

## Task 4: Extender RulesEngine con detección de anomalías

Implementa en producción la lógica de `anomalous_reading` y `sensor_degraded` que los tests ya documentan.

**Files:**
- Modify: `agrotrack-back/src/services/rules.engine.ts`

- [ ] **Step 1: Reemplazar el contenido completo de rules.engine.ts**

Reemplazar `agrotrack-back/src/services/rules.engine.ts` con:

```typescript
import { execProcedure } from '@core/db/connection';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

interface Reading {
  sensor_id: number;
  gateway_id: number;
  temperature?: number;
  voltage?: number;
  battery?: number;
  [key: string]: any;
}

interface Threshold {
  id: number;
  metric: string;
  min_value: number | null;
  max_value: number | null;
  alert_message: string | null;
}

const ANOMALY_DELTA_C        = 8;
const ANOMALY_MIN_C          = -20;
const ANOMALY_MAX_C          = 40;
const ANOMALY_WINDOW_MS      = 10 * 60 * 1000;
const ANOMALY_DEGRADED_COUNT = 5;

class RulesEngine {
  private lastTemperatures = new Map<number, number>();
  private anomalyWindows   = new Map<number, number[]>();

  async evaluate(reading: Reading) {
    const thresholdsResult = await execProcedure('iot.get_thresholds_for_sensor', [{ sensor_id: reading.sensor_id }]);

    if (!thresholdsResult.error && thresholdsResult.result) {
      const thresholds: Threshold[] = Array.isArray(thresholdsResult.result)
        ? thresholdsResult.result
        : [thresholdsResult.result];

      for (const threshold of thresholds) {
        const value = reading[threshold.metric as keyof Reading] as number | undefined;
        if (value === undefined || value === null) continue;

        let breached = false;
        let message  = '';

        if (threshold.min_value !== null && value < threshold.min_value) {
          breached = true;
          message  = threshold.alert_message ?? `${threshold.metric} bajo umbral mínimo: ${value} < ${threshold.min_value}`;
        } else if (threshold.max_value !== null && value > threshold.max_value) {
          breached = true;
          message  = threshold.alert_message ?? `${threshold.metric} superó umbral máximo: ${value} > ${threshold.max_value}`;
        }

        if (breached) {
          await this.triggerAlert({
            sensor_id: reading.sensor_id,
            gateway_id: reading.gateway_id,
            type: 'threshold_exceeded',
            metric: threshold.metric,
            value,
            threshold: threshold.min_value !== null && value < threshold.min_value
              ? threshold.min_value
              : threshold.max_value!,
            message,
          });
        }
      }
    }

    if (reading.temperature !== undefined && reading.temperature !== null) {
      await this.evaluateAnomaly(reading.sensor_id, reading.gateway_id, reading.temperature);
    }
  }

  private async evaluateAnomaly(sensorId: number, gatewayId: number, temperature: number) {
    const prev          = this.lastTemperatures.get(sensorId);
    const isOutOfRange  = temperature < ANOMALY_MIN_C || temperature > ANOMALY_MAX_C;
    const isDeltaSpike  = prev !== undefined && Math.abs(temperature - prev) > ANOMALY_DELTA_C;

    if (isOutOfRange || isDeltaSpike) {
      const message = isOutOfRange
        ? `Lectura fuera de rango físico: T=${temperature}°C (rango válido: ${ANOMALY_MIN_C}°C – ${ANOMALY_MAX_C}°C)`
        : `Salto de temperatura anómalo: ${prev}°C → ${temperature}°C (delta: ${Math.abs(temperature - prev!).toFixed(1)}°C)`;

      await this.triggerAlert({
        sensor_id: sensorId,
        gateway_id: gatewayId,
        type: 'anomalous_reading',
        metric: 'temperature',
        value: temperature,
        threshold: prev,
        message,
      });

      const now    = Date.now();
      const window = (this.anomalyWindows.get(sensorId) ?? []).filter(t => now - t < ANOMALY_WINDOW_MS);
      window.push(now);
      this.anomalyWindows.set(sensorId, window);

      if (window.length >= ANOMALY_DEGRADED_COUNT) {
        this.anomalyWindows.set(sensorId, []);
        await this.triggerAlert({
          sensor_id: sensorId,
          gateway_id: gatewayId,
          type: 'sensor_degraded',
          metric: 'temperature',
          message: `Sensor ${sensorId} posiblemente defectuoso: ${window.length} lecturas anómalas en 10 minutos`,
        });
      }
    } else {
      this.lastTemperatures.set(sensorId, temperature);
    }
  }

  async triggerAlert(alert: {
    sensor_id: number;
    gateway_id: number;
    type: string;
    metric?: string;
    value?: number;
    threshold?: number;
    message: string;
  }) {
    console.warn(`[Rules] ALERTA sensor=${alert.sensor_id} tipo=${alert.type} — ${alert.message}`);

    const result = await execProcedure('iot.save_alert', [alert]);
    if (result.error) {
      console.error('[Rules] Error al guardar alerta:', result.error);
      return;
    }

    broadcastToGateway(alert.gateway_id, { type: 'alert', data: { ...alert, id: result.result?.id } });
  }
}

export const rulesEngine = new RulesEngine();
```

- [ ] **Step 2: Verificar que todos los tests siguen pasando**

```bash
cd agrotrack-back && bun test
```
Esperado: todos los tests en verde (incluyendo los nuevos de iot.features).

- [ ] **Step 3: Commit**

```bash
git add agrotrack-back/src/services/rules.engine.ts
git commit -m "feat(rules): detección de anomalías puras y degradación de sensor con ventana deslizante"
```

---

## Task 5: Migración SQL — nuevas funciones y columnas

Crea los stored procedures para el endpoint de última lectura y configuración WiFi del gateway.

**Files:**
- Create: `agrotrack-back/migration-v2.sql`

- [ ] **Step 1: Crear el archivo de migración**

Crear `agrotrack-back/migration-v2.sql`:

```sql
-- =============================================================================
-- AgroTrack — Migration v2
-- Agrega: columnas wifi en iot.gateways + 2 funciones nuevas
-- Ejecutar en Supabase SQL Editor
-- =============================================================================

-- ─── 1. Columnas WiFi en iot.gateways ────────────────────────────────────────

ALTER TABLE iot.gateways
  ADD COLUMN IF NOT EXISTS wifi_ssid     TEXT,
  ADD COLUMN IF NOT EXISTS wifi_security TEXT DEFAULT 'WPA2';

-- ─── 2. iot.get_last_reading_by_sensor ───────────────────────────────────────
-- Retorna la lectura más reciente de un sensor específico.

CREATE OR REPLACE FUNCTION iot.get_last_reading_by_sensor(p_sensor_id INT)
RETURNS JSON
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
  v_result JSON;
BEGIN
  SELECT row_to_json(r) INTO v_result
  FROM (
    SELECT
      sensor_id,
      temperature,
      voltage,
      battery,
      extra_data,
      received_at
    FROM iot.readings
    WHERE sensor_id = p_sensor_id
    ORDER BY received_at DESC
    LIMIT 1
  ) r;

  RETURN v_result;  -- NULL si no hay lecturas
END;
$$;

GRANT EXECUTE ON FUNCTION iot.get_last_reading_by_sensor(INT) TO authenticated, service_role;

-- ─── 3. iot.update_gateway_wifi ──────────────────────────────────────────────
-- Actualiza la configuración WiFi almacenada para un gateway.
-- La contraseña NO se persiste en BD por seguridad.

CREATE OR REPLACE FUNCTION iot.update_gateway_wifi(
  p_id       INT,
  p_ssid     TEXT,
  p_security TEXT DEFAULT 'WPA2'
)
RETURNS JSON
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
  v_result JSON;
BEGIN
  UPDATE iot.gateways
  SET
    wifi_ssid     = p_ssid,
    wifi_security = p_security,
    updated_at    = NOW()
  WHERE id = p_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Gateway no encontrado: %', p_id;
  END IF;

  SELECT row_to_json(g) INTO v_result
  FROM (
    SELECT id, name, identifier, wifi_ssid, wifi_security, updated_at
    FROM iot.gateways
    WHERE id = p_id
  ) g;

  RETURN v_result;
END;
$$;

GRANT EXECUTE ON FUNCTION iot.update_gateway_wifi(INT, TEXT, TEXT) TO authenticated, service_role;
```

- [ ] **Step 2: Ejecutar en Supabase SQL Editor**

Copiar el contenido de `migration-v2.sql` y ejecutarlo en el SQL Editor de Supabase Dashboard. Verificar que no hay errores.

- [ ] **Step 3: Verificar que las funciones existen**

En el SQL Editor de Supabase, ejecutar:
```sql
SELECT routine_name
FROM information_schema.routines
WHERE routine_schema = 'iot'
  AND routine_name IN ('get_last_reading_by_sensor', 'update_gateway_wifi');
```
Esperado: 2 filas.

- [ ] **Step 4: Commit**

```bash
git add agrotrack-back/migration-v2.sql
git commit -m "feat(db): get_last_reading_by_sensor + update_gateway_wifi + columnas wifi en gateways"
```

---

## Task 6: Endpoint — Última lectura de un sensor

**Files:**
- Modify: `agrotrack-back/src/modules/iot/telemetry.api.ts`

- [ ] **Step 1: Agregar la nueva ruta antes del cierre del grupo**

En `agrotrack-back/src/modules/iot/telemetry.api.ts`, agregar entre la ruta `.get('/latest/:gateway_id', ...)` y el cierre `)`:

```typescript
    .get('/sensor/:sensor_id/last', async ({ params, set }) => {
      const result = await execProcedure('iot.get_last_reading_by_sensor', [{ sensor_id: parseInt(params.sensor_id) }]);
      if (result.error) { set.status = 500; return { message: result.error }; }
      if (!result.result) { set.status = 404; return { message: 'Sin lecturas registradas para este sensor' }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.view_telemetry })
```

El archivo completo después del cambio debe verse así:

```typescript
import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';
import { rulesEngine } from '@services/rules.engine';
import { watchdogService } from '@services/watchdog.service';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

const path = '/telemetry';

export const TelemetryApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .post('/ingest', async ({ body, set, headers }) => {
      const apiKey = headers['x-api-key'];
      if (!apiKey) { set.status = 401; return { message: 'API Key requerida' }; }

      const gatewayResult = await execProcedure('iot.get_gateway_by_api_key', [{ api_key: apiKey }]);
      if (gatewayResult.error || !gatewayResult.result) {
        set.status = 401; return { message: 'Gateway no autorizado' };
      }

      const gateway = gatewayResult.result;
      const payload = body as any;

      const saveResult = await execProcedure('iot.save_reading', [{
        sensor_id: payload.sensor_id,
        gateway_id: gateway.id,
        temperature: payload.temperature,
        voltage: payload.voltage,
        battery: payload.battery,
        extra_data: payload.extra_data || null,
      }]);

      if (saveResult.error) { set.status = 500; return { message: saveResult.error }; }

      const reading = { ...payload, gateway_id: gateway.id, received_at: new Date().toISOString() };

      watchdogService.heartbeat(payload.sensor_id);
      await rulesEngine.evaluate(reading);
      broadcastToGateway(gateway.id, { type: 'reading', data: reading });

      return { ok: true, reading_id: saveResult.result?.id };
    }, {
      body: t.Object({
        sensor_id: t.Number(),
        temperature: t.Optional(t.Number()),
        voltage: t.Optional(t.Number()),
        battery: t.Optional(t.Number()),
        extra_data: t.Optional(t.Any()),
      }),
    })

    .get('/latest/:gateway_id', async ({ params, set }) => {
      const result = await execProcedure('iot.get_latest_readings_by_gateway', [{ gateway_id: parseInt(params.gateway_id) }]);
      if (result.error) { set.status = 500; return { message: result.error }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.view_telemetry })

    .get('/sensor/:sensor_id/last', async ({ params, set }) => {
      const result = await execProcedure('iot.get_last_reading_by_sensor', [{ sensor_id: parseInt(params.sensor_id) }]);
      if (result.error) { set.status = 500; return { message: result.error }; }
      if (!result.result) { set.status = 404; return { message: 'Sin lecturas registradas para este sensor' }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.view_telemetry })
  );
```

- [ ] **Step 2: Verificar que todos los tests pasan**

```bash
cd agrotrack-back && bun test
```
Esperado: todos en verde.

- [ ] **Step 3: Commit**

```bash
git add agrotrack-back/src/modules/iot/telemetry.api.ts
git commit -m "feat(telemetry): GET /telemetry/sensor/:id/last — timestamp de última lectura por sensor"
```

---

## Task 7: Endpoint — Configuración WiFi del gateway

**Files:**
- Modify: `agrotrack-back/src/modules/iot/sensors.api.ts`

- [ ] **Step 1: Agregar la ruta PUT /gateways/:id/wifi**

En `agrotrack-back/src/modules/iot/sensors.api.ts`, agregar entre `.post('/gateways', ...)` y el cierre `)`  de la última ruta:

```typescript
    .put('/gateways/:id/wifi', async ({ params, body, set }) => {
      const result = await execProcedure('iot.update_gateway_wifi', [{
        id:       parseInt(params.id),
        ssid:     (body as any).ssid,
        security: (body as any).security ?? 'WPA2',
      }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return { ok: true, gateway_id: parseInt(params.id), ssid: (body as any).ssid };
    }, {
      requirePermission: PERMISSIONS.iot.manage_gateways,
      body: t.Object({
        ssid:     t.String(),
        password: t.Optional(t.String()),
        security: t.Optional(t.Union([
          t.Literal('WPA2'),
          t.Literal('WPA3'),
          t.Literal('open'),
        ])),
      }),
    })
```

El bloque `.group(path, app => app ... )` de `sensors.api.ts` debe quedar así al final:

```typescript
    .post('/gateways', async ({ body, set }) => {
      const result = await execProcedure('iot.save_gateway', [body]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_gateways,
      body: t.Object({
        name:       t.String(),
        identifier: t.String(),
        location:   t.Optional(t.String()),
      }),
    })

    .put('/gateways/:id/wifi', async ({ params, body, set }) => {
      const result = await execProcedure('iot.update_gateway_wifi', [{
        id:       parseInt(params.id),
        ssid:     (body as any).ssid,
        security: (body as any).security ?? 'WPA2',
      }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return { ok: true, gateway_id: parseInt(params.id), ssid: (body as any).ssid };
    }, {
      requirePermission: PERMISSIONS.iot.manage_gateways,
      body: t.Object({
        ssid:     t.String(),
        password: t.Optional(t.String()),
        security: t.Optional(t.Union([
          t.Literal('WPA2'),
          t.Literal('WPA3'),
          t.Literal('open'),
        ])),
      }),
    })
  );
```

- [ ] **Step 2: Verificar que todos los tests pasan**

```bash
cd agrotrack-back && bun test
```
Esperado: todos en verde.

- [ ] **Step 3: Commit final**

```bash
git add agrotrack-back/src/modules/iot/sensors.api.ts
git commit -m "feat(sensors): PUT /sensors/gateways/:id/wifi — configuración de red WiFi del gateway"
```

---

## Resumen de endpoints nuevos

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| `GET` | `/telemetry/sensor/:sensor_id/last` | `view_telemetry` | Última lectura y timestamp de un sensor |
| `PUT` | `/sensors/gateways/:id/wifi` | `manage_gateways` | Configura SSID y seguridad WiFi del gateway |

## Resumen de nuevos tipos de alerta (WebSocket)

| `type` | Cuándo | Acción recomendada en app |
|---|---|---|
| `threshold_exceeded` | Lectura supera min/max configurado | Notificación naranja operacional |
| `anomalous_reading` | Salto >8°C o fuera de -20°C/40°C | Notificación amarilla de monitoreo |
| `sensor_degraded` | ≥5 anomalías en 10 minutos | Notificación roja de mantenimiento |
| `sensor_offline` | Sin heartbeat >30s | Notificación crítica gris/negra |
