import { Elysia } from 'elysia';
import { validateToken } from '@core/jwt';
import { userStore } from '@core/store';
import { PermisoSlug } from '@core/permisos.type';

export const authPlugin = new Elysia({ name: 'auth-plugin' })
  .derive({ as: 'global' }, async ({ headers, cookie }) => {
    const user = await validateToken(headers, cookie);
    return { user };
  })
  .macro({
    requirePermission(value: PermisoSlug | PermisoSlug[]) {
      return {
        async beforeHandle({ user, set }) {
          if (user?.error) { set.status = 401; return { message: user.error }; }

          if (!value) return;

          if (user?.isAdmin) return;

          const permissions = Array.isArray(value) ? value : [value];
          let hasAccess = false;

          for (const perm of permissions as string[]) {
            if (await userStore.hasPermission(user.id, perm as any)) {
              hasAccess = true;
              break;
            }
          }

          if (!hasAccess) {
            set.status = 403;
            return { message: 'No tienes los permisos necesarios para realizar esta acción' };
          }
        },
      };
    },
  });
