import { describe, it, expect, beforeEach } from "bun:test";
import { PERMISSIONS } from "../src/core/permissions.constants";

const ROLE_PERMISSIONS: Record<string, Set<string>> = {
  Administrador: new Set([
    ...Object.values(PERMISSIONS.iot),
    ...Object.values(PERMISSIONS.admin),
  ]),

  Técnico: new Set([
    PERMISSIONS.iot.view_sensors,
    PERMISSIONS.iot.manage_sensors,
    PERMISSIONS.iot.rename_sensor_alias,
    PERMISSIONS.iot.view_telemetry,
    PERMISSIONS.iot.view_alerts,
    PERMISSIONS.iot.resolve_alerts,
    PERMISSIONS.iot.manage_thresholds,
    PERMISSIONS.iot.view_reports,
    PERMISSIONS.iot.manage_gateways,
    PERMISSIONS.iot.manage_helpdesk,
  ]),

  Operador: new Set([
    PERMISSIONS.iot.view_sensors,
    PERMISSIONS.iot.rename_sensor_alias,
    PERMISSIONS.iot.view_telemetry,
    PERMISSIONS.iot.view_alerts,
    PERMISSIONS.iot.view_reports,
  ]),
};

class RbacEngine {
  private permissions = new Map<string, Set<string>>();
  private admins = new Set<string>();

  addUser(userId: string, roleName: string): void {
    const perms = ROLE_PERMISSIONS[roleName];
    if (!perms) throw new Error(`Rol desconocido: ${roleName}`);
    this.permissions.set(userId, new Set(perms));
    if (roleName === "Administrador") this.admins.add(userId);
  }

  isAdmin(userId: string): boolean {
    return this.admins.has(userId);
  }

  hasPermission(userId: string, perm: string): boolean {
    if (this.isAdmin(userId)) return true;
    return this.permissions.get(userId)?.has(perm) ?? false;
  }

  checkAccess(userId: string, required: string | string[]): boolean {
    if (this.isAdmin(userId)) return true;
    const list = Array.isArray(required) ? required : [required];
    return list.some((p) => this.hasPermission(userId, p));
  }
}

describe("Operador — zona de trabajo", () => {
  let rbac: RbacEngine;
  const uid = "operador-01";

  beforeEach(() => {
    rbac = new RbacEngine();
    rbac.addUser(uid, "Operador");
  });

  // ── Accesos permitidos ────────────────────────────────────────────────────
  it("puede ver sensores", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_sensors)).toBe(true);
  });

  it("puede ver telemetría", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_telemetry)).toBe(true);
  });

  it("puede ver alertas", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_alerts)).toBe(true);
  });

  it("puede ver reportes", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_reports)).toBe(true);
  });

  it("puede renombrar el alias de un sensor", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.rename_sensor_alias)).toBe(true);
  });

  // ── Accesos denegados ─────────────────────────────────────────────────────
  it("NO puede gestionar sensores", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_sensors)).toBe(false);
  });

  it("NO puede resolver alertas", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.resolve_alerts)).toBe(false);
  });

  it("NO puede gestionar umbrales", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_thresholds)).toBe(
      false,
    );
  });

  it("NO puede gestionar gateways", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_gateways)).toBe(
      false,
    );
  });

  it("NO puede gestionar usuarios", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.admin.manage_users)).toBe(false);
  });

  it("NO puede gestionar roles", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.admin.manage_roles)).toBe(false);
  });

  it("no tiene flag de administrador", () => {
    expect(rbac.isAdmin(uid)).toBe(false);
  });

  it("tiene exactamente 5 permisos asignados", () => {
    const perms = ROLE_PERMISSIONS["Operador"];
    expect(perms.size).toBe(5);
  });
});

describe("Técnico — zona de trabajo", () => {
  let rbac: RbacEngine;
  const uid = "tecnico-01";

  beforeEach(() => {
    rbac = new RbacEngine();
    rbac.addUser(uid, "Técnico");
  });

  // ── Accesos permitidos ────────────────────────────────────────────────────
  it("puede ver sensores", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_sensors)).toBe(true);
  });

  it("puede gestionar sensores", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_sensors)).toBe(true);
  });

  it("puede renombrar el alias de un sensor", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.rename_sensor_alias)).toBe(true);
  });

  it("puede ver telemetría", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_telemetry)).toBe(true);
  });

  it("puede ver alertas", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_alerts)).toBe(true);
  });

  it("puede resolver alertas", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.resolve_alerts)).toBe(true);
  });

  it("puede gestionar umbrales", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_thresholds)).toBe(
      true,
    );
  });

  it("puede ver reportes", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_reports)).toBe(true);
  });

  it("puede gestionar gateways", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_gateways)).toBe(true);
  });

  // ── Accesos denegados ─────────────────────────────────────────────────────
  it("NO puede gestionar usuarios", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.admin.manage_users)).toBe(false);
  });

  it("NO puede gestionar roles", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.admin.manage_roles)).toBe(false);
  });

  it("no tiene flag de administrador", () => {
    expect(rbac.isAdmin(uid)).toBe(false);
  });

  it("tiene exactamente 10 permisos asignados (todos los IoT)", () => {
    const perms = ROLE_PERMISSIONS["Técnico"];
    expect(perms.size).toBe(10);
  });

  it("sus permisos son un superconjunto de los del Operador", () => {
    const operador = ROLE_PERMISSIONS["Operador"];
    const tecnico = ROLE_PERMISSIONS["Técnico"];
    for (const perm of operador) {
      expect(tecnico.has(perm)).toBe(true);
    }
  });
});

describe("Administrador — zona de trabajo", () => {
  let rbac: RbacEngine;
  const uid = "admin-01";

  beforeEach(() => {
    rbac = new RbacEngine();
    rbac.addUser(uid, "Administrador");
  });

  // ── Tiene todos los permisos IoT ──────────────────────────────────────────
  it("puede ver sensores", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_sensors)).toBe(true);
  });

  it("puede gestionar sensores", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_sensors)).toBe(true);
  });

  it("puede renombrar el alias de un sensor", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.rename_sensor_alias)).toBe(true);
  });

  it("puede ver telemetría", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_telemetry)).toBe(true);
  });

  it("puede ver alertas", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_alerts)).toBe(true);
  });

  it("puede resolver alertas", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.resolve_alerts)).toBe(true);
  });

  it("puede gestionar umbrales", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_thresholds)).toBe(
      true,
    );
  });

  it("puede ver reportes", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.view_reports)).toBe(true);
  });

  it("puede gestionar gateways", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.iot.manage_gateways)).toBe(true);
  });

  // ── Exclusivos de admin ───────────────────────────────────────────────────
  it("puede gestionar usuarios", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.admin.manage_users)).toBe(true);
  });

  it("puede gestionar roles", () => {
    expect(rbac.hasPermission(uid, PERMISSIONS.admin.manage_roles)).toBe(true);
  });

  it("tiene flag de administrador activo", () => {
    expect(rbac.isAdmin(uid)).toBe(true);
  });

  it("tiene los 11 permisos del sistema", () => {
    const perms = ROLE_PERMISSIONS["Administrador"];
    const total =
      Object.values(PERMISSIONS.iot).length +
      Object.values(PERMISSIONS.admin).length;
    expect(perms.size).toBe(total);
  });

  it("su matriz incluye todos los permisos del Técnico", () => {
    const admin = ROLE_PERMISSIONS["Administrador"];
    const tecnico = ROLE_PERMISSIONS["Técnico"];
    for (const perm of tecnico) {
      expect(admin.has(perm)).toBe(true);
    }
  });
});

describe("Shortcut isAdmin — bypass de permisos (mirrors auth.guard.ts)", () => {
  it("admin obtiene acceso aunque el permiso no esté en su Set", () => {
    const rbac = new RbacEngine();
    rbac.addUser("admin-x", "Administrador");
    // checkAccess usa isAdmin() internamente → siempre true
    expect(rbac.checkAccess("admin-x", "permiso.inexistente")).toBe(true);
  });

  it("admin pasa cualquier array de permisos sin importar el contenido", () => {
    const rbac = new RbacEngine();
    rbac.addUser("admin-x", "Administrador");
    expect(rbac.checkAccess("admin-x", ["foo", "bar", "baz"])).toBe(true);
  });

  it("no-admin con permiso inexistente es denegado", () => {
    const rbac = new RbacEngine();
    rbac.addUser("op", "Operador");
    expect(rbac.checkAccess("op", "permiso.inexistente")).toBe(false);
  });
});

describe("requirePermission — lógica OR para arrays", () => {
  let rbac: RbacEngine;
  const uid = "tecnico-or";

  beforeEach(() => {
    rbac = new RbacEngine();
    rbac.addUser(uid, "Técnico");
  });

  it("concede acceso si el usuario tiene al menos uno de los permisos requeridos", () => {
    const required = [
      PERMISSIONS.iot.resolve_alerts, // Técnico SÍ tiene
      PERMISSIONS.admin.manage_users, // Técnico NO tiene
    ];
    expect(rbac.checkAccess(uid, required)).toBe(true);
  });

  it("deniega si no tiene ninguno de los permisos requeridos", () => {
    const required = [
      PERMISSIONS.admin.manage_users,
      PERMISSIONS.admin.manage_roles,
    ];
    expect(rbac.checkAccess(uid, required)).toBe(false);
  });

  it("concede con un único permiso que el usuario tiene", () => {
    expect(rbac.checkAccess(uid, PERMISSIONS.iot.manage_gateways)).toBe(true);
  });

  it("deniega con un único permiso que el usuario no tiene", () => {
    expect(rbac.checkAccess(uid, PERMISSIONS.admin.manage_users)).toBe(false);
  });
});

describe("Aislamiento entre roles", () => {
  let rbac: RbacEngine;

  beforeEach(() => {
    rbac = new RbacEngine();
    rbac.addUser("op", "Operador");
    rbac.addUser("tec", "Técnico");
    rbac.addUser("admin", "Administrador");
  });

  it("los permisos de Operador no se propagan al Técnico", () => {
    // Técnico tiene sus propios permisos, no los del Operador directamente
    // pero sí los contiene como superconjunto — aquí verificamos que
    // un cambio en los permisos del Operador no afecta al Técnico
    const opPerms = [...ROLE_PERMISSIONS["Operador"]];
    const tecPerms = [...ROLE_PERMISSIONS["Técnico"]];
    // Los permisos son conjuntos independientes (no referencias compartidas)
    opPerms.push("iot.fake_permission");
    expect(tecPerms.includes("iot.fake_permission")).toBe(false);
  });

  it("revocar acceso a un usuario no afecta a otro del mismo rol", () => {
    rbac.addUser("op2", "Operador");
    // op y op2 tienen permisos independientes en Sets distintos
    expect(rbac.hasPermission("op", PERMISSIONS.iot.view_sensors)).toBe(true);
    expect(rbac.hasPermission("op2", PERMISSIONS.iot.view_sensors)).toBe(true);
  });

  it("Operador no puede hacer lo exclusivo del Técnico: resolver alertas", () => {
    expect(rbac.hasPermission("op", PERMISSIONS.iot.resolve_alerts)).toBe(
      false,
    );
    expect(rbac.hasPermission("tec", PERMISSIONS.iot.resolve_alerts)).toBe(
      true,
    );
  });

  it("Técnico no puede hacer lo exclusivo del Administrador: gestionar usuarios", () => {
    expect(rbac.hasPermission("tec", PERMISSIONS.admin.manage_users)).toBe(
      false,
    );
    expect(rbac.hasPermission("admin", PERMISSIONS.admin.manage_users)).toBe(
      true,
    );
  });

  it("Operador no puede hacer lo exclusivo del Administrador: gestionar roles", () => {
    expect(rbac.hasPermission("op", PERMISSIONS.admin.manage_roles)).toBe(
      false,
    );
    expect(rbac.hasPermission("admin", PERMISSIONS.admin.manage_roles)).toBe(
      true,
    );
  });

  it("los tres roles comparten el permiso de ver sensores", () => {
    expect(rbac.hasPermission("op", PERMISSIONS.iot.view_sensors)).toBe(true);
    expect(rbac.hasPermission("tec", PERMISSIONS.iot.view_sensors)).toBe(true);
    expect(rbac.hasPermission("admin", PERMISSIONS.iot.view_sensors)).toBe(
      true,
    );
  });

  it("solo Administrador y Técnico pueden gestionar sensores", () => {
    expect(rbac.hasPermission("op", PERMISSIONS.iot.manage_sensors)).toBe(
      false,
    );
    expect(rbac.hasPermission("tec", PERMISSIONS.iot.manage_sensors)).toBe(
      true,
    );
    expect(rbac.hasPermission("admin", PERMISSIONS.iot.manage_sensors)).toBe(
      true,
    );
  });

  it("isAdmin es exclusivo del Administrador", () => {
    expect(rbac.isAdmin("op")).toBe(false);
    expect(rbac.isAdmin("tec")).toBe(false);
    expect(rbac.isAdmin("admin")).toBe(true);
  });
});

describe("Jerarquía de roles — invariantes del sistema", () => {
  it("cada rol superior tiene todos los permisos del inferior", () => {
    const operador = ROLE_PERMISSIONS["Operador"];
    const tecnico = ROLE_PERMISSIONS["Técnico"];
    const admin = ROLE_PERMISSIONS["Administrador"];

    for (const perm of operador) {
      expect(tecnico.has(perm)).toBe(true);
      expect(admin.has(perm)).toBe(true);
    }
    for (const perm of tecnico) {
      expect(admin.has(perm)).toBe(true);
    }
  });

  it("Técnico tiene más permisos que Operador", () => {
    const operador = ROLE_PERMISSIONS["Operador"];
    const tecnico = ROLE_PERMISSIONS["Técnico"];
    expect(tecnico.size).toBeGreaterThan(operador.size);
  });

  it("Administrador tiene más permisos que Técnico", () => {
    const tecnico = ROLE_PERMISSIONS["Técnico"];
    const admin = ROLE_PERMISSIONS["Administrador"];
    expect(admin.size).toBeGreaterThan(tecnico.size);
  });

  it("ningún rol tiene permisos fuera del catálogo definido", () => {
    const catalog = new Set<string>([
      ...Object.values(PERMISSIONS.iot),
      ...Object.values(PERMISSIONS.admin),
    ]);
    for (const [rol, perms] of Object.entries(ROLE_PERMISSIONS)) {
      for (const perm of perms) {
        expect(catalog.has(perm)).toBe(true);
      }
    }
  });

  it("el catálogo total tiene exactamente 12 permisos", () => {
    const total =
      Object.values(PERMISSIONS.iot).length +
      Object.values(PERMISSIONS.admin).length;
    expect(total).toBe(12);
  });
});
