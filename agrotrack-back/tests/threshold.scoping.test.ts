import { describe, it, expect } from "bun:test";

// ─── Regla de visibilidad de umbrales/alertas por operador (replica thresholds.api.ts/alerts.api.ts)
function resolveUserIdFilter(isAdmin: boolean, currentUserId: number): number | null {
  return isAdmin ? null : currentUserId;
}

describe("Scoping por operador — filtro de user_id", () => {
  it("Admin no recibe filtro (ve todo)", () => {
    expect(resolveUserIdFilter(true, 7)).toBeNull();
  });

  it("Operador recibe su propio id como filtro", () => {
    expect(resolveUserIdFilter(false, 7)).toBe(7);
  });

  it("Técnico (no admin) también recibe su propio id como filtro", () => {
    expect(resolveUserIdFilter(false, 42)).toBe(42);
  });
});

// ─── Regla de visibilidad de una alerta ya guardada (replica el WHERE de iot.get_active_alerts)
function isAlertVisibleTo(alertUserId: number | null, viewerUserId: number, viewerIsAdmin: boolean): boolean {
  if (viewerIsAdmin) return true;
  if (alertUserId === null) return true; // alerta de sistema, visible para todos
  return alertUserId === viewerUserId;
}

describe("Visibilidad de alertas — privadas por operador, públicas si son de sistema", () => {
  it("alerta de sistema (user_id null) es visible para cualquier operador", () => {
    expect(isAlertVisibleTo(null, 7, false)).toBe(true);
  });

  it("alerta de un umbral propio es visible para su dueño", () => {
    expect(isAlertVisibleTo(7, 7, false)).toBe(true);
  });

  it("alerta de un umbral ajeno NO es visible para otro operador", () => {
    expect(isAlertVisibleTo(7, 9, false)).toBe(false);
  });

  it("Admin ve alertas de cualquier operador", () => {
    expect(isAlertVisibleTo(7, 9, true)).toBe(true);
  });
});
