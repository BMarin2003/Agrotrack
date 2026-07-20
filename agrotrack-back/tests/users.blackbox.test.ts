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
    let userId: number | null = null;

    try {
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
      userId = created.id;

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

      // Limpieza: soft-delete del usuario de prueba (establece enable=FALSE, sin remover el registro).
      const deleteRes = await routerApi.handle(
        new Request(`http://localhost/users/${userId}`, {
          method: "DELETE",
          headers: authHeaders(token),
        }),
      );
      expect(deleteRes.status).toBe(200);
      const deleted = await deleteRes.json();
      expect(deleted.ok).toBe(true);

      // Verificar que el soft-delete fué exitoso: el usuario debe aparecer en la lista con enable=false.
      const listRes = await routerApi.handle(
        new Request("http://localhost/users", { headers: authHeaders(token) }),
      );
      expect(listRes.status).toBe(200);
      const usersList = await listRes.json();
      const deletedUser = usersList.find((u: any) => u.id === userId);
      expect(deletedUser).toBeDefined();
      expect(deletedUser.enable).toBe(false);
    } finally {
      // Best-effort cleanup: asegurar que el usuario de prueba se elimine (soft-delete) incluso si alguna aserción falla.
      if (userId) {
        try {
          await routerApi.handle(
            new Request(`http://localhost/users/${userId}`, {
              method: "DELETE",
              headers: authHeaders(token),
            }),
          );
        } catch {
          // Ignore cleanup errors; they don't mask the original assertion failure.
        }
      }
    }
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
