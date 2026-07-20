import { describe, it, expect } from "bun:test";
import { routerApi } from "../src/router";
import { getToken, authHeaders } from "./helpers/auth";

describe("Caja negra — GET/POST/PUT /helpdesk/tickets", () => {
  it("técnico crea un ticket, lo lista y actualiza su estado (ciclo completo)", async () => {
    const token = await getToken("tecnico");

    const createRes = await routerApi.handle(
      new Request("http://localhost/helpdesk/tickets", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ subject: "Ticket de prueba caja negra", description: "Descripción de prueba" }),
      }),
    );
    expect(createRes.status).toBe(200);
    const created = await createRes.json();
    expect(created.id).toBeDefined();
    const ticketId = created.id;

    const listRes = await routerApi.handle(
      new Request("http://localhost/helpdesk/tickets", { headers: authHeaders(token) }),
    );
    expect(listRes.status).toBe(200);
    const tickets = await listRes.json();
    const foundTicket = tickets.find((t: any) => t.id === ticketId);
    expect(foundTicket).toBeDefined();
    expect(foundTicket.subject).toBe("Ticket de prueba caja negra");

    const statusRes = await routerApi.handle(
      new Request(`http://localhost/helpdesk/tickets/${ticketId}/status`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ status: "resuelto" }),
      }),
    );
    expect(statusRes.status).toBe(200);
  });

  it("subject vacío devuelve error de validación (422, minLength: 1)", async () => {
    const token = await getToken("tecnico");
    const res = await routerApi.handle(
      new Request("http://localhost/helpdesk/tickets", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ subject: "" }),
      }),
    );
    expect(res.status).toBe(422);
  });

  it("status fuera del enum permitido devuelve error de validación (422)", async () => {
    const token = await getToken("tecnico");
    const createRes = await routerApi.handle(
      new Request("http://localhost/helpdesk/tickets", {
        method: "POST",
        headers: authHeaders(token),
        body: JSON.stringify({ subject: "Ticket para probar status inválido" }),
      }),
    );
    const created = await createRes.json();
    const ticketId = created.id;

    const res = await routerApi.handle(
      new Request(`http://localhost/helpdesk/tickets/${ticketId}/status`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ status: "estado_que_no_existe" }),
      }),
    );
    expect(res.status).toBe(422);

    // Cleanup: close the synthetic ticket to avoid leaving it open in the real help desk
    const cleanupRes = await routerApi.handle(
      new Request(`http://localhost/helpdesk/tickets/${ticketId}/status`, {
        method: "PUT",
        headers: authHeaders(token),
        body: JSON.stringify({ status: "cerrado" }),
      }),
    );
    expect(cleanupRes.status).toBe(200);
  });
});
