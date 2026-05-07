import { cors } from '@elysiajs/cors';
import { openapi } from '@elysiajs/openapi';
import { Elysia } from 'elysia';
import { configServer } from './config';
import { routerApi, routerWs } from './router';
import { bootServer } from './run-server';

try {
  bootServer();
} catch (error) {
  console.error(error);
}

const app = new Elysia()
  .onError(({ code, error, request }) => {
    switch (code) {
      case 'NOT_FOUND':
        const url = new URL(request.url);
        return { message: `Not found: ${request.method} ${url.pathname}` };
      case 'INTERNAL_SERVER_ERROR':
        return { message: 'Internal server error' };
      case 'VALIDATION':
        const fields = error.all.map(e => e.path.replace('/', '')).join(', ');
        return {
          message: `Error de validación en: ${fields}`,
          errors: error.all.map(e => ({ field: e.path.replace('/', ''), message: e.message })),
        };
      default:
        return { message: 'Unknown error' };
    }
  })
  .use(cors({ origin: true, credentials: true }))
  .use(openapi())
  .group('/api', api => api.use(routerApi))
  .group('/ws', ws => ws.use(routerWs));

app.listen(configServer.port);

console.log(`🌱 AgroTrack Antigravity running at ${app.server?.url?.origin}`);
