import { routerApi } from "../../src/router";

const CREDENTIALS = {
  operador: { email: "operador@agrotrack.com", password: "Operador2024!" },
  tecnico:  { email: "tecnico@agrotrack.com",  password: "Tecnico2024!" },
  admin:    { email: "admin@agrotrack.com",    password: "Admin2024!" },
} as const;

export type TestRole = keyof typeof CREDENTIALS;

const tokenCache = new Map<TestRole, string>();

/** Login real contra Supabase con una de las 3 cuentas sembradas.
 *  Cachea el token por rol para no reloguear en cada test. */
export async function getToken(role: TestRole): Promise<string> {
  const cached = tokenCache.get(role);
  if (cached) return cached;

  const res = await routerApi.handle(
    new Request("http://localhost/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(CREDENTIALS[role]),
    }),
  );

  if (res.status !== 200) {
    throw new Error(`No se pudo autenticar como ${role}: HTTP ${res.status}`);
  }

  const body = await res.json();
  tokenCache.set(role, body.token);
  return body.token;
}

export function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}
