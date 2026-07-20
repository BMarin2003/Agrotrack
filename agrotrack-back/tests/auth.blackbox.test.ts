// agrotrack-back/tests/auth.blackbox.test.ts
import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

describe("Caja negra — POST /auth/login", () => {
  it("credenciales válidas devuelven 200 con user, token y expiresIn", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "operador@agrotrack.com", password: "Operador2024!" }),
      }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.user.email).toBe("operador@agrotrack.com");
    expect(typeof body.token).toBe("string");
    expect(typeof body.expiresIn).toBe("number");
  });

  it("contraseña incorrecta devuelve 401", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "operador@agrotrack.com", password: "contraseña_incorrecta" }),
      }),
    );
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.message).toBe("Credenciales inválidas");
  });

  it("email inexistente devuelve 401", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "no-existe-esta-cuenta@agrotrack.com", password: "cualquiera" }),
      }),
    );
    expect(res.status).toBe(401);
  });

  it("body sin password devuelve error de validación (422)", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "operador@agrotrack.com" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});

describe("Caja negra — POST /auth/verify-token", () => {
  it("token válido devuelve 200 con datos de usuario renovados", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/auth/verify-token", {
        method: "POST",
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.user.email).toBe("operador@agrotrack.com");
  });

  it("sin header Authorization devuelve error de validación (422, header requerido por schema)", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/verify-token", { method: "POST" }),
    );
    expect(res.status).toBe(422);
  });

  it("token con firma inválida devuelve 401", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/verify-token", {
        method: "POST",
        headers: { authorization: "Bearer token.invalido.falso" },
      }),
    );
    expect(res.status).toBe(401);
  });
});

describe("Caja negra — POST /auth/logout", () => {
  it("devuelve 200 aunque no se envíe token (logout es idempotente)", async () => {
    const res = await routerApi.handle(
      new Request("http://localhost/auth/logout", { method: "POST" }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.message).toBe("Logout exitoso");
  });

  it("tras logout, el token dejado de whitelist ya no autentica rutas protegidas", async () => {
    // Login independiente (no usa el cache de getToken, porque logout invalida ESTE token específico)
    const loginRes = await routerApi.handle(
      new Request("http://localhost/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "operador@agrotrack.com", password: "Operador2024!" }),
      }),
    );
    const { token } = await loginRes.json();

    await routerApi.handle(
      new Request("http://localhost/auth/logout", {
        method: "POST",
        headers: authHeaders(token),
      }),
    );

    const protectedRes = await routerApi.handle(
      new Request("http://localhost/sensors/gateways", { headers: authHeaders(token) }),
    );
    expect(protectedRes.status).toBe(401);
  });
});

describe("Caja negra — PUT /auth/update-password", () => {
  it("contraseña actual incorrecta devuelve 401", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/auth/update-password", {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ currentPassword: "esto_esta_mal", newPassword: "NuevaClaveSegura123" }),
      }),
    );
    expect(res.status).toBe(401);
  });

  it("nueva contraseña menor a 8 caracteres devuelve error de validación (422)", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/auth/update-password", {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ currentPassword: "Operador2024!", newPassword: "corta" }),
      }),
    );
    expect(res.status).toBe(422);
  });
});
