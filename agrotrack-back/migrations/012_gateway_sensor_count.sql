-- ============================================================
-- AgroTrack — Migration 012: Conteo real de sensores por gateway
-- iot.list_gateways nunca devolvió `sensor_count` en ninguna de sus
-- versiones anteriores (001/005/007/010/011) — GatewayDto.sensorCount
-- siempre llegaba null desde el backend y la app mostraba "0 sensores"
-- sin importar cuántos hubiera. Se cuenta de la relación real
-- iot.sensors.gateway_id, no de un valor aparte que se pueda desincronizar.
-- ============================================================

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
        'sensor_count', COALESCE(sc.cnt, 0),
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
    ) lm ON true
    LEFT JOIN LATERAL (
        SELECT COUNT(*) AS cnt
        FROM iot.sensors s
        WHERE s.gateway_id = g.id AND s.enable = TRUE
    ) sc ON true;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;
