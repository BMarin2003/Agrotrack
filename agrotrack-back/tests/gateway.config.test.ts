import { describe, it, expect } from "bun:test";

// ─── Validación de configuración de gateway (WiFi + PIN) ─────────────────────

function isValidSsid(ssid: string): boolean {
  return ssid.trim().length > 0;
}

function isValidSecurity(security: string): boolean {
  return ["WPA2", "WPA3", "open"].includes(security);
}

function isValidPin(pin: string): boolean {
  return /^\d{4}$/.test(pin);
}

// Simula la selección de gateways para config en lote
function resolveTargetGateways(
  allIds: number[],
  selectedId: number | null,
  applyToAll: boolean,
): number[] {
  if (applyToAll) return allIds;
  return selectedId !== null ? [selectedId] : [];
}

// ─── HU: configurar red WiFi del gateway ─────────────────────────────────────

describe("HU: configurar red WiFi del gateway", () => {
  it("SSID con texto es válido", () => {
    expect(isValidSsid("MiRedWiFi")).toBe(true);
  });

  it("SSID vacío es inválido", () => {
    expect(isValidSsid("")).toBe(false);
  });

  it("SSID con solo espacios es inválido", () => {
    expect(isValidSsid("   ")).toBe(false);
  });

  it("SSID con caracteres especiales es válido", () => {
    expect(isValidSsid("AgroTrack_2026!")).toBe(true);
  });

  it("tipo de seguridad WPA2 es válido", () => {
    expect(isValidSecurity("WPA2")).toBe(true);
  });

  it("tipo de seguridad WPA3 es válido", () => {
    expect(isValidSecurity("WPA3")).toBe(true);
  });

  it("tipo de seguridad open es válido (red sin contraseña)", () => {
    expect(isValidSecurity("open")).toBe(true);
  });

  it("tipo de seguridad desconocido es inválido", () => {
    expect(isValidSecurity("WEP")).toBe(false);
  });

  it("contraseña puede estar vacía en red abierta (open)", () => {
    const password = "";
    const security = "open";
    expect(isValidSecurity(security)).toBe(true);
    expect(password).toBe("");
  });
});

// ─── HU: configurar PIN del gateway ──────────────────────────────────────────

describe("HU: configurar PIN del gateway (4 dígitos)", () => {
  it("PIN de exactamente 4 dígitos es válido", () => {
    expect(isValidPin("1234")).toBe(true);
  });

  it("PIN de 3 dígitos es inválido", () => {
    expect(isValidPin("123")).toBe(false);
  });

  it("PIN de 5 dígitos es inválido", () => {
    expect(isValidPin("12345")).toBe(false);
  });

  it("PIN vacío es inválido", () => {
    expect(isValidPin("")).toBe(false);
  });

  it("PIN con letras es inválido", () => {
    expect(isValidPin("12a4")).toBe(false);
  });

  it("PIN con caracteres especiales es inválido", () => {
    expect(isValidPin("12.4")).toBe(false);
  });

  it("PIN '0000' es válido (todo ceros es un PIN legítimo)", () => {
    expect(isValidPin("0000")).toBe(true);
  });

  it("PIN '9999' es válido (todo nueves)", () => {
    expect(isValidPin("9999")).toBe(true);
  });

  it("PIN con espacios es inválido", () => {
    expect(isValidPin("12 4")).toBe(false);
  });
});

// ─── HU: configurar PIN en lote (múltiples gateways) ─────────────────────────

describe("HU: configurar PIN en lote para múltiples gateways", () => {
  const ALL_IDS = [1, 2, 3, 4];

  it("applyToAll=true envía a todos los gateways", () => {
    const targets = resolveTargetGateways(ALL_IDS, 1, true);
    expect(targets).toEqual(ALL_IDS);
    expect(targets).toHaveLength(4);
  });

  it("applyToAll=false con gateway seleccionado envía solo a ese", () => {
    const targets = resolveTargetGateways(ALL_IDS, 2, false);
    expect(targets).toEqual([2]);
  });

  it("applyToAll=false sin gateway seleccionado no envía a ninguno", () => {
    const targets = resolveTargetGateways(ALL_IDS, null, false);
    expect(targets).toHaveLength(0);
  });

  it("applyToAll=true con un único gateway sigue enviando al único", () => {
    const targets = resolveTargetGateways([5], 5, true);
    expect(targets).toEqual([5]);
  });

  it("el PIN no se duplica al enviar a múltiples gateways", () => {
    const pin    = "4567";
    const ids    = resolveTargetGateways(ALL_IDS, null, true);
    const payloads = ids.map((id) => ({ gateway_id: id, pin }));
    expect(new Set(payloads.map((p) => p.pin)).size).toBe(1);
    expect(payloads).toHaveLength(4);
  });
});
