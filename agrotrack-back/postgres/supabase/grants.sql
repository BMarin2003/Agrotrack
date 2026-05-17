-- =============================================================================
-- AGROTRACK — Permisos de schemas para Supabase
-- Ejecutar DESPUÉS de migration.sql en Supabase SQL Editor
-- =============================================================================

-- Acceso a los schemas (necesario para PostgREST y el service role)
GRANT USAGE ON SCHEMA core TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA iot  TO anon, authenticated, service_role;

-- Acceso a todas las tablas actuales
GRANT ALL ON ALL TABLES    IN SCHEMA core TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES    IN SCHEMA iot  TO anon, authenticated, service_role;

-- Acceso a las secuencias (SERIAL / BIGSERIAL)
GRANT ALL ON ALL SEQUENCES IN SCHEMA core TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA iot  TO anon, authenticated, service_role;

-- Ejecución de los stored procedures
GRANT ALL ON ALL FUNCTIONS IN SCHEMA core TO anon, authenticated, service_role;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA iot  TO anon, authenticated, service_role;

-- Asegurar que las tablas futuras también hereden los permisos
ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT ALL ON TABLES    TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA iot  GRANT ALL ON TABLES    TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA iot  GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA iot  GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;
