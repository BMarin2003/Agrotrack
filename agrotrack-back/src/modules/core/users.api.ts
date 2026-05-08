import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/users';

export const UsersApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .get('/', async ({ set }) => {
      const result = await execProcedure('core.list_users', []);
      if (result.error) { set.status = 500; return { message: result.error }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.admin.manage_users })

    .post('/', async ({ body, set }) => {
      const data = body as any;
      data.password_hash = Bun.password.hashSync(data.password);
      delete data.password;
      const result = await execProcedure('core.save_user', [data]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, {
      requirePermission: PERMISSIONS.admin.manage_users,
      body: t.Object({
        names: t.String(),
        email: t.String(),
        password: t.String({ minLength: 8 }),
        role_id: t.Optional(t.Number()),
      }),
    })

    .put('/:id', async ({ params, body, set }) => {
      const result = await execProcedure('core.save_user', [{ id: parseInt(params.id), ...(body as any) }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, {
      requirePermission: PERMISSIONS.admin.manage_users,
      body: t.Object({
        names: t.Optional(t.String()),
        email: t.Optional(t.String()),
        role_id: t.Optional(t.Number()),
        enable: t.Optional(t.Boolean()),
      }),
    })

    .delete('/:id', async ({ params, set }) => {
      const result = await execProcedure('core.delete_user', [{ id: parseInt(params.id) }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.admin.manage_users })
  );
