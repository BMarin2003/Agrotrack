import { SQL } from 'bun';
import { configServer } from '../../config';

export interface IPgResult { error?: any; result?: any }

let sql: SQL;

function getSqlInstance(): SQL {
  if (!sql) {
    const connectionUrl = `postgresql://${configServer.db.user}:${configServer.db.password}@${configServer.db.host}:${configServer.db.port}/${configServer.db.database}`;
    sql = new SQL(connectionUrl, {
      idleTimeout: 20,
      max: configServer.db.maxPoolSize || 10,
      connectionTimeout: 10,
    });
  }
  return sql;
}

export async function execProcedure(
  procedureName: string,
  args: any[] | { [key: string]: any },
  { maxRetries }: { maxRetries: number } = { maxRetries: 3 }
): Promise<IPgResult> {
  const db = getSqlInstance();

  let finalArgs: any[];
  if (!args) {
    finalArgs = [];
  } else if (Array.isArray(args)) {
    finalArgs = args;
  } else {
    finalArgs = [args];
  }

  const placeholders = finalArgs.map((_, i) => `$${i + 1}`).join(', ');
  const startTime = Date.now();
  let lastError: any = null;

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const funcToExec = `SELECT * FROM ${procedureName}(${placeholders}) as output`;
      const queryResult = await db.unsafe(funcToExec, finalArgs);
      const result = queryResult[0]?.output;

      const elapsed = Date.now() - startTime;
      if (attempt > 1) {
        console.log(`[DB] ${procedureName} - ${elapsed}ms (reintento ${attempt - 1} exitoso)`);
      } else {
        console.log(`[DB] ${procedureName} - ${elapsed}ms`);
      }

      return { result };
    } catch (error) {
      lastError = error;
      const errorMsg = error instanceof Error ? error.message : String(error);
      const isTimeout =
        errorMsg.toLowerCase().includes('timeout') ||
        errorMsg.toLowerCase().includes('timed out') ||
        errorMsg.toLowerCase().includes('connection timeout');

      if (isTimeout && attempt < maxRetries) {
        console.warn(`[DB] Timeout en ${procedureName} (intento ${attempt}/${maxRetries}), reintentando...`);
        await new Promise(resolve => setTimeout(resolve, 100 * attempt));
        continue;
      }

      console.error(`[DB] Error en ${procedureName}:`, errorMsg);
      return { error: errorMsg };
    }
  }

  const errorMsg = lastError instanceof Error ? lastError.message : String(lastError);
  console.error(`[DB] Error en ${procedureName} después de ${maxRetries} intentos:`, errorMsg);
  return { error: errorMsg };
}

export function getSqlClient(): SQL {
  return getSqlInstance();
}

export async function closeSqlConnection(timeout?: number): Promise<void> {
  if (sql) {
    try {
      await sql.close({ timeout: timeout ?? 5 });
    } catch (error) {
      console.error('[DB] Error al cerrar conexión:', error);
    }
  }
}

export async function testDbConnection(): Promise<IPgResult> {
  const db = getSqlInstance();
  try {
    const queryResult = await db.unsafe(`SELECT 1 as result`);
    console.log('[DB] Conexión a la base de datos exitosa');
    return { result: queryResult[0]?.result };
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : String(error);
    console.error('[DB] Error en testDbConnection:', errorMsg);
    return { error: errorMsg };
  }
}
