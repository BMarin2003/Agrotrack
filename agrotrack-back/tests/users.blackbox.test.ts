// agrotrack-back/tests/users.blackbox.test.ts
import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

describe("Caja negra — GET /users", () => {
  it("admin puede listar usuarios (200, array)", async () => {
    const token = await getToken("admin");
    const res = await routerApi.handle(
      new Request("http://localhost/users", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
    expect(body.length).toBeGreaterThan(0);
  });

  it("operador NO tiene permiso admin.manage_users (403)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/users", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(403);
  });

  it("técnico NO tiene permiso admin.manage_users (403)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request("http://localhost/users", { headers: authHeaders(token) }),
    );
    expect(res.status).toBe(403);
  });

  it("sin token devuelve 401", async () => {
    const res = await routerApi.handle(new Request("http://localhost/users"));
    expect(res.status).toBe(401);
  });
});

describe("Caja negra — POST/PUT/DELETE /users (ciclo de vida completo)", () => {
  it("admin crea, edita, deshabilita y borra un usuario de prueba", async () => {
    const token = await getToken("admin");
    const uniqueEmail = `test-caja-negra-${Date.now()}@agrotrack.com`;

    const createRes = await routerApi.handle(
      new Request("http://localhost/users", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({
          names: "Usuario De Prueba Caja Negra",
          email: uniqueEmail,
          password: "PasswordTemporal123",
        }),
      }),
    );
    expect(createRes.status).toBe(200);
    const created = await createRes.json();
    expect(created.id).toBeDefined();
    const userId = created.id;

    const updateRes = await routerApi.handle(
      new Request(`http://localhost/users/${userId}`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ names: "Nombre Editado" }),
      }),
    );
    expect(updateRes.status).toBe(200);
    const updated = await updateRes.json();
    expect(updated.id).toBe(userId);

    const disableRes = await routerApi.handle(
      new Request(`http://localhost/users/${userId}`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ enable: false }),
      }),
    );
    expect(disableRes.status).toBe(200);

    // Limpieza: borrar el usuario de prueba para no dejar basura en la BD real.
    const deleteRes = await routerApi.handle(
      new Request(`http://localhost/users/${userId}`, {
        method: "DELETE",
        headers: authHeaders(token),
      }),
    );
    expect(deleteRes.status).toBe(200);
    const deleted = await deleteRes.json();
    expect(deleted.ok).toBe(true);
  });

  it("crear usuario con password menor a 8 caracteres devuelve error de validación (422)", async () => {
    const token = await getToken("admin");
    const res = await routerApi.handle(
      new Request("http://localhost/users", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ names: "X", email: "x@x.com", password: "corta" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});
