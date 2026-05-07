import { testDbConnection } from '@core/db/connection';
import { Elysia } from 'elysia';
import { readFileSync } from 'fs';

import { AuthApi } from '@modules/core/auth.api';
import { UsersApi } from '@modules/core/users.api';

import { SensorsApi } from '@modules/iot/sensors.api';
import { TelemetryApi } from '@modules/iot/telemetry.api';
import { ThresholdsApi } from '@modules/iot/thresholds.api';
import { AlertsApi } from '@modules/iot/alerts.api';
import { ReportsApi } from '@modules/iot/reports.api';

import { TelemetryWs } from '@modules/iot/telemetry.ws';

const handleHome = async ({ status }) => {
  let version = '';
  const d = new Date();

  const queryResult = await testDbConnection();
  if (queryResult.error) {
    return status(500, { message: queryResult.error });
  }

  try {
    version = readFileSync('deploy.txt').toString();
  } catch {
    version = configServer.version;
  }

  return {
    message: 'AgroTrack Antigravity API',
    version,
    time: Date.now(),
    date: d.toISOString(),
  };
};

import { configServer } from './config';

export const routerApi = new Elysia()
  .get('/', handleHome)
  .use(AuthApi)
  .use(UsersApi)
  .use(SensorsApi)
  .use(TelemetryApi)
  .use(ThresholdsApi)
  .use(AlertsApi)
  .use(ReportsApi);

export const routerWs = new Elysia({
  websocket: {
    idleTimeout: 1000 * 60 * 60 * 12,
  },
}).use(TelemetryWs);
