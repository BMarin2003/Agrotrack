import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { supabaseAdmin } from '@core/supabase';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/alerts';

export const AlertsApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .get('/', async ({ query, user, set }) => {
      const userId = (user as any).isAdmin ? null : (user as any).id;
      const result = await execProcedure('iot.get_active_alerts', [{
        gateway_id: query.gateway_id || null,
        resolved: query.resolved === 'true',
        user_id: userId,
      }]);
      if (result.error) { set.status = 500; return { message: result.error }; }
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.view_alerts,
      query: t.Object({
        gateway_id: t.Optional(t.String()),
        resolved: t.Optional(t.String()),
      }),
    })

    .put('/:id/resolve', async ({ params, set }) => {
      const result = await execProcedure('iot.resolve_alert', [{ id: parseInt(params.id) }]);
      if (result.error) { set.status = 400; return { message: result.error }; }
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.resolve_alerts })

    .delete('/clear', async ({ set }) => {
      const { error } = await (supabaseAdmin.schema('iot') as any)
        .from('alerts')
        .delete()
        .gt('id', 0);
      if (error) { set.status = 500; return { message: error.message }; }
      return { ok: true };
    }, { requirePermission: PERMISSIONS.iot.resolve_alerts })
  );
