import { setTimeZone } from 'bun:jsc';

export const configServer = {
  version: '0.1.0',
  team: process.env.TEAM_NAME || 'Corall D&R',
  timeZone: 'America/Lima',
  port: parseInt(process.env.PORT || '3000'),
  auth: {
    secret: process.env.JWT_SECRET || 'default_secret',
    expiresIn: process.env.JWT_EXPIRE_IN ? parseInt(process.env.JWT_EXPIRE_IN) : 7 * 24 * 60 * 60,
  },
  db: {
    user: process.env.DB_USER,
    host: process.env.DB_HOST,
    database: process.env.DB_NAME || 'agrotrack',
    password: process.env.DB_PASSWORD,
    port: parseInt(process.env.DB_PORT || '5432'),
    maxPoolSize: parseInt(process.env.DB_MAX_POOL_SIZE || '10'),
  },
  redis: {
    url: process.env.REDIS_URL || 'redis://localhost:6379',
  },
  mqtt: {
    brokerUrl: process.env.MQTT_BROKER_URL || 'mqtt://localhost:1883',
    username: process.env.MQTT_USERNAME || '',
    password: process.env.MQTT_PASSWORD || '',
    clientId: process.env.MQTT_CLIENT_ID || `agrotrack-server-${Date.now()}`,
    topicPrefix: 'agrotrack/gateways',
  },
};

setTimeZone(configServer.timeZone);
