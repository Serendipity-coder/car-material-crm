import { PERMISSION_KEY, USER_KEY } from '../data/keys'

export const GLOBAL_DATA_PERMISSION = 'system:data:all'

export interface ClientDataScope {
  global: boolean
  departmentId: string
}

interface StoredUser {
  departmentId?: unknown
  deptId?: unknown
  dept_id?: unknown
}

function readArray(key: string): unknown[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) ?? '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

function readUser(): StoredUser {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(USER_KEY) ?? 'null')
    return value && typeof value === 'object' ? value as StoredUser : {}
  } catch {
    return {}
  }
}

export function currentDataScope(): ClientDataScope {
  const permissions = readArray(PERMISSION_KEY).map(String)
  const user = readUser()
  const departmentId = String(user.departmentId ?? user.deptId ?? user.dept_id ?? '').trim()
  return {
    global: permissions.includes('*') || permissions.includes(GLOBAL_DATA_PERMISSION),
    departmentId,
  }
}

export function isDepartmentScopedResource(resource: string): boolean {
  return resource !== 'todos'
}

/**
 * Returns null when the user has no authorized department context. Callers must
 * fail closed instead of issuing an unrestricted request in that case.
 */
export function scopedDepartmentFilters(resource: string, filters: Record<string, string> = {}): Record<string, string> | null {
  const next = { ...filters }
  if (!isDepartmentScopedResource(resource)) return next

  const scope = currentDataScope()
  if (scope.global) {
    if (!next.departmentId) delete next.departmentId
    return next
  }
  if (!scope.departmentId) return null
  next.departmentId = scope.departmentId
  return next
}

export function defaultDepartmentId(selectedDepartmentId = ''): string {
  const scope = currentDataScope()
  return scope.global ? selectedDepartmentId : scope.departmentId
}
