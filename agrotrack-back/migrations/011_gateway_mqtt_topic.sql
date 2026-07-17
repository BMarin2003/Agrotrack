-- ============================================================
-- AgroTrack — Migration 011: Configurar tópico MQTT por gateway (HU19)
-- No hay forma real de empujar esta config a un gateway físico sin un
-- canal de aprovisionamiento (BLE/AP local/QR) que no existe en este repo
-- todavía. Se guarda en BD igual que WiFi/PIN, con un ciclo de
-- confirmación (status pending/applied/error) — la flota simulada lo
-- confirma solo con latencia; un gateway real se queda en 'pending' hasta
-- que exista un mecanismo real de aprovisionamiento.
-- ============================================================

ALTER TABLE iot.gateways
    ADD COLUMN IF NOT EXISTS mqtt_topic            TEXT,
    ADD COLUMN IF NOT EXISTS mqtt_topic_status      VARCHAR(20) NOT NULL DEFAULT 'none',
    ADD COLUMN IF NOT EXISTS mqtt_topic_request_id  UUID;

CREATE OR REPLACE FUNCTION iot.update_gateway_mqtt_topic(p_data JSON)
RETURNS JSON AS $$
DECLARE v_request_id UUID; v_identifier TEXT;
BEGIN
    v_request_id := gen_random_uuid();

    UPDATE iot.gateways SET
        mqtt_topic           = p_data->>'topic',
        mqtt_topic_status    = 'pending',
        mqtt_topic_request_id = v_request_id,
        date_up              = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'id')::INTEGER
    RETURNING identifier INTO v_identifier;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Gateway no encontrado: %', p_data->>'id';
    END IF;

    RETURN JSON_BUILD_OBJECT('ok', TRUE, 'request_id', v_request_id, 'identifier', v_identifier);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.mark_mqtt_topic_status(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.gateways SET
        mqtt_topic_status = COALESCE(p_data->>'status', 'applied')
    WHERE mqtt_topic_request_id = (p_data->>'request_id')::UUID
      AND mqtt_topic_status = 'pending';
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

-- iot.list_gateways: expone mqtt_topic/mqtt_topic_status para poder
-- pollear el resultado desde la app (mismo patrón que la calibración).
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
        'next_maintenance', lm.next_scheduled,
        'mqtt_topic', g.mqtt_topic,
        'mqtt_topic_status', g.mqtt_topic_status,
        'status', CASE
            WHEN NOT g.enable THEN 'maintenance'
            WHEN g.last_synced_at IS NOT NULL AND g.last_synced_at > NOW() - INTERVAL '2 minutes' THEN 'online'
            ELSE 'offline'
        END
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

GRANT EXECUTE ON FUNCTION iot.update_gateway_mqtt_topic(JSON) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.mark_mqtt_topic_status(JSON)    TO anon, authenticated, service_role;
