import { describe, it, expect } from "bun:test";

// ─── Mapeo de payload de telemetría → estado de gateway (replica telemetry.api.ts/mqtt.service.ts)
function extractGatewayStatus(payload: {
  connectivity_mode?: string;
  pending_sync?: number;
  gateway_battery?: number;
}): { connectivity_mode?: string; pending_sync_count?: number; battery?: number } | null {
  const hasConnectivity = payload.connectivity_mode === "wifi" || payload.connectivity_mode === "sim";
  const hasPendingSync = typeof payload.pending_sync === "number";
  const hasBattery = typeof payload.gateway_battery === "number";

  if (!hasConnectivity && !hasPendingSync && !hasBattery) return null;

  return {
    ...(hasConnectivity ? { connectivity_mode: payload.connectivity_mode } : {}),
    ...(hasPendingSync ? { pending_sync_count: payload.pending_sync } : {}),
    ...(hasBattery ? { battery: payload.gateway_battery } : {}),
  };
}

describe("Estado de gateway — mapeo desde payload de telemetría", () => {
  it("payload con connectivity_mode='wifi' se mapea correctamente", () => {
    expect(extractGatewayStatus({ connectivity_mode: "wifi" })).toEqual({ connectivity_mode: "wifi" });
  });

  it("payload con connectivity_mode='sim' se mapea correctamente", () => {
    expect(extractGatewayStatus({ connectivity_mode: "sim" })).toEqual({ connectivity_mode: "sim" });
  });

  it("connectivity_mode con valor desconocido se ignora (no se envía)", () => {
    expect(extractGatewayStatus({ connectivity_mode: "bluetooth" })).toBeNull();
  });

  it("pending_sync=0 se mapea (sincronizado)", () => {
    expect(extractGatewayStatus({ pending_sync: 0 })).toEqual({ pending_sync_count: 0 });
  });

  it("pending_sync>0 se mapea (sincronización pendiente)", () => {
    expect(extractGatewayStatus({ pending_sync: 12 })).toEqual({ pending_sync_count: 12 });
  });

  it("gateway_battery se mapea a battery", () => {
    expect(extractGatewayStatus({ gateway_battery: 87 })).toEqual({ battery: 87 });
  });

  it("gateway_battery=0 se mapea (bateria agotada, no se confunde con ausente)", () => {
    expect(extractGatewayStatus({ gateway_battery: 0 })).toEqual({ battery: 0 });
  });

  it("payload sin ninguno de los tres campos no genera actualización", () => {
    expect(extractGatewayStatus({})).toBeNull();
  });

  it("payload con los tres campos los mapea juntos", () => {
    expect(extractGatewayStatus({ connectivity_mode: "sim", pending_sync: 3, gateway_battery: 55 }))
      .toEqual({ connectivity_mode: "sim", pending_sync_count: 3, battery: 55 });
  });
});
