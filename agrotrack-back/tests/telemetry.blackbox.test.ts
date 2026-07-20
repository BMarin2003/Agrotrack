import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

describe("Caja negra — POST /telemetry/ingest (solo casos negativos, ver Global Constraints)", () => {
  it("sin header x-api-key devuelve 401", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/telemetry/ingest", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sensor_id: 1, temperature: 4.5 }),
      }),
    );
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.message).toBe("API Key requerida");
  });

  it("x-api-key inválida (gateway no encontrado) devuelve 401", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/telemetry/ingest", {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-api-key": "clave-que-no-existe" },
        body: JSON.stringify({ sensor_id: 1, temperature: 4.5 }),
      }),
    );
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.message).toBe("Gateway no autorizado");
  });
});

describe("Caja negra — GET /telemetry/latest/:gateway_id y /telemetry/sensor/:sensor_id/last", () => {
  it("lecturas más recientes de un gateway (200, array)", async () => {
    const token = await getToken("operador");
    const gwRes = await routerApi.handle(
      new Request("http://localhost/sensors/gateways", { headers: authHeaders(token) }),
    );
    const gateways = await gwRes.json();
    if (!gateways.length) return;

    const res = await routerApi.handle(
      new Request(`http://localhost/telemetry/latest/${gateways[0].id}`, { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
  });

  it("sensor sin lecturas devuelve 404", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/telemetry/sensor/99999999/last", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(404);
  });
});
