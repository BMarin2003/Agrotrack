-- ============================================================
-- AgroTrack — Migration 013: Reportes de Gateway y General
-- Dos procedimientos de agregación server-side para no depender de
-- decenas de llamadas paralelas desde la app (ver spec 2026-07-16).
-- ============================================================

CREATE OR REPLACE FUNCTION iot.get_gateway_report(p_data JSON)
RETURNS JSON AS $$
DECLARE
  v_gateway_id INTEGER := (p_data->>'gateway_id')::INTEGER;
  v_from TIMESTAMPTZ := (p_data->>'from_ts')::TIMESTAMPTZ;
  v_to   TIMESTAMPTZ := (p_data->>'to_ts')::TIMESTAMPTZ;
  v_gateway JSON;
  v_summary JSON;
  v_sensors JSON;
  v_alerts JSON;
BEGIN
  SELECT JSON_BUILD_OBJECT(
    'id', g.id, 'name', g.name, 'location', g.location,
    'connectivity_mode', g.connectivity_mode,
    'pending_sync_count', g.pending_sync_count,
    'battery', g.battery
  ) INTO v_gateway
  FROM iot.gateways g WHERE g.id = v_gateway_id;

  IF v_gateway IS NULL THEN
    RAISE EXCEPTION 'Gateway no encontrado: %', v_gateway_id;
  END IF;

  SELECT JSON_AGG(JSON_BUILD_OBJECT(
    'id', s.id, 'name', COALESCE(al.alias, s.name), 'unit', s.unit,
    'temp_min', stats.temp_min, 'temp_max', stats.temp_max, 'temp_avg', stats.temp_avg,
    'readings', COALESCE(stats.readings, '[]'::JSON)
  ) ORDER BY s.id) INTO v_sensors
  FROM iot.sensors s
  LEFT JOIN iot.sensor_aliases al
    ON al.sensor_id = s.id AND al.user_id = (p_data->>'user_id')::INTEGER
  CROSS JOIN LATERAL (
    SELECT
      MIN(r.temperature) AS temp_min,
      MAX(r.temperature) AS temp_max,
      ROUND(AVG(r.temperature)::NUMERIC, 2) AS temp_avg,
      (
        SELECT JSON_AGG(JSON_BUILD_OBJECT('id', r2.id, 'temperature', r2.temperature, 'received_at', r2.received_at) ORDER BY r2.received_at)
        FROM (
          SELECT * FROM iot.sensor_readings r2
          WHERE r2.sensor_id = s.id AND r2.received_at BETWEEN v_from AND v_to
          ORDER BY r2.received_at DESC
          LIMIT 500
        ) r2
      ) AS readings
    FROM iot.sensor_readings r
    WHERE r.sensor_id = s.id AND r.received_at BETWEEN v_from AND v_to
  ) stats
  WHERE s.gateway_id = v_gateway_id AND s.enable = TRUE;

  SELECT JSON_BUILD_OBJECT(
    'temp_min', MIN(r.temperature),
    'temp_max', MAX(r.temperature),
    'temp_avg', ROUND(AVG(r.temperature)::NUMERIC, 2),
    'sensor_count', (SELECT COUNT(*) FROM iot.sensors WHERE gateway_id = v_gateway_id AND enable = TRUE),
    'reading_count', COUNT(r.id)
  ) INTO v_summary
  FROM iot.sensor_readings r
  JOIN iot.sensors s ON s.id = r.sensor_id
  WHERE s.gateway_id = v_gateway_id AND r.received_at BETWEEN v_from AND v_to;

  SELECT JSON_BUILD_OBJECT(
    'total', COUNT(*),
    'resolved', COUNT(*) FILTER (WHERE a.resolved),
    'unresolved', COUNT(*) FILTER (WHERE NOT a.resolved)
  ) INTO v_alerts
  FROM iot.alerts a
  WHERE a.gateway_id = v_gateway_id AND a.created_at BETWEEN v_from AND v_to;

  RETURN JSON_BUILD_OBJECT(
    'gateway', v_gateway,
    'summary', v_summary,
    'sensors', COALESCE(v_sensors, '[]'::JSON),
    'alerts', v_alerts
  );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_general_report(p_data JSON)
RETURNS JSON AS $$
DECLARE
  v_from TIMESTAMPTZ := (p_data->>'from_ts')::TIMESTAMPTZ;
  v_to   TIMESTAMPTZ := (p_data->>'to_ts')::TIMESTAMPTZ;
  v_gateways JSON;
BEGIN
  SELECT JSON_AGG(JSON_BUILD_OBJECT(
    'id', g.id, 'name', g.name, 'location', g.location,
    'connectivity_mode', g.connectivity_mode,
    'pending_sync_count', g.pending_sync_count,
    'battery', g.battery,
    'status', CASE
      WHEN NOT g.enable THEN 'maintenance'
      WHEN g.last_synced_at IS NOT NULL AND g.last_synced_at > NOW() - INTERVAL '2 minutes' THEN 'online'
      ELSE 'offline'
    END,
    'sensor_count', COALESCE(sc.cnt, 0),
    'temp_min', ts.temp_min, 'temp_max', ts.temp_max, 'temp_avg', ts.temp_avg,
    'alerts_total', COALESCE(al.total, 0), 'alerts_unresolved', COALESCE(al.unresolved, 0)
  ) ORDER BY g.id) INTO v_gateways
  FROM iot.gateways g
  LEFT JOIN LATERAL (
    SELECT COUNT(*) AS cnt FROM iot.sensors s WHERE s.gateway_id = g.id AND s.enable = TRUE
  ) sc ON true
  LEFT JOIN LATERAL (
    SELECT MIN(r.temperature) AS temp_min, MAX(r.temperature) AS temp_max, ROUND(AVG(r.temperature)::NUMERIC, 2) AS temp_avg
    FROM iot.sensor_readings r JOIN iot.sensors s ON s.id = r.sensor_id
    WHERE s.gateway_id = g.id AND r.received_at BETWEEN v_from AND v_to
  ) ts ON true
  LEFT JOIN LATERAL (
    SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE NOT a.resolved) AS unresolved
    FROM iot.alerts a WHERE a.gateway_id = g.id AND a.created_at BETWEEN v_from AND v_to
  ) al ON true;

  RETURN JSON_BUILD_OBJECT(
    'gateways', COALESCE(v_gateways, '[]'::JSON),
    'totals', JSON_BUILD_OBJECT(
      'gateway_count', (SELECT COUNT(*) FROM iot.gateways),
      'sensor_count', (SELECT COUNT(*) FROM iot.sensors WHERE enable = TRUE),
      'alert_count', (SELECT COUNT(*) FROM iot.alerts WHERE created_at BETWEEN v_from AND v_to)
    )
  );
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION iot.get_gateway_report(JSON) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.get_general_report(JSON) TO anon, authenticated, service_role;
