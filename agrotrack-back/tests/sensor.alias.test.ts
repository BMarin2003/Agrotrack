import { describe, it, expect } from "bun:test";

// ─── Regla de resolución de nombre visible (replica el COALESCE de iot.list_sensors/get_sensor)
function resolveSensorDisplayName(technicalName: string, alias: string | null): string {
  return alias ?? technicalName;
}

describe("Alias de sensor — resolución de nombre visible", () => {
  it("sin alias, se muestra el nombre técnico", () => {
    expect(resolveSensorDisplayName("SN-001", null)).toBe("SN-001");
  });

  it("con alias, se muestra el alias en vez del nombre técnico", () => {
    expect(resolveSensorDisplayName("SN-001", "Cámara fría 2")).toBe("Cámara fría 2");
  });

  it("alias vacío (string vacío) no es tratado como ausente — se respeta tal cual", () => {
    expect(resolveSensorDisplayName("SN-001", "")).toBe("");
  });
});
