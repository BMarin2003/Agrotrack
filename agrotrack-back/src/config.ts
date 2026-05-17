import { setTimeZone } from 'bun:jsc';

const isProduction = process.env.NODE_ENV === 'production';

export const configServer = {
  version: '0.1.0',
  isProduction,
  team: process.env.TEAM_NAME || 'Corall D&R',
  timeZone: 'America/Lima',
  port: parseInt(process.env.PORT || '3000'),
  auth: {
    secret: process.env.JWT_SECRET || 'default_secret',
    expiresIn: process.env.JWT_EXPIRE_IN ? parseInt(process.env.JWT_EXPIRE_IN) : 7 * 24 * 60 * 60,
  },
  db: {
    user: process.env.DB_USER || 'postgres',
    host: process.env.DB_HOST || 'localhost',
    database: process.env.DB_NAME || 'agrotrack',
    password: process.env.DB_PASSWORD || 'postgres',
    port: parseInt(process.env.DB_PORT || '5432'),
    maxPoolSize: parseInt(process.env.DB_MAX_POOL_SIZE || '10'),
    ssl: process.env.DB_SSL === 'true',
  },
  supabase: {
    url: process.env.SUPABASE_URL || '',
    anonKey: process.env.SUPABASE_ANON_KEY || '',
    serviceRoleKey: process.env.SUPABASE_SERVICE_ROLE_KEY || '',
  },
  mqtt: {
    brokerUrl: process.env.MQTT_BROKER_URL || 'mqtt://localhost:1883',
    username: process.env.MQTT_USERNAME || '',
    password: process.env.MQTT_PASSWORD || '',
    clientId: process.env.MQTT_CLIENT_ID || `agrotrack-server-${Date.now()}`,
    topicPrefix: 'agrotrack/gateways',
  },
  mock: {
    gatewayIdentifier: process.env.MOCK_GATEWAY_IDENTIFIER || 'GW-001',
    sensorCount: parseInt(process.env.MOCK_SENSOR_COUNT || '6'),
    intervalMs: parseInt(process.env.MOCK_INTERVAL_MS || '5000'),
  },
};

setTimeZone(configServer.timeZone);
