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
