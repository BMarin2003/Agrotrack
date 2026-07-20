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

    // El sensor usado aquí es real (compartido con la app) y el alias está
    // scopeado al usuario `operador`. Leemos el valor original ANTES de
    // sobreescribirlo para poder restaurarlo al final y no dejar un string
    // de prueba visible permanentemente en la app.
    const originalGetRes = await routerApi.handle(
      new Request(`http://localhost/sensors/${id}/alias`, { headers: authHeaders(token) }),
    );
    expect(originalGetRes.status).toBe(200);
    const originalAlias: string | null = (await originalGetRes.json()).alias ?? null;

    try {
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
    } finally {
      // Restaurar el alias original incluso si alguna aserción falló arriba.
      // El body de PUT /alias exige minLength: 1 (ver sensors.api.ts), así
      // que si el sensor nunca tuvo alias (originalAlias === null) no hay
      // forma de "restaurar null" por este endpoint — en ese caso no queda
      // otra que dejarlo como no-op documentado.
      if (originalAlias !== null) {
        await routerApi.handle(
          new Request(`http://localhost/sensors/${id}/alias`, {
            method: "PUT",
            headers: authHeaders(token),
            body: JSON.stringify({ alias: originalAlias }),
          }),
        );
      }
    }
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

  // NOTA: no hay caso "técnico calibra un sensor exitosamente (200)" a propósito.
  // `iot.save_calibration` (migrations/009_calibration_ack.sql:26-36) es un INSERT
  // puro sin dedup ni cleanup, y `iot.get_calibration` siempre devuelve la fila
  // más reciente (ORDER BY c.applied_at DESC LIMIT 1). Un POST exitoso contra el
  // primer sensor real de la BD dejaría una calibración falsa como "la actual"
  // del sensor para cualquier usuario real que lo consulte en la app — de forma
  // permanente y sin manera de deshacerlo — y además el route intenta publicar
  // un comando MQTT real al gateway físico. Por eso este archivo solo prueba
  // los caminos negativos (permisos/validación), que nunca llegan al INSERT.
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
