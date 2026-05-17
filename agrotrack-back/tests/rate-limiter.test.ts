import { describe, it, expect, beforeEach } from "bun:test";
import { RateLimiter } from "../src/core/rate-limiter";

describe("RateLimiter", () => {
  let limiter: RateLimiter;

  beforeEach(() => {
    limiter = new RateLimiter(3, 1000, 2000);
  });

  it("permite el primer intento", () => {
    const r = limiter.check("key1");
    expect(r.allowed).toBe(true);
    expect(r.remaining).toBe(3);
  });

  it("remaining decrece con cada intento fallido registrado", () => {
    limiter.record("key1");
    expect(limiter.check("key1").remaining).toBe(2);
    limiter.record("key1");
    expect(limiter.check("key1").remaining).toBe(1);
  });

  it("bloquea en el intento número maxAttempts + 1", () => {
    limiter.record("key1");
    limiter.record("key1");
    limiter.record("key1");
    const r = limiter.check("key1");
    expect(r.allowed).toBe(false);
    expect(r.retryAfter).toBeGreaterThan(0);
  });

  it("retryAfter refleja el tiempo de lockout en segundos", () => {
    for (let i = 0; i < 3; i++) limiter.record("key2");
    const r = limiter.check("key2");
    expect(r.allowed).toBe(false);
    expect(r.retryAfter).toBeLessThanOrEqual(2);
    expect(r.retryAfter).toBeGreaterThan(0);
  });

  it("claves distintas son independientes", () => {
    for (let i = 0; i < 3; i++) limiter.record("email-a@x.com");
    expect(limiter.check("email-a@x.com").allowed).toBe(false);
    expect(limiter.check("email-b@x.com").allowed).toBe(true);
  });

  it("reset libera la clave bloqueada", () => {
    for (let i = 0; i < 3; i++) limiter.record("key3");
    expect(limiter.check("key3").allowed).toBe(false);
    limiter.reset("key3");
    expect(limiter.check("key3").allowed).toBe(true);
  });

  it("sigue bloqueado dentro del periodo de lockout", () => {
    for (let i = 0; i < 3; i++) limiter.record("key4");
    const first = limiter.check("key4");
    const second = limiter.check("key4");
    expect(first.allowed).toBe(false);
    expect(second.allowed).toBe(false);
  });

  it("permite de nuevo tras expirar la ventana", async () => {
    const fast = new RateLimiter(2, 100, 200);
    fast.record("k");
    fast.record("k");
    expect(fast.check("k").allowed).toBe(false);
    await new Promise((r) => setTimeout(r, 250));
    expect(fast.check("k").allowed).toBe(true);
  });

  it("simula ataque de fuerza bruta (50 intentos)", () => {
    for (let i = 0; i < 50; i++) {
      const r = limiter.check("brute");
      if (r.allowed) limiter.record("brute");
    }
    expect(limiter.check("brute").allowed).toBe(false);
  });
});
