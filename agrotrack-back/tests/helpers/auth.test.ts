import { describe, it, expect } from "bun:test";
import { getToken, authHeaders } from "./auth";
import { routerApi } from "../../src/router";

describe("Helper de autenticación real", () => {
  it("obtiene un token válido para operador y lo reusa (cache)", async () => {
    const token1 = await getToken("operador");
    const token2 = await getToken("operador");
    expect(token1).toBe(token2);
    expect(typeof token1).toBe("string");
    expect(token1.split(".").length).toBe(3); // forma de un JWT
  });

  it("el token obtenido autentica contra una ruta protegida real", async () => {
    const token = await getToken("operador");
    const res = await routerApi.handle(
      new Request("http://localhost/sensors/gateways", {
        headers: authHeaders(token),
      }),
    );
    expect(res.status).toBe(200);
  });

  it("obtiene tokens distintos para roles distintos", async () => {
    const opToken = await getToken("operador");
    const adminToken = await getToken("admin");
    expect(opToken).not.toBe(adminToken);
  });
});
