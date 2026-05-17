/**
 * Seed script — crea 3 usuarios de prueba en Supabase.
 * Ejecutar: bun run seed
 *
 * PRE-REQUISITOS:
 *   1. Ejecutar postgres/supabase/migration.sql en Supabase SQL Editor
 *   2. En Supabase Dashboard → Project Settings → API → Exposed schemas:
 *      añadir "core" e "iot"
 *
 * Usuarios creados:
 *   operador@agrotrack.com  / Operador2024!   → rol: Operador
 *   tecnico@agrotrack.com   / Tecnico2024!    → rol: Técnico
 *   admin@agrotrack.com     / Admin2024!      → rol: Administrador
 */

import 'dotenv/config';
import '../src/config';
import { supabaseAdmin } from '../src/core/supabase';
import { execProcedure } from '../src/core/db/connection';

const TEST_USERS = [
  { names: 'Operador Test', email: 'operador@agrotrack.com', password: 'Operador2024!', role: 'Operador' },
  { names: 'Técnico Test',  email: 'tecnico@agrotrack.com',  password: 'Tecnico2024!',  role: 'Técnico' },
  { names: 'Super Admin',   email: 'admin@agrotrack.com',    password: 'Admin2024!',    role: 'Administrador' },
];

async function testConnection() {
  const { data, error } = await (supabaseAdmin.schema('core') as any)
    .from('roles')
    .select('id')
    .limit(1);
  if (error) throw new Error(`Conexión fallida: ${error.message}\n\n  ¿Ejecutaste la migración SQL y configuraste los schemas expuestos?`);
  return true;
}

async function getRoleId(roleName: string): Promise<number | null> {
  const { data, error } = await (supabaseAdmin.schema('core') as any)
    .from('roles')
    .select('id')
    .eq('name', roleName)
    .limit(1)
    .single();
  if (error || !data) return null;
  return data.id;
}

async function userExists(email: string): Promise<number | null> {
  const { data, error } = await (supabaseAdmin.schema('core') as any)
    .from('users')
    .select('id')
    .eq('email', email)
    .limit(1)
    .single();
  if (error || !data) return null;
  return data.id;
}

async function seed() {
  console.log('[Seed] Iniciando seed de usuarios de prueba...\n');

  await testConnection();
  console.log('[Seed] Conexión OK\n');

  for (const user of TEST_USERS) {
    console.log(`[Seed] Procesando: ${user.email}`);

    const roleId = await getRoleId(user.role);
    if (!roleId) {
      console.error(`  [!] Rol "${user.role}" no encontrado. ¿Se ejecutó la migración SQL?`);
      continue;
    }

    const existingId = await userExists(user.email);
    if (existingId) {
      console.log(`  [~] Ya existe (id=${existingId}), omitiendo.`);
      continue;
    }

    const password_hash = Bun.password.hashSync(user.password, { algorithm: 'bcrypt', cost: 10 });

    const result = await execProcedure('core.save_user', [{
      names: user.names,
      email: user.email,
      password_hash,
      role_id: roleId,
    }]);

    if (result.error) {
      console.error(`  [!] Error al crear: ${result.error}`);
    } else {
      console.log(`  [+] Creado — id=${result.result?.id}  rol=${user.role}`);
    }
  }

  console.log('\n[Seed] Listo.\n');
  console.log('Credenciales de prueba:');
  console.log('────────────────────────────────────────────────');
  for (const u of TEST_USERS) {
    console.log(`  ${u.email.padEnd(32)} ${u.password.padEnd(16)} (${u.role})`);
  }
  console.log('────────────────────────────────────────────────');
  process.exit(0);
}

seed().catch(err => {
  console.error('\n[Seed] Error fatal:', err.message ?? err);
  process.exit(1);
});
