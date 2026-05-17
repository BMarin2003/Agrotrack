import { createClient } from '@supabase/supabase-js';
import { configServer } from 'src/config';

const { url, anonKey, serviceRoleKey } = configServer.supabase;

export const supabase = createClient(url, anonKey);

export const supabaseAdmin = createClient(url, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});
