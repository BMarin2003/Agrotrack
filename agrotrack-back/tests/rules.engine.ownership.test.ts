import { describe, it, expect } from "bun:test";

interface ThresholdRow {
  id: number;
  user_id: number | null;
  metric: string;
  min_value: number | null;
  max_value: number | null;
}

// ─── Regla: cada alerta se etiqueta con el user_id del umbral que la disparó (replica rules.engine.ts)
function buildAlertPayload(threshold: ThresholdRow, sensorId: number, gatewayId: number, value: number) {
  return {
    sensor_id: sensorId,
    gateway_id: gatewayId,
    type: "threshold_exceeded" as const,
    metric: threshold.metric,
    value,
    user_id: threshold.user_id,
  };
}

describe("RulesEngine — etiquetado de alertas por dueño del umbral", () => {
  it("umbral del operador 7 genera una alerta con user_id=7", () => {
    const threshold: ThresholdRow = { id: 1, user_id: 7, metric: "temperature", min_value: 2, max_value: 25 };
    const alert = buildAlertPayload(threshold, 10, 1, 30);
    expect(alert.user_id).toBe(7);
  });

  it("umbral del operador 9 genera una alerta independiente con user_id=9, para el mismo sensor", () => {
    const thresholdA: ThresholdRow = { id: 1, user_id: 7, metric: "temperature", min_value: 2, max_value: 20 };
    const thresholdB: ThresholdRow = { id: 2, user_id: 9, metric: "temperature", min_value: 2, max_value: 25 };
    const alertA = buildAlertPayload(thresholdA, 10, 1, 22); // rompe el de A (max 20) pero no el de B (max 25)
    const alertB = buildAlertPayload(thresholdB, 10, 1, 22);
    expect(alertA.user_id).toBe(7);
    expect(alertB.user_id).toBe(9);
    expect(alertA.user_id).not.toBe(alertB.user_id);
  });

  it("un umbral sin dueño (legado, user_id null) genera alerta de sistema sin dueño", () => {
    const threshold: ThresholdRow = { id: 3, user_id: null, metric: "temperature", min_value: 2, max_value: 25 };
    const alert = buildAlertPayload(threshold, 10, 1, 30);
    expect(alert.user_id).toBeNull();
  });
});
