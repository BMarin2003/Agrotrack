---
title: Seguridad y control de acceso
description: Modelo RBAC compartido entre backend y app.
sidebar:
  order: 4
---

Ambos extremos comparten el mismo modelo RBAC definido en el backend (`core.roles`/`core.permissions`/`core.role_permissions`):

- **4 roles**: Administrador (acceso total), Operador (monitoreo, umbrales, reportes), Técnico (gestión completa de gateways/sensores/calibración), Auditor (solo lectura).
- Cada ruta HTTP declara el permiso que requiere (`requirePermission: PERMISSIONS.iot.view_reports`), verificado por el plugin `authPlugin` antes de llegar al handler.
- Recursos con dueño (umbrales) se resuelven por `user_id` extraído del JWT en el propio backend — el cliente nunca puede pasar un `user_id` arbitrario en el body, evitando suplantación.
- La app Android adapta la navegación disponible según el rol (`SessionManager.currentRole()`), ocultando accesos a módulos que el usuario no puede usar, como capa adicional de UX sobre el control de acceso real (que siempre se aplica en el backend, no solo en el cliente).
