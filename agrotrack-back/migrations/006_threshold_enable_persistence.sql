-- ─── Fix: iot.upsert_threshold ignoraba el campo `enable` del payload y forzaba
-- enable = TRUE en cada guardado (ON CONFLICT), impidiendo desactivar alertas
-- de un sensor sin que se reactiven solas en el siguiente guardado.
CREATE OR REPLACE FUNCTION iot.upsert_threshold(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    INSERT INTO iot.thresholds (sensor_id, user_id, metric, min_value, max_value, alert_message, enable)
    VALUES (
        (p_data->>'sensor_id')::INTEGER, (p_data->>'user_id')::INTEGER,
        COALESCE(p_data->>'metric', 'temperature'),
        (p_data->>'min_value')::NUMERIC, (p_data->>'max_value')::NUMERIC,
        p_data->>'alert_message',
        COALESCE((p_data->>'enable')::BOOLEAN, TRUE)
    )
    ON CONFLICT (sensor_id, user_id, metric) DO UPDATE SET
        min_value     = EXCLUDED.min_value,
        max_value     = EXCLUDED.max_value,
        alert_message = EXCLUDED.alert_message,
        enable        = EXCLUDED.enable,
        date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
    RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;
