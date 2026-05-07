export const PERMISSIONS = {
  iot: {
    view_sensors:      'iot.view_sensors',
    manage_sensors:    'iot.manage_sensors',
    view_telemetry:    'iot.view_telemetry',
    view_alerts:       'iot.view_alerts',
    resolve_alerts:    'iot.resolve_alerts',
    manage_thresholds: 'iot.manage_thresholds',
    view_reports:      'iot.view_reports',
    manage_gateways:   'iot.manage_gateways',
  },
  admin: {
    manage_users: 'admin.manage_users',
    manage_roles: 'admin.manage_roles',
  },
} as const;
