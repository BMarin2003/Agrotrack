import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/thresholds';

export const ThresholdsApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .get('/', async ({ query, status }) => {
      const result = await execProcedure('iot.list_thresholds', [{ sensor_id: query.sensor_id || null }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_thresholds,
      query: t.Object({ sensor_id: t.Optional(t.String()) }),
    })

    .post('/', async ({ body, user, status }) => {
      const result = await execProcedure('iot.upsert_threshold', [{
        ...(body as any),
        user_id: (user as any).id,
      }]);
      if (result.error) return status(400, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_thresholds,
      body: t.Object({
        sensor_id: t.Number(),
        metric: t.String(),
        min_value: t.Optional(t.Nullable(t.Number())),
        max_value: t.Optional(t.Nullable(t.Number())),
        alert_message: t.Optional(t.String()),
      }),
    })

    .delete('/:id', async ({ params, status }) => {
      const result = await execProcedure('iot.delete_threshold', [{ id: parseInt(params.id) }]);
      if (result.error) return status(400, { message: result.error });
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.manage_thresholds })
  );
