-- ============================================================
-- AgroTrack — Migration 008: Mesa de ayuda (tickets)
-- HU26: sistema de tickets real (antes era un placeholder en la app
-- sin nada de backend). Tabla + procedimientos + permiso nuevo.
-- ============================================================

-- ─── 1. Enum de estado del ticket ───────────────────────────────────────────
DO $$ BEGIN
  CREATE TYPE iot.enum_ticket_status AS ENUM ('abierto', 'en_progreso', 'resuelto', 'cerrado');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ─── 2. Tabla: iot.helpdesk_tickets ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iot.helpdesk_tickets (
    id          BIGSERIAL PRIMARY KEY,
    gateway_id  INTEGER REFERENCES iot.gateways(id),
    created_by  INTEGER NOT NULL REFERENCES core.users(id),
    subject     VARCHAR(200) NOT NULL,
    description TEXT,
    status      iot.enum_ticket_status NOT NULL DEFAULT 'abierto',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tickets_status     ON iot.helpdesk_tickets(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_gateway     ON iot.helpdesk_tickets(gateway_id);

-- ─── 3. iot.create_ticket ───────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION iot.create_ticket(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO iot.helpdesk_tickets (gateway_id, created_by, subject, description)
    VALUES (
        (p_data->>'gateway_id')::INTEGER,
        (p_data->>'user_id')::INTEGER,
        p_data->>'subject',
        p_data->>'description'
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;

-- ─── 4. iot.list_tickets ────────────────────────────────────────────────────
-- Vista global (igual que el resto del módulo de soporte): cualquiera con el
-- permiso ve todos los tickets, no solo los propios.
CREATE OR REPLACE FUNCTION iot.list_tickets(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', t.id, 'gateway_id', t.gateway_id, 'gateway_name', g.name,
        'created_by', t.created_by, 'created_by_name', u.names,
        'subject', t.subject, 'description', t.description,
        'status', t.status, 'created_at', t.created_at, 'updated_at', t.updated_at
    ) ORDER BY t.created_at DESC) INTO v_result
    FROM iot.helpdesk_tickets t
    LEFT JOIN iot.gateways g ON g.id = t.gateway_id
    JOIN core.users u ON u.id = t.created_by
    WHERE (p_data->>'status' IS NULL OR t.status = (p_data->>'status')::iot.enum_ticket_status);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

-- ─── 5. iot.update_ticket_status ────────────────────────────────────────────
CREATE OR REPLACE FUNCTION iot.update_ticket_status(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.helpdesk_tickets SET
        status     = (p_data->>'status')::iot.enum_ticket_status,
        updated_at = NOW()
    WHERE id = (p_data->>'id')::BIGINT;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Ticket no encontrado: %', p_data->>'id';
    END IF;

    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

-- ─── 6. Permiso nuevo: gestionar mesa de ayuda ──────────────────────────────
INSERT INTO core.permissions (name, slug) VALUES
    ('Gestionar mesa de ayuda', 'iot.manage_helpdesk')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO core.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM core.roles r, core.permissions p
WHERE r.name IN ('Técnico', 'Administrador') AND p.slug = 'iot.manage_helpdesk'
ON CONFLICT DO NOTHING;

-- ─── 7. Permisos de ejecución (Supabase RPC) ────────────────────────────────
GRANT ALL ON TABLE  iot.helpdesk_tickets        TO anon, authenticated, service_role;
GRANT ALL ON SEQUENCE iot.helpdesk_tickets_id_seq TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.create_ticket(JSON)        TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.list_tickets(JSON)         TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.update_ticket_status(JSON) TO anon, authenticated, service_role;
