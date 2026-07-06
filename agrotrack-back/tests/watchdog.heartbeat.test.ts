import { describe, it, expect } from "bun:test";

// ─── Replica la resolución de gateway_id de `heartbeat()` en watchdog.service.ts:
// resolvedGatewayId = gatewayId ?? existing?.gateway_id ?? 0
function resolveGatewayId(
  gatewayId: number | undefined,
  existing: { gateway_id: number } | undefined,
): number {
  return gatewayId ?? existing?.gateway_id ?? 0;
}

describe("Watchdog — resolución de gateway_id en heartbeat", () => {
  it("usa el gateway_id explícito cuando se provee (ej. MQTT, ingest HTTP)", () => {
    expect(resolveGatewayId(1, undefined)).toBe(1);
  });

  it("conserva el gateway_id de un heartbeat previo si no se provee uno nuevo", () => {
    expect(resolveGatewayId(undefined, { gateway_id: 1 })).toBe(1);
  });

  it("cae a 0 solo cuando nunca hubo gateway_id (ni explícito ni previo) — caso bug detectado", () => {
    expect(resolveGatewayId(undefined, undefined)).toBe(0);
  });

  it("un gateway_id explícito sobreescribe uno previo distinto", () => {
    expect(resolveGatewayId(2, { gateway_id: 1 })).toBe(2);
  });
});
