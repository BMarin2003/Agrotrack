import { PERMISSIONS } from './permissions.constants';

type DeepValue<T> = T extends string
  ? T
  : T extends object
  ? { [K in keyof T]: DeepValue<T[K]> }[keyof T]
  : never;

export type PermisoSlug = DeepValue<typeof PERMISSIONS> | (string & {});
