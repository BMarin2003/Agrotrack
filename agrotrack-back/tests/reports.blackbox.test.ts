import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

const DAY_AGO = new Date(Date.now() - 86_400_000).toISOString();
const NOW = new Date().toISOString();

async function firstIds(token: string) {
  const sensorsRes = await routerApi.handle(
    new Request("http://localhost/sensors", { headers: authHeaders(token) }),
  );
  const sensors = await sensorsRes.json();
  const gwRes = await routerApi.handle(
    new Request("http://localhost/sensors/gateways", { headers: authHeaders(token) }),
  );
  const gateways = await gwRes.json();
  if (!sensors.length || !gateways.length) throw new Error("Sin datos para testear reportes");
  return { sensorId: sensors[0].id, gatewayId: gateways[0].id };
}

describe("Caja negra — GET /reports/sensor/:sensor_id", () => {
  it("devuelve historial con rango de fechas (200, array)", async () => {
    const token = await getToken("operador");
    const { sensorId } = await firstIds(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/reports/sensor/${sensorId}?from=${DAY_AGO}&to=${NOW}`, {
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });
});

describe("Caja negra — GET /reports/gateway/:id", () => {
  it("devuelve el reporte agregado del gateway (200, con summary y sensors)", async () => {
    const token = await getToken("operador");
    const { gatewayId } = await firstIds(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/reports/gateway/${gatewayId}?from=${DAY_AGO}&to=${NOW}`, {
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("gateway");
    expect(body).toHaveProperty("summary");
    expect(body).toHaveProperty("sensors");
  });

  it("gateway_id inexistente devuelve error (500, RAISE EXCEPTION del lado SQL)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request(`http://localhost/reports/gateway/99999999?from=${DAY_AGO}&to=${NOW}`, {
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(500);
  });
});

describe("Caja negra — GET /reports/general", () => {
  it("devuelve el rollup de todos los gateways (200, con gateways y totals)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request(`http://localhost/reports/general?from=${DAY_AGO}&to=${NOW}`, {
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("gateways");
    expect(body).toHaveProperty("totals");
    expect(body.totals).toHaveProperty("gateway_count");
  });
});

describe("Caja negra — GET /reports/alerts/:gateway_id", () => {
  it("devuelve historial de alertas del gateway (200, array)", async () => {
    const token = await getToken("operador");
    const { gatewayId } = await firstIds(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/reports/alerts/${gatewayId}?from=${DAY_AGO}&to=${NOW}`, {
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(200);
  });
});
