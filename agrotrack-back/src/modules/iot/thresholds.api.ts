import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/thresholds';

export const ThresholdsApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .get('/', async ({ query, user, set }) => {
      const userId = (user as any).isAdmin ? null : (user as any).id;
      const result = await execProcedure('iot.list_thresholds', [{
        sensor_id: query.sensor_id || null,
        user_id: userId,
      }]);
      if (result.error) { set.status = 500; return { message: result.error }; }
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_thresholds,
      query: t.Object({ sensor_id: t.Optional(t.String()) }),
    })

    .post('/', async ({ body, user, set }) => {
      const result = await execProcedure('iot.upsert_threshold', [{
        ...(body as any),
        user_id: (user as any).id,
      }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_thresholds,
      body: t.Object({
        sensor_id: t.Number(),
        metric: t.String(),
        min_value: t.Optional(t.Nullable(t.Number())),
        max_value: t.Optional(t.Nullable(t.Number())),
        alert_message: t.Optional(t.String()),
        enable: t.Optional(t.Boolean()),
      }),
    })

    .delete('/:id', async ({ params, user, set }) => {
      const userId = (user as any).isAdmin ? null : (user as any).id;
      const result = await execProcedure('iot.delete_threshold', [{ id: parseInt(params.id), user_id: userId }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.manage_thresholds })
  );
