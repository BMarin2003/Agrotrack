import { Elysia, t } from "elysia";
import { execProcedure } from "@core/db/connection";
import { authPlugin } from "@core/auth.guard";
import { PERMISSIONS } from "@core/permissions.constants";

const path = "/helpdesk";

export const HelpdeskApi = new Elysia().group(path, (app) =>
  app
    .use(authPlugin)

    .get(
      "/tickets",
      async ({ query, set }) => {
        const result = await execProcedure("iot.list_tickets", [
          { status: query.status || null },
        ]);
        if (result.error) { set.status = 500; return { message: result.error }; }
        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_helpdesk,
        query: t.Object({
          status: t.Optional(t.String()),
        }),
      },
    )

    .post(
      "/tickets",
      async ({ body, user, set }) => {
        const result = await execProcedure("iot.create_ticket", [
          { ...(body as any), user_id: (user as any).id },
        ]);
        if (result.error) { set.status = 400; return { message: result.error }; }
        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_helpdesk,
        body: t.Object({
          gateway_id:  t.Optional(t.Number()),
          subject:     t.String({ minLength: 1, maxLength: 200 }),
          description: t.Optional(t.String()),
        }),
      },
    )

    .put(
      "/tickets/:id/status",
      async ({ params, body, set }) => {
        const result = await execProcedure("iot.update_ticket_status", [
          { id: parseInt(params.id), status: (body as any).status },
        ]);
        if (result.error) { set.status = 400; return { message: result.error }; }
        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_helpdesk,
        body: t.Object({
          status: t.Union([
            t.Literal("abierto"),
            t.Literal("en_progreso"),
            t.Literal("resuelto"),
            t.Literal("cerrado"),
          ]),
        }),
      },
    ),
);
