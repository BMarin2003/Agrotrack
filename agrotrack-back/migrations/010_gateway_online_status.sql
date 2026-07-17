-- ============================================================
-- AgroTrack — Migration 010: Estado online real del gateway
-- iot.list_gateways nunca devolvía un campo `status`, así que
-- GatewayStatus.from(null, enable) en la app siempre caía a "Offline"
-- sin importar si el gateway estaba sincronizando o no. Se calcula un
-- estado real a partir de last_synced_at (actualizado por
-- iot.update_gateway_status cada vez que pending_sync_count llega a 0).
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
