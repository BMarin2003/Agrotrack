-- ============================================================
-- AgroTrack — Migration 009: Confirmación (ack) de calibración por MQTT
-- HU32/33/34/35: hasta ahora "enviar calibración" solo escribía en BD,
-- sin publicar nada al gateway físico (MqttService era solo de entrada).
-- Se agrega el estado de confirmación por intento y se expone el
-- identifier del gateway (necesario para construir el tópico de comando).
--
-- IMPORTANTE: el tópico agrotrack/gateways/{identifier}/cmd/calibration y
-- su ack .../ack/calibration son un esquema PROPUESTO — no hay firmware en
-- este repo que los implemente todavía. ack_status se queda en 'pending'
-- hasta que el equipo de firmware publique el ack real; el timeout se
-- resuelve del lado de la app (ver CalibrationViewModel), no aquí.
-- ============================================================

-- ─── 1. iot.sensor_calibrations: estado de confirmación por intento ────────
ALTER TABLE iot.sensor_calibrations
    ADD COLUMN IF NOT EXISTS ack_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS request_id UUID;

-- ─── 2. iot.save_calibration: genera request_id para rastrear el ack ───────
CREATE OR REPLACE FUNCTION iot.save_calibration(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER; v_request_id UUID;
BEGIN
    v_request_id := gen_random_uuid();
    INSERT INTO iot.sensor_calibrations (sensor_id, gain, intercept, notes, applied_by, request_id, ack_status)
    VALUES (
        (p_data->>'sensor_id')::INTEGER,
        COALESCE((p_data->>'gain')::NUMERIC,      1.0),
        COALESCE((p_data->>'intercept')::NUMERIC, 0.0),
        p_data->>'notes',
        (p_data->>'user_id')::INTEGER,
        v_request_id,
        'pending'
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id, 'request_id', v_request_id);
END;
$$ LANGUAGE plpgsql;

-- ─── 3. iot.get_calibration: expone ack_status/request_id ──────────────────
CREATE OR REPLACE FUNCTION iot.get_calibration(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id',         c.id,
        'sensor_id',  c.sensor_id,
        'gain',       c.gain,
        'intercept',  c.intercept,
        'notes',      c.notes,
        'applied_at', c.applied_at,
        'ack_status', c.ack_status,
        'request_id', c.request_id
    ) INTO v_result
    FROM iot.sensor_calibrations c
    WHERE c.sensor_id = (p_data->>'sensor_id')::INTEGER
    ORDER BY c.applied_at DESC
    LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- ─── 4. iot.mark_calibration_ack: aplicado por MqttService al recibir ack ──
CREATE OR REPLACE FUNCTION iot.mark_calibration_ack(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.sensor_calibrations SET
        ack_status = COALESCE(p_data->>'status', 'ok')
    WHERE request_id = (p_data->>'request_id')::UUID
      AND ack_status = 'pending';
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

-- ─── 5. iot.get_sensor: expone el identifier del gateway (para el tópico) ──
CREATE OR REPLACE FUNCTION iot.get_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id', s.id, 'gateway_id', s.gateway_id, 'gateway_identifier', g.identifier,
        'name', COALESCE(al.alias, s.name),
        'identifier', s.identifier, 'type', s.type, 'unit', s.unit,
        'location', s.location, 'enable', s.enable
    ) INTO v_result
    FROM iot.sensors s
    JOIN iot.gateways g ON g.id = s.gateway_id
    LEFT JOIN iot.sensor_aliases al
      ON al.sensor_id = s.id AND al.user_id = (p_data->>'user_id')::INTEGER
    WHERE s.id = (p_data->>'id')::INTEGER;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- ─── 6. Permisos ─────────────────────────────────────────────────────────
GRANT EXECUTE ON FUNCTION iot.mark_calibration_ack(JSON) TO anon, authenticated, service_role;
