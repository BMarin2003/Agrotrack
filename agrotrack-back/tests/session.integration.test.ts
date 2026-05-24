import { describe, it, expect, beforeEach } from "bun:test";
import jwt from "jsonwebtoken";
import { createHash, randomUUID } from "crypto";
import { PERMISSIONS } from "../src/core/permissions.constants";

// ─── Constantes de test ───────────────────────────────────────────────────────

const SECRET   = "test_secret_agrotrack_integration";
const TTL      = 3600; // 1 hora en segundos
const TTL_MS   = TTL * 1000;

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeUser(overrides: Record<string, any> = {}) {
  return {
    id:    1,
    email: "operador@agrotrack.com",
    names: "Operador Test",
    roles: [{ id: 2, name: "Operador" }],
    ...overrides,
  };
}

function signToken(user: ReturnType<typeof makeUser>, ttl = TTL): string {
  // jwtid (jti) garantiza unicidad aunque el payload y el iat sean idénticos.
  // El sistema real genera tokens en instantes diferentes; en tests pueden
  // coincidir dentro del mismo segundo, produciendo el mismo hash en whitelist.
  return jwt.sign(
    { id: user.id, email: user.email, names: user.names, roles: user.roles },
    SECRET,
    { expiresIn: ttl, jwtid: randomUUID() },
  );
}

function verifyJwt(token: string): { payload: jwt.JwtPayload | null; error: string | null } {
  try {
    return { payload: jwt.verify(token, SECRET) as jwt.JwtPayload, error: null };
  } catch (e: any) {
    return { payload: null, error: e.message };
  }
}

// ─── Almacén de sesiones en memoria ──────────────────────────────────────────
// Replica exactamente la lógica de src/core/store.ts

class SessionStore {
  private whitelist   = new Map<string, number>();       // hash → expiry (ms)
  private permissions = new Map<string, Set<string>>(); // userId → permisos

  private hash(token: string) {
    return createHash("sha256").update(token).digest("hex");
  }

  // Equivale a userStore.addToken()
  addToken(token: string, ttlMs = TTL_MS): void {
    this.whitelist.set(this.hash(token), Date.now() + ttlMs);
  }

  // Equivale a userStore.removeToken()
  removeToken(token: string): void {
    this.whitelist.delete(this.hash(token));
  }

  // Equivale a userStore.isTokenValid()
  isTokenValid(token: string): boolean {
    const exp = this.whitelist.get(this.hash(token));
    if (!exp) return false;
    if (Date.now() > exp) {
      this.whitelist.delete(this.hash(token));
      return false;
    }
    return true;
  }

  // Equivale a userStore.setUserPermissions()
  setPermissions(userId: string, perms: string[]): void {
    this.permissions.set(userId, new Set(perms));
  }

  // Equivale a userStore.hasPermission()
  hasPermission(userId: string, perm: string): boolean {
    return this.permissions.get(userId)?.has(perm) ?? false;
  }

  // Simula el guard: valida JWT + whitelist (mirrors validateToken en jwt.ts)
  validateRequest(token: string): { ok: boolean; userId?: string; error?: string } {
    const { payload, error } = verifyJwt(token);
    if (error || !payload) return { ok: false, error: "JWT inválido" };
    if (!this.isTokenValid(token)) return { ok: false, error: "Token no está en whitelist" };
    return { ok: true, userId: String(payload.id) };
  }

  // Simula POST /auth/logout (solo remueve el token, permisos quedan en store)
  logout(token: string): void {
    this.removeToken(token);
  }

  // Simula POST /auth/verify-token (rota el token)
  rotateToken(oldToken: string, newUser: ReturnType<typeof makeUser>): string | null {
    if (!this.isTokenValid(oldToken)) return null;
    this.removeToken(oldToken);
    const newToken = signToken(newUser);
    this.addToken(newToken);
    this.setPermissions(String(newUser.id), [PERMISSIONS.iot.view_sensors]);
    return newToken;
  }

  activeTokenCount(): number {
    // Limpia expirados y cuenta
    for (const [hash, exp] of this.whitelist) {
      if (Date.now() > exp) this.whitelist.delete(hash);
    }
    return this.whitelist.size;
  }
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("Login — creación de sesión", () => {
  let store: SessionStore;

  beforeEach(() => {
    store = new SessionStore();
  });

  it("el token generado es un JWT válido", () => {
    const user  = makeUser();
    const token = signToken(user);
    const { error } = verifyJwt(token);
    expect(error).toBeNull();
  });

  it("después del login el token está en whitelist", () => {
    const token = signToken(makeUser());
    store.addToken(token);
    expect(store.isTokenValid(token)).toBe(true);
  });

  it("después del login los permisos del usuario están en caché", () => {
    const user  = makeUser();
    const token = signToken(user);
    store.addToken(token);
    store.setPermissions(String(user.id), [PERMISSIONS.iot.view_sensors, PERMISSIONS.iot.view_alerts]);
    expect(store.hasPermission(String(user.id), PERMISSIONS.iot.view_sensors)).toBe(true);
    expect(store.hasPermission(String(user.id), PERMISSIONS.iot.view_alerts)).toBe(true);
  });

  it("un request con token válido pasa la validación completa", () => {
    const token = signToken(makeUser());
    store.addToken(token);
    const result = store.validateRequest(token);
    expect(result.ok).toBe(true);
    expect(result.userId).toBe("1");
  });

  it("un request sin token en whitelist es rechazado aunque el JWT sea válido", () => {
    const token  = signToken(makeUser()); // JWT correcto pero nunca agregado a whitelist
    const result = store.validateRequest(token);
    expect(result.ok).toBe(false);
    expect(result.error).toMatch(/whitelist/i);
  });
});

describe("Logout — destrucción de sesión", () => {
  let store: SessionStore;
  let token: string;
  const user = makeUser();

  beforeEach(() => {
    store = new SessionStore();
    token = signToken(user);
    store.addToken(token);
    store.setPermissions(String(user.id), [PERMISSIONS.iot.view_sensors]);
  });

  it("después del logout el token no está en whitelist", () => {
    store.logout(token);
    expect(store.isTokenValid(token)).toBe(false);
  });

  it("después del logout los requests son rechazados", () => {
    store.logout(token);
    const result = store.validateRequest(token);
    expect(result.ok).toBe(false);
  });

  it("logout es idempotente — repetirlo no lanza error", () => {
    store.logout(token);
    expect(() => store.logout(token)).not.toThrow();
    expect(store.isTokenValid(token)).toBe(false);
  });

  it("los permisos quedan en caché tras el logout (el token invalida el acceso)", () => {
    // Diseño intencional: solo se limpia el token, no la caché de permisos.
    // El guard rechaza por token antes de llegar a la verificación de permisos.
    store.logout(token);
    expect(store.hasPermission(String(user.id), PERMISSIONS.iot.view_sensors)).toBe(true);
    expect(store.validateRequest(token).ok).toBe(false); // acceso bloqueado por token
  });

  it("logout de un usuario no afecta la sesión de otro", () => {
    const user2  = makeUser({ id: 2, email: "admin@agrotrack.com" });
    const token2 = signToken(user2);
    store.addToken(token2);

    store.logout(token);

    expect(store.isTokenValid(token)).toBe(false);
    expect(store.isTokenValid(token2)).toBe(true);
  });
});

describe("Replay attack — reuso de token revocado", () => {
  it("un token revocado no puede reutilizarse aunque el JWT no haya expirado", () => {
    const store = new SessionStore();
    const token = signToken(makeUser(), TTL); // JWT vigente por 1h
    store.addToken(token);

    // Verificamos que era válido antes del logout
    expect(store.validateRequest(token).ok).toBe(true);

    store.logout(token);

    // Replay: intentar usar el mismo token
    const { error: jwtError } = verifyJwt(token);
    expect(jwtError).toBeNull(); // el JWT sigue siendo criptográficamente válido...
    expect(store.validateRequest(token).ok).toBe(false); // ...pero la whitelist lo rechaza
  });

  it("un atacante con copia del token es bloqueado tras el logout legítimo", () => {
    const store           = new SessionStore();
    const token           = signToken(makeUser());
    const stolenToken     = token; // atacante copió el token

    store.addToken(token);
    store.logout(token); // usuario legítimo hace logout

    expect(store.validateRequest(stolenToken).ok).toBe(false);
  });
});

describe("Rotación de token — verify-token", () => {
  it("el token viejo queda inválido tras la rotación", () => {
    const store    = new SessionStore();
    const user     = makeUser();
    const oldToken = signToken(user);
    store.addToken(oldToken);

    const newToken = store.rotateToken(oldToken, user);

    expect(newToken).not.toBeNull();
    expect(store.isTokenValid(oldToken)).toBe(false);
  });

  it("el token nuevo es válido tras la rotación", () => {
    const store    = new SessionStore();
    const user     = makeUser();
    const oldToken = signToken(user);
    store.addToken(oldToken);

    const newToken = store.rotateToken(oldToken, user)!;

    expect(store.validateRequest(newToken).ok).toBe(true);
  });

  it("no se puede rotar un token que no está en whitelist", () => {
    const store    = new SessionStore();
    const user     = makeUser();
    const token    = signToken(user);
    // nunca se agrega a whitelist

    const result = store.rotateToken(token, user);
    expect(result).toBeNull();
  });

  it("no se puede rotar un token ya revocado", () => {
    const store = new SessionStore();
    const user  = makeUser();
    const token = signToken(user);
    store.addToken(token);
    store.logout(token);

    const result = store.rotateToken(token, user);
    expect(result).toBeNull();
  });

  it("la rotación no crea tokens duplicados en whitelist", () => {
    const store    = new SessionStore();
    const user     = makeUser();
    const oldToken = signToken(user);
    store.addToken(oldToken);

    const before = store.activeTokenCount();
    store.rotateToken(oldToken, user);
    const after = store.activeTokenCount();

    expect(after).toBe(before); // 1 viejo → 1 nuevo = mismo total
  });
});

describe("Sesiones concurrentes — mismo usuario, múltiples dispositivos", () => {
  it("un usuario puede tener dos tokens activos simultáneamente", () => {
    const store  = new SessionStore();
    const user   = makeUser();
    const tokenA = signToken(user);
    const tokenB = signToken(user);
    store.addToken(tokenA);
    store.addToken(tokenB);

    expect(store.isTokenValid(tokenA)).toBe(true);
    expect(store.isTokenValid(tokenB)).toBe(true);
  });

  it("logout de un dispositivo no invalida la sesión del otro", () => {
    const store  = new SessionStore();
    const user   = makeUser();
    const tokenA = signToken(user);
    const tokenB = signToken(user);
    store.addToken(tokenA);
    store.addToken(tokenB);

    store.logout(tokenA);

    expect(store.isTokenValid(tokenA)).toBe(false);
    expect(store.isTokenValid(tokenB)).toBe(true);
  });

  it("logout de todos los dispositivos invalida todas las sesiones", () => {
    const store  = new SessionStore();
    const user   = makeUser();
    const tokenA = signToken(user);
    const tokenB = signToken(user);
    const tokenC = signToken(user);
    store.addToken(tokenA);
    store.addToken(tokenB);
    store.addToken(tokenC);

    store.logout(tokenA);
    store.logout(tokenB);
    store.logout(tokenC);

    expect(store.activeTokenCount()).toBe(0);
  });
});

describe("Expiración de sesión", () => {
  it("token expirado en whitelist es rechazado automáticamente", () => {
    const store = new SessionStore();
    const token = signToken(makeUser());
    store.addToken(token, -1); // TTL en el pasado
    expect(store.isTokenValid(token)).toBe(false);
  });

  it("token expirado es eliminado de whitelist al validarse", () => {
    const store = new SessionStore();
    const token = signToken(makeUser());
    store.addToken(token, -1);
    store.isTokenValid(token); // dispara limpieza
    expect(store.activeTokenCount()).toBe(0);
  });

  it("JWT expirado es rechazado por verificación criptográfica antes de llegar a whitelist", () => {
    const store = new SessionStore();
    const token = signToken(makeUser(), -1); // JWT con exp en el pasado
    store.addToken(token); // está en whitelist

    const { error } = verifyJwt(token);
    expect(error).toMatch(/expired/i); // falla antes que la whitelist
  });

  it("sesión con TTL largo permanece válida durante su vigencia", () => {
    const store = new SessionStore();
    const token = signToken(makeUser(), 7200); // 2 horas
    store.addToken(token, 7200 * 1000);
    expect(store.isTokenValid(token)).toBe(true);
  });
});

describe("Ciclo de vida completo — login → uso → logout → rechazo", () => {
  it("flujo estándar de operador", () => {
    const store = new SessionStore();
    const user  = makeUser({ id: 10, roles: [{ id: 2, name: "Operador" }] });
    const perms = [PERMISSIONS.iot.view_sensors, PERMISSIONS.iot.view_alerts];

    // 1. Login
    const token = signToken(user);
    store.addToken(token);
    store.setPermissions(String(user.id), perms);

    // 2. Requests autorizados
    expect(store.validateRequest(token).ok).toBe(true);
    expect(store.hasPermission(String(user.id), PERMISSIONS.iot.view_sensors)).toBe(true);
    expect(store.hasPermission(String(user.id), PERMISSIONS.iot.resolve_alerts)).toBe(false);

    // 3. Logout
    store.logout(token);

    // 4. Requests rechazados
    expect(store.validateRequest(token).ok).toBe(false);
  });

  it("flujo completo de técnico con rotación de token", () => {
    const store = new SessionStore();
    const user  = makeUser({ id: 20, roles: [{ id: 3, name: "Técnico" }] });

    // Login
    const token1 = signToken(user);
    store.addToken(token1);
    store.setPermissions(String(user.id), Object.values(PERMISSIONS.iot));
    expect(store.validateRequest(token1).ok).toBe(true);

    // verify-token → rotación
    const token2 = store.rotateToken(token1, user)!;
    expect(store.validateRequest(token1).ok).toBe(false); // token viejo inválido
    expect(store.validateRequest(token2).ok).toBe(true);  // token nuevo válido

    // Logout con token rotado
    store.logout(token2);
    expect(store.validateRequest(token2).ok).toBe(false);
  });
});
