import { Elysia } from 'elysia';
import { verifyToken } from '@core/jwt';
import { ElysiaWS } from 'elysia/ws';

interface WsClient {
  ws: ElysiaWS;
  user: { id: string | number };
  gatewayId: string | number;
}

// clientId -> WsClient
export const wsClients = new Map<string, WsClient>();

export function broadcastToGateway(gatewayId: string | number, payload: object) {
  const message = JSON.stringify(payload);
  for (const [, client] of wsClients) {
    if (String(client.gatewayId) === String(gatewayId)) {
      try {
        client.ws.send(message);
      } catch {}
    }
  }
}

export function broadcastToAll(payload: object) {
  const message = JSON.stringify(payload);
  for (const [, client] of wsClients) {
    try {
      client.ws.send(message);
    } catch {}
  }
}

export const TelemetryWs = new Elysia()
  .ws('telemetry', {
    open: async ws => {
      const { token, gateway_id, wsid } = ws.data.query as any;

      if (!token || !wsid) return ws.terminate();

      const user = verifyToken(token);
      if (!user) return ws.terminate();

      wsClients.set(wsid, { ws, user: { id: user.id }, gatewayId: gateway_id || '*' });
      console.log(`[WS] Cliente conectado wsid=${wsid} gateway=${gateway_id}`);

      ws.send(JSON.stringify({ type: 'connected', gatewayId: gateway_id }));
    },
    close: ws => {
      const { wsid } = ws.data.query as any;
      wsClients.delete(wsid);
    },
    message: (ws, message) => {
      // ping/pong keepalive
      if (message === 'ping') ws.send('pong');
    },
  });
