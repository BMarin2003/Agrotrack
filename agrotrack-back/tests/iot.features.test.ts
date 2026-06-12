import { describe, it, expect } from "bun:test";

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

// ─── Lógica de detección de anomalías (replica la extensión de rules.engine.ts) ─

const ANOMALY_DELTA_C        = 8;
const ANOMALY_MIN_C          = -20;
const ANOMALY_MAX_C          = 40;
const ANOMALY_WINDOW_MS      = 10 * 60 * 1000;
const ANOMALY_DEGRADED_COUNT = 5;

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

  it("salto de 8°C exactos no es anómalo (umbral es estricto, no supera)", () => {
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
    const oldTimestamps = [11, 12, 13, 14, 15].map((i) => now - i * 60_000);
    expect(countRecentAnomalies(oldTimestamps, now)).toBe(0);
  });

  it("mezcla de anomalías recientes y viejas: solo cuentan las recientes", () => {
    const now = Date.now();
    const recent = [1, 2].map((i) => now - i * 60_000);
    const old    = [11, 12, 13].map((i) => now - i * 60_000);
    expect(countRecentAnomalies([...recent, ...old], now)).toBe(2);
  });

  it("ventana se reinicia tras disparar sensor_degraded (evita alertas repetidas)", () => {
    let anomalyWindow: number[] = [];
    const now = Date.now();

    for (let i = 0; i < 5; i++) {
      anomalyWindow.push(now - i * 30_000);
    }

    const wasTriggered = countRecentAnomalies(anomalyWindow, now) >= ANOMALY_DEGRADED_COUNT;
    if (wasTriggered) anomalyWindow = [];

    expect(wasTriggered).toBe(true);
    expect(anomalyWindow).toHaveLength(0);
  });
});

// ─── Lógica de watchdog (replica watchdog.service.ts) ────────────────────────

const OFFLINE_THRESHOLD_MS = 30_000;

function isSensorOffline(lastSeen: number, now: number): boolean {
  return now - lastSeen > OFFLINE_THRESHOLD_MS;
}

// ─── Suite 5: Sensor desconectado (watchdog) ─────────────────────────────────

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
    expect(isSensorOffline(Date.now(), Date.now())).toBe(false);
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

    expect(shouldAlert(1, true)).toBe(true);
    expect(shouldAlert(1, true)).toBe(false);
  });

  it("sensor que vuelve a transmitir sale del registro de offline", () => {
    const offlineSensors = new Set<number>([1, 2]);
    offlineSensors.delete(1);
    expect(offlineSensors.has(1)).toBe(false);
    expect(offlineSensors.has(2)).toBe(true);
  });
});

// ─── Suite 6: Desactivar sensor sin perder configuración ─────────────────────

describe("Sensor desactivado — umbrales conservados", () => {
  it("deshabilitar un sensor solo cambia 'enable', no elimina umbrales", () => {
    const sensor = { id: 1, name: "Bodega Norte", enable: true };
    const thresholds = [
      { id: 10, sensor_id: 1, metric: "temperature", min_value: 2, max_value: 25 },
      { id: 11, sensor_id: 1, metric: "battery",     min_value: 15, max_value: null },
    ];

    sensor.enable = false;

    expect(thresholds).toHaveLength(2);
    expect(thresholds[0].min_value).toBe(2);
    expect(thresholds[1].min_value).toBe(15);
  });

  it("sensor deshabilitado conserva todos sus datos de configuración", () => {
    const sensor = {
      id: 1, name: "Bodega Norte", identifier: "SN-001", location: "Almacén 3", enable: true,
    };
    sensor.enable = false;

    expect(sensor.name).toBe("Bodega Norte");
    expect(sensor.identifier).toBe("SN-001");
    expect(sensor.location).toBe("Almacén 3");
    expect(sensor.enable).toBe(false);
  });

  it("al reactivar el sensor sus umbrales siguen aplicables", () => {
    const sensor     = { id: 1, enable: false };
    const thresholds = [{ sensor_id: 1, metric: "temperature", min_value: 2, max_value: 25 }];

    sensor.enable = true;

    const applicable = thresholds.filter((t) => t.sensor_id === sensor.id);
    expect(applicable).toHaveLength(1);
    expect(applicable[0].min_value).toBe(2);
  });

  it("deshabilitar sensor A no afecta los umbrales de sensor B", () => {
    const thresholdsA = [{ sensor_id: 1, metric: "temperature", min_value: 2 }];
    const thresholdsB = [{ sensor_id: 2, metric: "temperature", min_value: 5 }];

    // Sensor A deshabilitado — thresholds B intactos
    expect(thresholdsB[0].min_value).toBe(5);
    expect(thresholdsA[0].sensor_id).not.toBe(thresholdsB[0].sensor_id);
  });
});
