import { describe, it, expect, beforeAll } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

let testGatewayId: number;

beforeAll(async () => {
  const token = await getToken("tecnico");
  const res = await routerApi.handle(
    new Request("http://localhost/sensors/gateways", {
      method: "POST",
      headers: authHeaders(token),
      body: JSON.stringify({
        name: `Gateway prueba caja negra ${Date.now()}`,
        identifier: `test-cn-${Date.now()}`,
        location: "Ubicación de prueba",
      }),
    }),
  );
  if (res.status !== 200) throw new Error(`No se pudo crear el gateway sintético de prueba: HTTP ${res.status}`);
  const created = await res.json();
  testGatewayId = created.id;
});

describe("Caja negra — POST /sensors/gateways (crear gateway)", () => {
  it("el gateway sintético de beforeAll se creó correctamente (200, con id)", () => {
    expect(typeof testGatewayId).toBe("number");
  });

  it("operador NO tiene permiso manage_gateways (403)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/sensors/gateways", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ name: "X", identifier: `test-${Date.now()}` }),
      }),
    );
    expect(res.status).toBe(403);
  });
});

describe("Caja negra — PUT /sensors/gateways/:id/wifi (sobre el gateway sintético, NUNCA uno real)", () => {
  it("técnico configura WiFi del gateway sintético (200)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/gateways/${testGatewayId}/wifi`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ ssid: "RedDePrueba", password: "clave12345", security: "WPA2" }),
      }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.ok).toBe(true);
    expect(body.ssid).toBe("RedDePrueba");
  });

  it("security con valor fuera del enum permitido devuelve error de validación (422)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/gateways/${testGatewayId}/wifi`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ ssid: "Red", security: "WEP" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});

describe("Caja negra — PUT /sensors/gateways/:id/mqtt-topic (sobre el gateway sintético)", () => {
  it("técnico configura el tópico MQTT del gateway sintético (200)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/gateways/${testGatewayId}/mqtt-topic`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ topic: `agrotrack/gateways/${testGatewayId}/data` }),
      }),
    );
    expect(res.status).toBe(200);
  });

  it("tópico vacío devuelve error de validación (422, minLength: 1)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request(`http://localhost/sensors/gateways/${testGatewayId}/mqtt-topic`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ topic: "" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});

describe("Caja negra — PUT /sensors/gateways/pin (sobre el gateway sintético)", () => {
  it("técnico configura PIN de 4 dígitos en el gateway sintético (200)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request("http://localhost/sensors/gateways/pin", {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ gateway_ids: [testGatewayId], pin: "1234" }),
      }),
    );
    expect(res.status).toBe(200);
  });

  it("PIN que no matchea el patrón de 4 dígitos devuelve error de validación (422)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request("http://localhost/sensors/gateways/pin", {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ gateway_ids: [testGatewayId], pin: "12" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});

describe("Caja negra — GET/POST /sensors/gateways/:id/maintenance (sobre el gateway sintético)", () => {
  it("registra un mantenimiento y luego aparece en el listado (round-trip)", async () => {
    const token = await getToken("tecnico");

    const postRes = await routerApi.handle(
      new Request(`http://localhost/sensors/gateways/${testGatewayId}/maintenance`, {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ notes: "Mantenimiento de prueba caja negra" }),
      }),
    );
    expect(postRes.status).toBe(200);

    const listRes = await routerApi.handle(
      new Request(`http://localhost/sensors/gateways/${testGatewayId}/maintenance`, { headers: authHeaders(token) }),
    );
    expect(listRes.status).toBe(200);
    const records = await listRes.json();
    expect(records.some((r: any) => r.notes === "Mantenimiento de prueba caja negra")).toBe(true);
  });
});
