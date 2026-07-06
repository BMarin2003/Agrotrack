import { describe, it, expect } from "bun:test";

// ─── Replica el merge de `enable` en iot.upsert_threshold (migración 006):
// COALESCE((p_data->>'enable')::BOOLEAN, TRUE) en el INSERT,
// EXCLUDED.enable (el valor recién resuelto) en el UPDATE por conflicto.
function resolveEnableOnUpsert(incomingEnable: boolean | undefined): boolean {
  return incomingEnable ?? true;
}

describe("Persistencia de iot.upsert_threshold — campo enable", () => {
  it("guardar con enable=false debe persistir false (bug: antes quedaba forzado a true)", () => {
    expect(resolveEnableOnUpsert(false)).toBe(false);
  });

  it("guardar con enable=true persiste true", () => {
    expect(resolveEnableOnUpsert(true)).toBe(true);
  });

  it("si no se envía enable, se asume true (compatibilidad con llamadas previas)", () => {
    expect(resolveEnableOnUpsert(undefined)).toBe(true);
  });

  it("un guardado posterior con enable=false tras uno con true también persiste false", () => {
    let enable = resolveEnableOnUpsert(true);
    expect(enable).toBe(true);
    enable = resolveEnableOnUpsert(false);
    expect(enable).toBe(false);
  });
});
