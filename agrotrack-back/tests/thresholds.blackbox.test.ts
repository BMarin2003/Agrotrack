import { describe, it, expect, beforeAll } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

let testSensorId: number;

beforeAll(async () => {
  const token = await getToken("tecnico");

  const gwRes = await routerApi.handle(
    new Request("http://localhost/sensors/gateways", {
      method: "POST",
      headers: authHeaders(token),
      body: JSON.stringify({
        name: `Gateway prueba umbrales ${Date.now()}`,
        identifier: `test-thresholds-gw-${Date.now()}`,
      }),
    }),
  );
  if (gwRes.status !== 200) throw new Error(`No se pudo crear el gateway sintético: HTTP ${gwRes.status}`);
  const gateway = await gwRes.json();

  const sensorRes = await routerApi.handle(
    new Request("http://localhost/sensors", {
      method: "POST",
      headers: authHeaders(token),
      body: JSON.stringify({
        gateway_id: gateway.id,
        name: `Sensor prueba umbrales ${Date.now()}`,
        identifier: `test-thresholds-sensor-${Date.now()}`,
      }),
    }),
  );
  if (sensorRes.status !== 200) throw new Error(`No se pudo crear el sensor sintético: HTTP ${sensorRes.status}`);
  const sensor = await sensorRes.json();
  testSensorId = sensor.id;
});

describe("Caja negra — GET/POST /thresholds (sobre el sensor sintético, NUNCA uno real)", () => {
  it("crea un umbral propio y lo puede volver a leer (round-trip, scoping por operador)", async () => {
    const token = await getToken("operador");

    const postRes = await routerApi.handle(
      new Request("http://localhost/thresholds", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ sensor_id: testSensorId, metric: "temperature", min_value: -2, max_value: 8 }),
      }),
    );
    expect(postRes.status).toBe(200);

    const getRes = await routerApi.handle(
      new Request(`http://localhost/thresholds?sensor_id=${testSensorId}`, { headers: authHeaders(token) }),
    );
    expect(getRes.status).toBe(200);
    const list = await getRes.json();
    expect(list.some((t: any) => t.sensor_id === testSensorId && t.metric === "temperature")).toBe(true);
  });

  it("sin token devuelve 401", async () => {
    const res = await routerApi.handle(new Request("http://localhost/thresholds"));
    expect(res.status).toBe(401);
  });
});

describe("Caja negra — DELETE /thresholds/:id (verifica el fix de ownership de esta sesión, sobre el sensor sintético)", () => {
  it("un operador puede borrar SU PROPIO umbral", async () => {
    const token = await getToken("operador");

    const createRes = await routerApi.handle(
      new Request("http://localhost/thresholds", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ sensor_id: testSensorId, metric: "voltage", min_value: 3.0, max_value: 4.3 }),
      }),
    );
    const created = await createRes.json();

    const deleteRes = await routerApi.handle(
      new Request(`http://localhost/thresholds/${created.id}`, {
        method: "DELETE",
        headers: authHeaders(token),
      }),
    );
    expect(deleteRes.status).toBe(200);
  });

  it("un operador NO puede borrar un umbral de OTRO operador (caja negra del fix de esta sesión)", async () => {
    const tecnicoToken = await getToken("tecnico");

    // El técnico crea su propio umbral en el sensor sintético.
    const createRes = await routerApi.handle(
      new Request("http://localhost/thresholds", {
        method: "POST",
        headers: authHeaders(tecnicoToken),
        body: JSON.stringify({ sensor_id: testSensorId, metric: "battery", min_value: 10, max_value: 100 }),
      }),
    );
    const tecnicoThreshold = await createRes.json();

    // El operador intenta borrar el umbral del técnico — debe fallar.
    const operadorToken = await getToken("operador");
    const deleteRes = await routerApi.handle(
      new Request(`http://localhost/thresholds/${tecnicoThreshold.id}`, {
        method: "DELETE",
        headers: authHeaders(operadorToken),
      }),
    );
    expect(deleteRes.status).toBe(400); // iot.delete_threshold lanza excepción → set.status = 400
    const body = await deleteRes.json();
    expect(body.message).toMatch(/no encontrado|sin permiso/i);

    // Limpieza: el técnico borra su propio umbral (sobre el sensor sintético, sin riesgo).
    await routerApi.handle(
      new Request(`http://localhost/thresholds/${tecnicoThreshold.id}`, {
        method: "DELETE",
        headers: authHeaders(tecnicoToken),
      }),
    );
  });

  it("admin puede borrar el umbral de cualquier operador", async () => {
    const operadorToken = await getToken("operador");
    const createRes = await routerApi.handle(
      new Request("http://localhost/thresholds", {
        method: "POST",
        headers: authHeaders(operadorToken),
        body: JSON.stringify({ sensor_id: testSensorId, metric: "temperature", min_value: 0, max_value: 5 }),
      }),
    );
    const created = await createRes.json();

    const adminToken = await getToken("admin");
    const deleteRes = await routerApi.handle(
      new Request(`http://localhost/thresholds/${created.id}`, {
        method: "DELETE",
        headers: authHeaders(adminToken),
      }),
    );
    expect(deleteRes.status).toBe(200);
  });
});
