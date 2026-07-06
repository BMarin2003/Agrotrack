import { describe, it, expect } from "bun:test";

// ─── Mapeo de payload de telemetría → estado de gateway (replica telemetry.api.ts/mqtt.service.ts)
function extractGatewayStatus(payload: {
  connectivity_mode?: string;
  pending_sync?: number;
}): { connectivity_mode?: string; pending_sync_count?: number } | null {
  const hasConnectivity = payload.connectivity_mode === "wifi" || payload.connectivity_mode === "sim";
  const hasPendingSync = typeof payload.pending_sync === "number";

  if (!hasConnectivity && !hasPendingSync) return null;

  return {
    ...(hasConnectivity ? { connectivity_mode: payload.connectivity_mode } : {}),
    ...(hasPendingSync ? { pending_sync_count: payload.pending_sync } : {}),
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

  it("payload sin ninguno de los dos campos no genera actualización", () => {
    expect(extractGatewayStatus({})).toBeNull();
  });

  it("payload con ambos campos mapea los dos juntos", () => {
    expect(extractGatewayStatus({ connectivity_mode: "sim", pending_sync: 3 }))
      .toEqual({ connectivity_mode: "sim", pending_sync_count: 3 });
  });
});
