import { supabaseAdmin } from '../supabase';

export interface IPgResult { error?: any; result?: any }

/**
 * Ejecuta un stored procedure de PostgreSQL vía Supabase RPC.
 *
 * Convenciones del proyecto:
 *  - Todos los procedures aceptan exactamente un parámetro JSON: p_data
 *  - Los nombres siguen el patrón "schema.function_name"
 *  - Los schemas "core" e "iot" deben estar expuestos en Supabase API Settings
 */
export async function execProcedure(
  procedureName: string,
  args: any[] | { [key: string]: any },
  _options: { maxRetries?: number } = {},
): Promise<IPgResult> {
  const dotIdx = procedureName.lastIndexOf('.');
  const schema = dotIdx > -1 ? procedureName.slice(0, dotIdx) : 'public';
  const fn     = dotIdx > -1 ? procedureName.slice(dotIdx + 1) : procedureName;

  let p_data: any;
  if (!args || (Array.isArray(args) && args.length === 0)) {
    p_data = {};
  } else if (Array.isArray(args)) {
    p_data = args[0];
  } else {
    p_data = args;
  }

  const t0 = Date.now();
  try {
    const { data, error } = await (supabaseAdmin.schema(schema) as any).rpc(fn, { p_data });
    const ms = Date.now() - t0;
    if (error) {
      console.error(`[DB] Error en ${procedureName}:`, error.message);
      return { error: error.message };
    }
    console.log(`[DB] ${procedureName} - ${ms}ms`);
    return { result: data };
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    console.error(`[DB] Error en ${procedureName}:`, msg);
    return { error: msg };
  }
}

export async function testDbConnection(): Promise<IPgResult> {
  const { error } = await (supabaseAdmin.schema('core') as any)
    .from('roles')
    .select('id')
    .limit(1);

  if (error) {
    console.error('[DB] Error en testDbConnection:', error.message);
    return { error: error.message };
  }
  console.log('[DB] Conexión a Supabase exitosa');
  return { result: true };
}

/** No-op: Supabase REST no requiere cerrar conexiones. */
export async function closeSqlConnection(_timeout?: number): Promise<void> {}
