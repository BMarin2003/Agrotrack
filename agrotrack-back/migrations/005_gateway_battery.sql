-- ============================================================
-- AgroTrack — Migration 005: Batería del gateway
-- El payload MQTT (TwarmPayload) ya trae el campo Bg (Battery Gateway),
-- pero hasta ahora solo se guardaba inerte en extra_data sin usarse.
-- Se agrega la columna y se expone junto a connectivity_mode/pending_sync_count.
-- ============================================================

ALTER TABLE iot.gateways
    ADD COLUMN IF NOT EXISTS battery NUMERIC(5,2);

-- ─── iot.update_gateway_status: acepta battery opcional ────────────────────
CREATE OR REPLACE FUNCTION iot.update_gateway_status(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.gateways SET
        connectivity_mode  = COALESCE(p_data->>'connectivity_mode', connectivity_mode),
        pending_sync_count = COALESCE((p_data->>'pending_sync_count')::INTEGER, pending_sync_count),
        battery            = COALESCE((p_data->>'battery')::NUMERIC, battery),
        last_synced_at     = CASE WHEN COALESCE((p_data->>'pending_sync_count')::INTEGER, 0) = 0
                                   THEN NOW() ELSE last_synced_at END,
        date_up            = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'gateway_id')::INTEGER;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

-- ─── iot.list_gateways: incluye battery ────────────────────────────────────
CREATE OR REPLACE FUNCTION iot.list_gateways(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', g.id, 'name', g.name, 'identifier', g.identifier,
        'location', g.location, 'enable', g.enable,
        'connectivity_mode', g.connectivity_mode,
        'pending_sync_count', g.pending_sync_count,
        'battery', g.battery
    ) ORDER BY g.id) INTO v_result FROM iot.gateways g;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;
