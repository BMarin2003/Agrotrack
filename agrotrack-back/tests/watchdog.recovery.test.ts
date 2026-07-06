import { describe, it, expect } from "bun:test";

// ─── Regla: solo se dispara sensor_recovered si el sensor ESTABA marcado offline
// (replica el `if (offlineSensors.has(sensorId))` de watchdog.service.ts)
function shouldEmitRecoveryAlert(offlineSensors: Set<number>, sensorId: number): boolean {
  return offlineSensors.has(sensorId);
}

describe("Watchdog — alerta de recuperación (sensor_recovered)", () => {
  it("sensor previamente offline que vuelve a latir dispara la alerta", () => {
    const offlineSensors = new Set<number>([1, 2]);
    expect(shouldEmitRecoveryAlert(offlineSensors, 1)).toBe(true);
  });

  it("sensor que nunca estuvo offline no dispara la alerta", () => {
    const offlineSensors = new Set<number>([2]);
    expect(shouldEmitRecoveryAlert(offlineSensors, 1)).toBe(false);
  });

  it("tras dispararse la alerta, el sensor sale del set (no se repite en el siguiente heartbeat)", () => {
    const offlineSensors = new Set<number>([1]);
    const shouldEmit = shouldEmitRecoveryAlert(offlineSensors, 1);
    if (shouldEmit) offlineSensors.delete(1);

    expect(shouldEmit).toBe(true);
    expect(shouldEmitRecoveryAlert(offlineSensors, 1)).toBe(false);
  });

  it("recuperación de un sensor no afecta el estado offline de otro", () => {
    const offlineSensors = new Set<number>([1, 2]);
    if (shouldEmitRecoveryAlert(offlineSensors, 1)) offlineSensors.delete(1);
    expect(offlineSensors.has(2)).toBe(true);
  });
});
