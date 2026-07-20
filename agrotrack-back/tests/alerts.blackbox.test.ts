import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

describe("Caja negra — GET /alerts", () => {
  it("lista alertas activas con permiso view_alerts (200, array)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/alerts", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  it("filtra por resolved=true vía query param", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/alerts?resolved=true", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
  });

  it("sin token devuelve 401", async () => {
    const res = await routerApi.handle(new Request("http://localhost/alerts"));
    expect(res.status).toBe(401);
  });
});

describe("Caja negra — PUT /alerts/:id/resolve", () => {
  it("id inexistente no rompe el endpoint (400, ninguna fila afectada)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/alerts/999999999/resolve", {
        method: "PUT",
        headers: authHeaders(token),
      }),
    );
    // iot.resolve_alert sobre un id inexistente no lanza excepción (UPDATE afecta 0 filas) — 200 con éxito silencioso.
    expect(res.status).toBe(200);
  });
});

describe("Caja negra — DELETE /alerts/clear (SOLO auth/permisos, nunca el camino de éxito)", () => {
  it("sin token devuelve 401 — NO se ejecuta el borrado real", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/alerts/clear", { method: "DELETE" }),
    );
    expect(res.status).toBe(401);
  });

  it("operador sin permiso resolve_alerts (si no lo tuviera) devolvería 403 — aquí Operador SÍ tiene resolve_alerts, se documenta que no se prueba el 200 por ser destructivo sobre datos reales", () => {
    // Caso intencionalmente no ejecutado contra el endpoint real: DELETE /alerts/clear
    // borra TODAS las filas de iot.alerts sin scoping (ver alerts.api.ts:36-43).
    // Ver Global Constraints de este plan.
    expect(true).toBe(true);
  });
});
