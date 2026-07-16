-- ============================================================
-- AgroTrack — Migration 007: Alias en dashboard + próximo mantenimiento
-- iot.get_latest_readings_by_gateway ahora aplica el alias del sensor
-- (igual que iot.list_sensors/iot.get_sensor desde la migración 004,
-- que nunca se propagó a esta función). iot.list_gateways expone
-- next_maintenance para que el listado global de soporte no dependa
-- de entrar al detalle de cada gateway.
-- ============================================================

-- ─── 1. iot.get_latest_readings_by_gateway: alias por operador (HU6) ───────
CREATE OR REPLACE FUNCTION iot.get_latest_readings_by_gateway(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(t ORDER BY t.sensor_id) INTO v_result
    FROM (
        SELECT DISTINCT ON (r.sensor_id)
            r.id, r.sensor_id, COALESCE(al.alias, s.name) AS sensor_name, s.unit,
            r.temperature, r.voltage, r.battery, r.extra_data, r.received_at
        FROM iot.sensor_readings r
        JOIN iot.sensors s ON s.id = r.sensor_id
        LEFT JOIN iot.sensor_aliases al
          ON al.sensor_id = r.sensor_id AND al.user_id = (p_data->>'user_id')::INTEGER
        WHERE r.gateway_id = (p_data->>'gateway_id')::INTEGER
        ORDER BY r.sensor_id, r.received_at DESC
    ) t;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

-- ─── 2. iot.list_gateways: expone next_maintenance (HU27) ──────────────────
CREATE OR REPLACE FUNCTION iot.list_gateways(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', g.id, 'name', g.name, 'identifier', g.identifier,
        'location', g.location, 'enable', g.enable,
        'connectivity_mode', g.connectivity_mode,
        'pending_sync_count', g.pending_sync_count,
        'battery', g.battery,
        'next_maintenance', lm.next_scheduled
    ) ORDER BY g.id) INTO v_result
    FROM iot.gateways g
    LEFT JOIN LATERAL (
        SELECT m.next_scheduled
        FROM iot.gateway_maintenance m
        WHERE m.gateway_id = g.id
        ORDER BY m.performed_at DESC
        LIMIT 1
    ) lm ON true;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;
