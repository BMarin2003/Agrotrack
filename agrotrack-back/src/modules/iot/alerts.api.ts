import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/alerts';

export const AlertsApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .get('/', async ({ query, status }) => {
      const result = await execProcedure('iot.get_active_alerts', [{
        gateway_id: query.gateway_id || null,
        resolved: query.resolved === 'true',
      }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.view_alerts,
      query: t.Object({
        gateway_id: t.Optional(t.String()),
        resolved: t.Optional(t.String()),
      }),
    })

    .put('/:id/resolve', async ({ params, status }) => {
      const result = await execProcedure('iot.resolve_alert', [{ id: parseInt(params.id) }]);
      if (result.error) return status(400, { message: result.error });
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.resolve_alerts })
  );
