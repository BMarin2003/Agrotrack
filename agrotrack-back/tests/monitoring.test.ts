import { describe, it, expect } from "bun:test";

// ─── Lógica de display de sensores (replica MonitoringScreen / watchdog) ──────

// Umbral de display (90s): sensor aparece offline en la UI
// Umbral de alerta (30s): watchdog dispara sensor_offline alert
const OFFLINE_DISPLAY_MS = 90_000;

function getSensorDisplayStatus(
  lastSeenMs: number | null,
  enable: boolean,
  now: number,
): "online" | "offline" | "disabled" {
  if (!enable) return "disabled";
  if (lastSeenMs === null) return "offline";
  return now - lastSeenMs > OFFLINE_DISPLAY_MS ? "offline" : "online";
}

function formatTemperature(value: number | null | undefined): string {
  if (value === null || value === undefined) return "--";
  return `${value.toFixed(1)}°C`;
}

function formatBattery(value: number | null | undefined): string {
  if (value === null || value === undefined) return "--";
  return `${Math.round(value)}%`;
}

function isReadingFresh(receivedAt: string, now: number, maxAgeMs = 300_000): boolean {
  const ts = new Date(receivedAt).getTime();
  return !isNaN(ts) && now - ts < maxAgeMs;
}

// ─── HU: ver temperatura actual ───────────────────────────────────────────────

describe("HU: ver temperatura actual del sensor", () => {
  it("temperatura válida se formatea con un decimal y unidad °C", () => {
    expect(formatTemperature(22.5)).toBe("22.5°C");
  });

  it("temperatura negativa se formatea correctamente", () => {
    expect(formatTemperature(-3.7)).toBe("-3.7°C");
  });

  it("temperatura con más decimales se redondea a uno", () => {
    expect(formatTemperature(15.678)).toBe("15.7°C");
  });

  it("temperatura nula muestra placeholder '--'", () => {
    expect(formatTemperature(null)).toBe("--");
  });

  it("temperatura undefined muestra placeholder '--'", () => {
    expect(formatTemperature(undefined)).toBe("--");
  });

  it("cero grados se muestra como '0.0°C' no como vacío", () => {
    expect(formatTemperature(0)).toBe("0.0°C");
  });
});

// ─── HU: ver nivel de batería del gateway ─────────────────────────────────────

describe("HU: ver nivel de batería del gateway", () => {
  it("batería válida se formatea como porcentaje entero", () => {
    expect(formatBattery(85.6)).toBe("86%");
  });

  it("batería en 0% se muestra como '0%', no como '--'", () => {
    expect(formatBattery(0)).toBe("0%");
  });

  it("batería null muestra '--'", () => {
    expect(formatBattery(null)).toBe("--");
  });

  it("batería 100% se muestra correctamente", () => {
    expect(formatBattery(100)).toBe("100%");
  });

  it("fracción de batería (14.9%) se redondea hacia arriba a 15%", () => {
    expect(formatBattery(14.9)).toBe("15%");
  });
});

// ─── HU: ver timestamp de la última lectura ───────────────────────────────────

describe("HU: timestamp de la última lectura del sensor", () => {
  const now = Date.now();

  it("lectura recibida hace 1 minuto es fresca (dentro de 5 min)", () => {
    const receivedAt = new Date(now - 60_000).toISOString();
    expect(isReadingFresh(receivedAt, now)).toBe(true);
  });

  it("lectura recibida hace exactamente 5 minutos es el límite — no es fresca", () => {
    const receivedAt = new Date(now - 300_000).toISOString();
    expect(isReadingFresh(receivedAt, now)).toBe(false);
  });

  it("lectura de hace 10 minutos no es fresca", () => {
    const receivedAt = new Date(now - 600_000).toISOString();
    expect(isReadingFresh(receivedAt, now)).toBe(false);
  });

  it("timestamp inválido no es fresco", () => {
    expect(isReadingFresh("not-a-date", now)).toBe(false);
  });

  it("lectura recibida ahora mismo es fresca", () => {
    const receivedAt = new Date(now).toISOString();
    expect(isReadingFresh(receivedAt, now)).toBe(true);
  });
});

// ─── HU: ver estado de cada sensor ───────────────────────────────────────────

describe("HU: estado de cada sensor en monitoreo", () => {
  const now = Date.now();

  it("sensor con lectura reciente (< 90s) está online", () => {
    expect(getSensorDisplayStatus(now - 30_000, true, now)).toBe("online");
  });

  it("sensor con lectura exactamente en 90s sigue online (el umbral es estricto: > 90s)", () => {
    expect(getSensorDisplayStatus(now - 90_000, true, now)).toBe("online");
  });

  it("sensor sin lectura en más de 90s está offline", () => {
    expect(getSensorDisplayStatus(now - 120_000, true, now)).toBe("offline");
  });

  it("sensor deshabilitado (enable=false) aparece como disabled independiente del último heartbeat", () => {
    expect(getSensorDisplayStatus(now - 5_000, false, now)).toBe("disabled");
  });

  it("sensor nunca visto (lastSeen=null) aparece offline", () => {
    expect(getSensorDisplayStatus(null, true, now)).toBe("offline");
  });

  it("sensor deshabilitado sin heartbeat también es disabled, no offline", () => {
    expect(getSensorDisplayStatus(null, false, now)).toBe("disabled");
  });

  it("el umbral de display (90s) es independiente del umbral de alerta watchdog (30s)", () => {
    // Entre 30s y 90s: alerta ya disparada pero sensor sigue 'online' en pantalla
    const lastSeen = now - 60_000;
    expect(getSensorDisplayStatus(lastSeen, true, now)).toBe("online");
  });
});
