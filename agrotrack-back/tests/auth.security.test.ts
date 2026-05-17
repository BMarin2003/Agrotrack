import { describe, it, expect, beforeEach } from "bun:test";
import jwt from "jsonwebtoken";
import { createHash } from "crypto";

const TEST_SECRET = "test_secret_agrotrack_2024";
const WRONG_SECRET = "wrong_secret_hacker";
const EXPIRES_IN = 3600; // 1 hora

function makePayload(overrides: Record<string, any> = {}) {
  return {
    id: 1,
    email: "operador@agrotrack.com",
    names: "Operador Test",
    roles: [{ id: 2, name: "Operador" }],
    ...overrides,
  };
}

function sign(
  payload: Record<string, any>,
  secret = TEST_SECRET,
  options: jwt.SignOptions = {},
) {
  return jwt.sign(payload, secret, { expiresIn: EXPIRES_IN, ...options });
}

function verify(token: string, secret = TEST_SECRET) {
  try {
    return {
      payload: jwt.verify(token, secret) as jwt.JwtPayload,
      error: null,
    };
  } catch (e: any) {
    return { payload: null, error: e.message as string };
  }
}

class TestStore {
  private whitelist = new Map<string, number>();

  add(token: string, ttlSeconds = EXPIRES_IN) {
    const hash = createHash("sha256").update(token).digest("hex");
    this.whitelist.set(hash, Date.now() + ttlSeconds * 1000);
  }

  remove(token: string) {
    const hash = createHash("sha256").update(token).digest("hex");
    this.whitelist.delete(hash);
  }

  isValid(token: string): boolean {
    const hash = createHash("sha256").update(token).digest("hex");
    const exp = this.whitelist.get(hash);
    if (!exp) return false;
    if (Date.now() > exp) {
      this.whitelist.delete(hash);
      return false;
    }
    return true;
  }
}

describe("JWT — generación y verificación", () => {
  it("firma y verifica un token válido", () => {
    const token = sign(makePayload());
    const { payload, error } = verify(token);
    expect(error).toBeNull();
    expect(payload?.email).toBe("operador@agrotrack.com");
    expect(payload?.id).toBe(1);
  });

  it("contiene los campos requeridos (id, email, roles)", () => {
    const token = sign(makePayload());
    const { payload } = verify(token);
    expect(payload).toHaveProperty("id");
    expect(payload).toHaveProperty("email");
    expect(payload).toHaveProperty("roles");
    expect(Array.isArray(payload?.roles)).toBe(true);
  });

  it("incluye exp (unix timestamp de expiración)", () => {
    const before = Math.floor(Date.now() / 1000);
    const token = sign(makePayload());
    const { payload } = verify(token);
    expect(payload?.exp).toBeGreaterThan(before);
    expect(payload?.exp).toBeLessThanOrEqual(before + EXPIRES_IN + 2);
  });
});

describe("JWT — tokens manipulados y falsificados", () => {
  it("rechaza token con payload manipulado (firma inválida)", () => {
    const token = sign(makePayload());
    const parts = token.split(".");
    const fakePayload = Buffer.from(
      JSON.stringify({ id: 999, email: "hacker@evil.com", roles: [] }),
    ).toString("base64url");
    const tampered = `${parts[0]}.${fakePayload}.${parts[2]}`;
    const { error } = verify(tampered);
    expect(error).not.toBeNull();
    expect(error).toMatch(/invalid signature/i);
  });

  it("rechaza token firmado con clave incorrecta", () => {
    const token = sign(makePayload(), WRONG_SECRET);
    const { error } = verify(token, TEST_SECRET);
    expect(error).not.toBeNull();
    expect(error).toMatch(/invalid signature/i);
  });

  it("rechaza token con algoritmo none (ataque de degradación)", () => {
    const header = Buffer.from(
      JSON.stringify({ alg: "none", typ: "JWT" }),
    ).toString("base64url");
    const payload = Buffer.from(JSON.stringify(makePayload())).toString(
      "base64url",
    );
    const noneToken = `${header}.${payload}.`;
    const { error } = verify(noneToken);
    expect(error).not.toBeNull();
  });

  it("rechaza token con header modificado", () => {
    const token = sign(makePayload());
    const parts = token.split(".");
    const fakeHeader = Buffer.from(
      JSON.stringify({ alg: "HS256", typ: "JWT", kid: "attacker" }),
    ).toString("base64url");
    const tampered = `${fakeHeader}.${parts[1]}.${parts[2]}`;
    const { error } = verify(tampered);
    expect(error).not.toBeNull();
  });

  it("rechaza string aleatorio como token", () => {
    const { error } = verify("eyJhbGciOiJIUzI1NiJ9.abc.xyz");
    expect(error).not.toBeNull();
  });

  it("rechaza token vacío", () => {
    const { error } = verify("");
    expect(error).not.toBeNull();
  });
});

describe("JWT — expiración", () => {
  it("rechaza token expirado", () => {
    const token = sign(makePayload(), TEST_SECRET, { expiresIn: -1 });
    const { error } = verify(token);
    expect(error).not.toBeNull();
    expect(error).toMatch(/expired/i);
  });

  it("acepta token recién emitido (no expirado)", () => {
    const token = sign(makePayload(), TEST_SECRET, { expiresIn: 60 });
    const { error } = verify(token);
    expect(error).toBeNull();
  });
});

describe("Token whitelist — revocación tras logout", () => {
  let store: TestStore;

  beforeEach(() => {
    store = new TestStore();
  });

  it("token activo está en whitelist", () => {
    const token = sign(makePayload());
    store.add(token);
    expect(store.isValid(token)).toBe(true);
  });

  it("token removido (logout) ya no es válido", () => {
    const token = sign(makePayload());
    store.add(token);
    store.remove(token);
    expect(store.isValid(token)).toBe(false);
  });

  it("token nunca agregado a la whitelist es inválido", () => {
    const token = sign(makePayload());
    expect(store.isValid(token)).toBe(false);
  });

  it("token distinto no afecta a otro token en whitelist", () => {
    const tokenA = sign(makePayload({ email: "a@test.com" }));
    const tokenB = sign(makePayload({ email: "b@test.com" }));
    store.add(tokenA);
    store.remove(tokenB); // logout de B no afecta a A
    expect(store.isValid(tokenA)).toBe(true);
  });

  it("token expirado en whitelist es rechazado automáticamente", async () => {
    const token = sign(makePayload());
    store.add(token, -1); // ttl en el pasado
    expect(store.isValid(token)).toBe(false);
  });

  it("token manipulado no coincide con hash original en whitelist", () => {
    const token = sign(makePayload());
    store.add(token);
    const parts = token.split(".");
    const fakePayload = Buffer.from(JSON.stringify({ id: 999 })).toString(
      "base64url",
    );
    const tampered = `${parts[0]}.${fakePayload}.${parts[2]}`;
    expect(store.isValid(tampered)).toBe(false);
  });
});

describe("Escenarios de ataque combinados", () => {
  it("token válido + firma incorrecta es rechazado antes de llegar a whitelist", () => {
    const token = sign(makePayload(), WRONG_SECRET);
    const { error } = verify(token, TEST_SECRET);
    expect(error).not.toBeNull();
  });

  it("replay de token revocado falla en whitelist aunque JWT siga vigente", () => {
    const store = new TestStore();
    const token = sign(makePayload(), TEST_SECRET, { expiresIn: 3600 });
    store.add(token);

    const { error } = verify(token);
    expect(error).toBeNull();

    store.remove(token);
    expect(store.isValid(token)).toBe(false);
  });
});
