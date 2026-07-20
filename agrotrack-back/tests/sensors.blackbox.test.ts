import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

async function firstSensorId(token: string): Promise<number> {
  const res = await routerApi.handle(
    new Request("http://localhost/sensors", { headers: authHeaders(token) }),
  );
  const sensors = await res.json();
  if (!sensors.length) throw new Error("No hay sensores en la BD para testear — la flota debe estar corriendo");
  return sensors[0].id;
}

describe("Caja negra — GET /sensors y /sensors/gateways", () => {
  it("lista sensores con permiso view_sensors (200, array)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/sensors", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  it("filtra sensores por gateway_id vía query param", async () => {
    const token = await getToken("operador");
    const gwRes = await routerApi.handle(
      new Request("http://localhost/sensors/gateways", { headers: authHeaders(token) }),
    );
    const gateways = await gwRes.json();
    if (!gateways.length) return; // sin datos, nada que filtrar
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors?gateway_id=${gateways[0].id}`, { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
    const sensors = await res.json();
    expect(sensors.every((s: any) => s.gateway_id === gateways[0].id)).toBe(true);
  });

  it("sin token devuelve 401", async () => {
    const res = await routerApi.handle(new Request("http://localhost/sensors"));
    expect(res.status).toBe(401);
  });
});

describe("Caja negra — GET /sensors/:id", () => {
  it("id existente devuelve 200 con el sensor", async () => {
    const token = await getToken("operador");
    const id = await firstSensorId(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}`, { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
  });

  it("id inexistente devuelve 404", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/sensors/99999999", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(404);
  });
});

describe("Caja negra — GET/PUT /sensors/:id/alias", () => {
  it("guarda un alias y lo puede volver a leer (round-trip)", async () => {
    const token = await getToken("operador");
    const id = await firstSensorId(token);
    const alias = `Alias de prueba ${Date.now()}`;

    const putRes = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/alias`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ alias }),
      }),
    );
    expect(putRes.status).toBe(200);

    const getRes = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/alias`, { headers: authHeaders(token) }),
    );
    expect(getRes.status).toBe(200);
    const body = await getRes.json();
    expect(body.alias).toBe(alias);
  });

  it("alias vacío devuelve error de validación (422, minLength: 1)", async () => {
    const token = await getToken("operador");
    const id = await firstSensorId(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/alias`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ alias: "" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});

describe("Caja negra — GET/POST /sensors/:id/calibration", () => {
  it("consulta calibración de un sensor sin calibración previa (200, null o vacío)", async () => {
    const token = await getToken("tecnico");
    const id = await firstSensorId(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/calibration`, { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
  });

  it("operador NO tiene permiso manage_sensors para calibrar (403)", async () => {
    const token = await getToken("operador");
    const id = await firstSensorId(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/calibration`, {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ gain: 1.0, intercept: 0.0 }),
      }),
    );
    expect(res.status).toBe(403);
  });

  it("técnico calibra un sensor exitosamente (200)", async () => {
    const token = await getToken("tecnico");
    const id = await firstSensorId(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/calibration`, {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ gain: 1.02, intercept: -0.15, notes: "Calibración de prueba caja negra" }),
      }),
    );
    expect(res.status).toBe(200);
  });

  it("gain no numérico devuelve error de validación (422)", async () => {
    const token = await getToken("tecnico");
    const id = await firstSensorId(token);
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/calibration`, {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ gain: "no-es-numero", intercept: 0.0 }),
      }),
    );
    expect(res.status).toBe(422);
  });
});
