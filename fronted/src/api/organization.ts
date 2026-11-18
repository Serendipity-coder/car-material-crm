import { http, unwrapPayload, useMockData } from './client'
import { departmentsSeed, employeesSeed, permissionTreeSeed, rolesSeed, type Department, type Employee, type PermissionNode, type Role } from '../data/organization'
import type { PageResult } from './types'
import { MOCK_PASSWORDS_KEY } from '../data/keys'
import { applyPermissionCatalogDraft } from '../utils/permission-catalog'

const ORG_KEY = 'crm_org_mock_v1'
interface OrgSnapshot {
  employees: Employee[]
  departments: Department[]
  roles: Role[]
}

function initialOrg(): OrgSnapshot {
  return { employees: structuredClone(employeesSeed), departments: structuredClone(departmentsSeed), roles: structuredClone(rolesSeed) }
}
function readOrg(): OrgSnapshot {
  try {
    const stored = localStorage.getItem(ORG_KEY)
    if (!stored) return initialOrg()
    const value = JSON.parse(stored) as Partial<OrgSnapshot>
    const roles = Array.isArray(value.roles) ? value.roles.map((role, index) => ({ ...role, sort: Number.isFinite(role.sort) ? role.sort : index + 1 })) : structuredClone(rolesSeed)
    const salesRole = roles.find((role) => role.key === 'sales')
    const currentSalesDefault = rolesSeed.find((role) => role.key === 'sales')?.menuIds ?? []
    const previousSalesDefault = currentSalesDefault.filter((idValue) => idValue !== 'orders')
    const isUnmodifiedPreviousSalesDefault = salesRole?.menuIds.length === previousSalesDefault.length && previousSalesDefault.every((idValue) => salesRole.menuIds.includes(idValue))
    if (salesRole && isUnmodifiedPreviousSalesDefault) salesRole.menuIds = [...salesRole.menuIds, 'orders']
    const snapshot: OrgSnapshot = {
      employees: Array.isArray(value.employees) ? value.employees : structuredClone(employeesSeed),
      departments: Array.isArray(value.departments) ? value.departments : structuredClone(departmentsSeed),
      roles,
    }
    // 增量合并 seed 中新增的部门与员工（保留用户已有修改，仅追加缺失记录，避免旧缓存看不到新增组织数据）
    let changed = Boolean(salesRole && isUnmodifiedPreviousSalesDefault)
    for (const seedEmployee of employeesSeed) {
      if (!snapshot.employees.some((item) => item.id === seedEmployee.id)) { snapshot.employees.push(structuredClone(seedEmployee)); changed = true }
    }
    for (const seedDepartment of departmentsSeed) {
      if (!snapshot.departments.some((item) => item.id === seedDepartment.id)) { snapshot.departments.push(structuredClone(seedDepartment)); changed = true }
    }
    if (changed) writeOrg(snapshot)
    return snapshot
  } catch { return initialOrg() }
}
function writeOrg(org: OrgSnapshot): void { localStorage.setItem(ORG_KEY, JSON.stringify(org)) }
function saveMockPassword(account: string, password?: string): void {
  if (!password) return
  let passwords: Record<string, string> = {}
  try { passwords = JSON.parse(localStorage.getItem(MOCK_PASSWORDS_KEY) ?? '{}') as Record<string, string> } catch { passwords = {} }
  passwords[account] = password
  localStorage.setItem(MOCK_PASSWORDS_KEY, JSON.stringify(passwords))
}
function ensureOrg(): OrgSnapshot {
  const current = readOrg()
  if (!localStorage.getItem(ORG_KEY)) writeOrg(current)
  return current
}
function id(prefix: string): string { return `${prefix}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}` }
function error(message: string): never { throw new Error(message) }

function normalizeList<T>(value: unknown): T[] {
  const raw = value as { rows?: T[]; records?: T[]; data?: T[] }
  if (Array.isArray(value)) return value as T[]
  return raw?.rows ?? raw?.records ?? raw?.data ?? []
}
type RawEntity = Record<string, unknown>
export interface EmployeePageQuery { pageNum: number; pageSize: number; keyword?: string; departmentId?: string; roleId?: string; status?: Employee['status'] }
function asText(value: unknown): string { return value === undefined || value === null ? '' : String(value) }

// ---------------- 角色产品品类授权 ----------------
export interface RoleCategoryBinding { categoryId: string; includeChildren: boolean }
export interface RoleCategories { all: boolean; bindings: RoleCategoryBinding[] }
export interface CategoryNode { id: string; parentId: string; name: string; sort: number; status: string; children: CategoryNode[] }
export interface RoleCategoryInput { all: boolean; categoryIds: string[]; includeChildren: boolean }
export interface RoleDataScopeItem { moduleKey: string; scopeType: string; scopeValue: string | null }

function normalizeRoleCategories(value: unknown): RoleCategories {
  const raw = (value ?? {}) as RawEntity
  const bindings = Array.isArray(raw.bindings) ? raw.bindings : []
  return {
    all: Boolean(raw.all),
    bindings: bindings.map((item) => {
      const binding = item as RawEntity
      return { categoryId: asText(binding.categoryId), includeChildren: binding.includeChildren !== false && binding.includeChildren !== 0 }
    }).filter((binding) => binding.categoryId),
  }
}
function normalizeDataScopeItems(value: unknown): RoleDataScopeItem[] {
  const raw = (value ?? {}) as RawEntity
  const items = Array.isArray(raw.items) ? raw.items : []
  return items.map((item) => {
    const row = item as RawEntity
    return { moduleKey: asText(row.moduleKey), scopeType: asText(row.scopeType), scopeValue: row.scopeValue === null || row.scopeValue === undefined ? null : String(row.scopeValue) }
  }).filter((item) => item.moduleKey)
}
function normalizeCategoryNode(value: unknown): CategoryNode {
  const item = (value ?? {}) as RawEntity
  const children = Array.isArray(item.children) ? item.children : []
  return {
    id: asText(item.id), parentId: asText(item.parentId), name: asText(item.name),
    sort: Number(item.sort ?? 0), status: asText(item.status), children: children.map(normalizeCategoryNode),
  }
}
function enabledStatus(value: unknown): '启用' | '停用' { return ['停用', '1', 1, false].includes(value as string | number | boolean) ? '停用' : '启用' }
function employeeStatus(value: unknown): '在职' | '停用' { return ['停用', '1', 1, false].includes(value as string | number | boolean) ? '停用' : '在职' }
function normalizeEmployee(value: unknown): Employee {
  const item = value as RawEntity
  const rawRoles = Array.isArray(item.roleIds) ? item.roleIds : Array.isArray(item.roles) ? item.roles : []
  const rawGender = asText(item.gender ?? item.sex)
  return {
    id: asText(item.id ?? item.userId ?? item.user_id), name: asText(item.name ?? item.realName ?? item.nickName ?? item.nick_name),
    account: asText(item.account ?? item.userName ?? item.username ?? item.user_name), departmentId: asText(item.departmentId ?? item.deptId ?? item.dept_id),
    position: asText(item.position ?? item.postName ?? item.postNames), roleIds: rawRoles.map((role) => typeof role === 'object' && role !== null ? asText((role as RawEntity).id ?? (role as RawEntity).roleId ?? (role as RawEntity).role_id) : asText(role)).filter(Boolean),
    phone: asText(item.phone ?? item.phonenumber ?? item.phoneNumber), email: asText(item.email), status: employeeStatus(item.status),
    hireDate: asText(item.hireDate ?? item.createTime ?? item.create_time), gender: rawGender === '0' ? '男' : rawGender === '1' ? '女' : rawGender, remark: asText(item.remark), lastLoginIp: asText(item.lastLoginIp ?? item.loginIp ?? item.login_ip),
  }
}
function normalizeRole(value: unknown): Role {
  const item = value as RawEntity
  const menuValues = Array.isArray(item.menuIds) ? item.menuIds : Array.isArray(item.menu_ids) ? item.menu_ids : Array.isArray(item.menus) ? item.menus : []
  return {
    id: asText(item.id ?? item.roleId ?? item.role_id), name: asText(item.name ?? item.roleName ?? item.role_name),
    key: asText(item.key ?? item.roleKey ?? item.role_key), sort: Number(item.sort ?? item.roleSort ?? item.role_sort ?? 0), status: enabledStatus(item.status),
    memberCount: Number(item.memberCount ?? item.userCount ?? item.member_count ?? 0),
    menuIds: menuValues.map((menu) => typeof menu === 'object' && menu !== null ? asText((menu as RawEntity).id ?? (menu as RawEntity).menuId ?? (menu as RawEntity).menu_id) : asText(menu)).filter(Boolean), remark: asText(item.remark), builtin: Boolean(item.builtin ?? item.isBuiltin ?? (item.roleKey ?? item.role_key) === 'admin'),
  }
}
function normalizeDepartmentTree(value: unknown, parentId: string | null = null, output: Department[] = []): Department[] {
  for (const valueNode of normalizeList<unknown>(value)) {
    const item = valueNode as RawEntity
    const idValue = asText(item.id ?? item.deptId ?? item.dept_id)
    output.push({ id: idValue, parentId: item.parentId === null || item.parent_id === null ? null : asText(item.parentId ?? item.parent_id ?? parentId), name: asText(item.name ?? item.deptName ?? item.dept_name), code: asText(item.code ?? item.deptCode ?? item.dept_code), leaderId: asText(item.leaderId ?? item.leaderUserId ?? item.leader_id), sort: Number(item.sort ?? item.orderNum ?? item.order_num ?? 0), phone: asText(item.phone ?? item.telephone), email: asText(item.email), status: enabledStatus(item.status) })
    if (Array.isArray(item.children)) normalizeDepartmentTree(item.children, idValue, output)
  }
  return output
}
function normalizePermissionNode(value: unknown): PermissionNode {
  const item = value as RawEntity
  const children = Array.isArray(item.children) ? item.children.map(normalizePermissionNode) : undefined
  const rawType = asText(item.type ?? item.menuType ?? item.menu_type ?? 'C').toUpperCase()
  return { id: asText(item.id ?? item.menuId ?? item.menu_id), name: asText(item.name ?? item.menuName ?? item.menu_name), type: rawType === 'M' || rawType === 'F' ? rawType : 'C', permission: asText(item.permission ?? item.perms ?? item.permissionCode), ...(children ? { children } : {}) }
}
function employeePayload(input: Partial<Employee> & { password?: string }): RawEntity {
  const payload: RawEntity = {}
  if (input.name !== undefined) payload.realName = input.name
  if (input.account !== undefined) payload.username = input.account
  if (input.departmentId !== undefined) payload.deptId = input.departmentId || '0'
  if (input.position !== undefined) payload.position = input.position
  if (input.roleIds !== undefined) payload.roleIds = input.roleIds
  if (input.gender !== undefined) payload.gender = input.gender === '男' ? '0' : input.gender === '女' ? '1' : input.gender
  if (input.hireDate !== undefined) payload.hireDate = input.hireDate
  if (input.phone !== undefined) payload.phone = input.phone
  if (input.email !== undefined) payload.email = input.email
  if (input.remark !== undefined) payload.remark = input.remark
  if (input.status) payload.status = input.status === '在职' ? '0' : '1'
  if (input.password) payload.password = input.password
  return payload
}
function departmentPayload(input: Partial<Department>): RawEntity {
  const payload: RawEntity = {}
  if (input.name !== undefined) payload.deptName = input.name
  if (input.code !== undefined) payload.deptCode = input.code
  if (input.parentId !== undefined) payload.parentId = input.parentId || '0'
  if (input.leaderId !== undefined) payload.leaderId = input.leaderId
  if (input.sort !== undefined) payload.orderNum = input.sort
  if (input.phone !== undefined) payload.phone = input.phone
  if (input.email !== undefined) payload.email = input.email
  if (input.status) payload.status = input.status === '启用' ? '0' : '1'
  return payload
}
function rolePayload(input: Partial<Role>): RawEntity {
  const payload: RawEntity = {}
  if (input.name !== undefined) payload.roleName = input.name
  if (input.key !== undefined) payload.roleKey = input.key
  if (input.sort !== undefined) payload.sort = input.sort
  if (input.status) payload.status = input.status === '启用' ? '0' : '1'
  if (input.remark !== undefined) payload.remark = input.remark
  if (input.menuIds !== undefined) payload.menuIds = input.menuIds
  return payload
}

export function departmentPath(idValue: string, departments = ensureOrg().departments): string {
  const names: string[] = []
  let current = departments.find((item) => item.id === idValue)
  const seen = new Set<string>()
  while (current && !seen.has(current.id)) {
    seen.add(current.id)
    names.unshift(current.name)
    current = current.parentId ? departments.find((item) => item.id === current?.parentId) : undefined
  }
  return names.join(' / ')
}

export function departmentDescendantIds(idValue: string, departments = ensureOrg().departments): string[] {
  const ids = new Set<string>([idValue])
  let changed = true
  while (changed) {
    changed = false
    for (const item of departments) {
      if (item.parentId && ids.has(item.parentId) && !ids.has(item.id)) {
        ids.add(item.id)
        changed = true
      }
    }
  }
  return [...ids]
}

export function departmentTree(departments = ensureOrg().departments, employees = ensureOrg().employees): Array<Department & { children: ReturnType<typeof departmentTree>; employeeCount: number; path: string }> {
  const nodes = new Map<string, Department & { children: ReturnType<typeof departmentTree>; employeeCount: number; path: string }>()
  for (const dept of departments) {
    nodes.set(dept.id, { ...dept, children: [], employeeCount: employees.filter((employee) => employee.departmentId === dept.id && employee.status === '在职').length, path: departmentPath(dept.id, departments) })
  }
  const roots: ReturnType<typeof departmentTree> = []
  for (const node of nodes.values()) {
    if (node.parentId && nodes.has(node.parentId)) nodes.get(node.parentId)!.children.push(node)
    else roots.push(node)
  }
  const sortTree = (list: typeof roots): void => {
    list.sort((a, b) => a.sort - b.sort)
    list.forEach((item) => sortTree(item.children))
  }
  sortTree(roots)
  return roots
}

export const organizationApi = {
  async employees(): Promise<Employee[]> {
    if (!useMockData) {
      const result: Employee[] = []
      const pageSize = 1000
      let total = Number.POSITIVE_INFINITY
      for (let pageNum = 1; pageNum <= 1000 && result.length < total; pageNum += 1) {
        const payload = unwrapPayload<unknown>(await http.get('/system/users', { params: { pageNum, pageSize } }))
        const rows = normalizeList<unknown>(payload).map(normalizeEmployee)
        const raw = payload as RawEntity
        result.push(...rows)
        const reportedTotal = Number(raw?.total ?? raw?.totalCount ?? raw?.totalNum)
        if (Number.isFinite(reportedTotal)) total = reportedTotal
        else if (rows.length < pageSize) total = result.length
        if (!rows.length || rows.length < pageSize && result.length >= total) break
      }
      return result
    }
    return ensureOrg().employees
  },
  async employeePage(query: EmployeePageQuery): Promise<PageResult<Employee>> {
    const keyword = query.keyword?.trim().toLowerCase() ?? ''
    if (!useMockData) {
      const params = { pageNum: query.pageNum, pageSize: query.pageSize, ...(keyword ? { keyword: query.keyword?.trim() } : {}), ...(query.departmentId ? { deptId: query.departmentId } : {}), ...(query.roleId ? { roleId: query.roleId } : {}), ...(query.status ? { status: query.status === '在职' ? '0' : '1' } : {}) }
      const payload = unwrapPayload<unknown>(await http.get('/system/users', { params }))
      const rows = normalizeList<unknown>(payload).map(normalizeEmployee)
      const raw = payload as RawEntity
      return { rows, total: Number(raw?.total ?? raw?.totalCount ?? raw?.totalNum ?? rows.length) }
    }
    const all = ensureOrg().employees.filter((employee) => {
      const matchesSearch = !keyword || [employee.name, employee.account, employee.email, employee.phone, employee.position].some((value) => String(value).toLowerCase().includes(keyword))
      return matchesSearch && (!query.departmentId || employee.departmentId === query.departmentId) && (!query.roleId || employee.roleIds.includes(query.roleId)) && (!query.status || employee.status === query.status)
    })
    const start = (query.pageNum - 1) * query.pageSize
    return { rows: all.slice(start, start + query.pageSize), total: all.length }
  },
  async employeeDetail(employeeId: string): Promise<Employee> {
    if (!useMockData) return normalizeEmployee(unwrapPayload(await http.get(`/system/users/${encodeURIComponent(employeeId)}`)))
    const employee = ensureOrg().employees.find((item) => item.id === employeeId)
    if (!employee) error('员工不存在')
    return employee
  },
  async employeeSummary(): Promise<{ total: number; active: number; departments: number; newThisMonth: number }> {
    if (!useMockData) {
      const raw = unwrapPayload<RawEntity>(await http.get('/system/users/summary'))
      return { total: Number(raw.total ?? raw.userCount ?? 0), active: Number(raw.active ?? raw.activeCount ?? raw.activeUsers ?? 0), departments: Number(raw.departments ?? raw.deptCount ?? 0), newThisMonth: Number(raw.newThisMonth ?? raw.newCount ?? 0) }
    }
    const org = ensureOrg()
    const currentMonth = new Date().toISOString().slice(0, 7)
    return { total: org.employees.length, active: org.employees.filter((item) => item.status === '在职').length, departments: org.departments.filter((item) => item.status === '启用').length, newThisMonth: org.employees.filter((item) => item.hireDate.startsWith(currentMonth)).length }
  },
  async createEmployee(input: Omit<Employee, 'id'> & { password?: string }): Promise<Employee> {
    if (!useMockData) {
      const [employees, roles] = await Promise.all([organizationApi.employees(), organizationApi.roles()])
      if (employees.some((item) => item.account.toLowerCase() === input.account.toLowerCase())) error('登录账号已存在')
      if (!input.password || input.password.length < 6) error('初始密码至少需要 6 位')
      if (input.roleIds.some((roleId) => !roles.some((role) => role.id === roleId && role.status === '启用'))) error('角色必须存在且处于启用状态')
      return normalizeEmployee(unwrapPayload(await http.post('/system/users', employeePayload(input))))
    }
    const org = ensureOrg()
    if (org.employees.some((item) => item.account.toLowerCase() === input.account.toLowerCase())) error('登录账号已存在')
    if (input.password && input.password.length < 6) error('初始密码至少需要 6 位')
    if (input.roleIds.some((roleId) => !org.roles.some((role) => role.id === roleId && role.status === '启用'))) error('角色必须存在且处于启用状态')
    const { password: _password, ...safeInput } = input
    const employee = { ...safeInput, id: id('e') } as Employee
    org.employees.unshift(employee)
    saveMockPassword(employee.account, input.password)
    for (const roleId of employee.roleIds) { const role = org.roles.find((item) => item.id === roleId); if (role) role.memberCount += 1 }
    writeOrg(org)
    return employee
  },
  async updateEmployee(employeeId: string, input: Partial<Employee> & { password?: string }): Promise<Employee> {
    if (!useMockData) {
      const [employees, roles] = await Promise.all([organizationApi.employees(), organizationApi.roles()])
      const current = employees.find((item) => item.id === employeeId)
      if (!current) error('员工不存在')
      if (input.account && input.account !== current.account) error('编辑时不能修改登录账号')
      if (input.password && input.password.length < 6) error('新密码至少需要 6 位')
      const nextRoles = input.roleIds ?? current.roleIds
      if (nextRoles.some((roleId) => !roles.some((role) => role.id === roleId && role.status === '启用'))) error('角色必须存在且处于启用状态')
      const adminRoleIds = new Set(roles.filter((role) => role.key === 'admin').map((role) => role.id))
      const wasSuperAdmin = current.roleIds.some((roleId) => adminRoleIds.has(roleId))
      const keepsAdmin = nextRoles.some((roleId) => adminRoleIds.has(roleId))
      const activeAdmins = employees.filter((item) => item.status === '在职' && item.roleIds.some((roleId) => adminRoleIds.has(roleId)))
      if (wasSuperAdmin && current.status === '在职' && input.status === '停用' && activeAdmins.length <= 1) error('不能停用最后一位启用状态的超级管理员')
      if (wasSuperAdmin && input.roleIds && !keepsAdmin && activeAdmins.length <= 1) error('不能移除最后一位启用状态的超级管理员')
      const { password, ...fields } = input
      return normalizeEmployee(unwrapPayload(await http.put(`/system/users/${encodeURIComponent(employeeId)}`, { ...employeePayload(fields), ...(password ? { password } : {}) })))
    }
    const org = ensureOrg()
    const index = org.employees.findIndex((item) => item.id === employeeId)
    if (index < 0) error('员工不存在')
    const current = org.employees[index]
    if (input.account && input.account !== current.account) error('编辑时不能修改登录账号')
    if (input.password && input.password.length < 6) error('新密码至少需要 6 位')
    const nextRoles = input.roleIds ?? current.roleIds
    if (nextRoles.some((roleId) => !org.roles.some((role) => role.id === roleId && role.status === '启用'))) error('角色必须存在且处于启用状态')
    const superAdmin = current.roleIds.some((roleId) => org.roles.find((role) => role.id === roleId)?.key === 'admin')
    const activeAdmins = org.employees.filter((item) => item.status === '在职' && item.roleIds.some((roleId) => org.roles.find((role) => role.id === roleId)?.key === 'admin'))
    if (superAdmin && current.status === '在职' && input.status === '停用' && activeAdmins.length <= 1) error('不能停用最后一位启用状态的超级管理员')
    if (superAdmin && input.roleIds && !input.roleIds.includes(current.roleIds.find((roleId) => org.roles.find((role) => role.id === roleId)?.key === 'admin')!)) {
      if (org.employees.filter((item) => item.status === '在职' && item.roleIds.some((roleId) => org.roles.find((role) => role.id === roleId)?.key === 'admin')).length <= 1) error('不能移除最后一位启用状态的超级管理员')
    }
    for (const roleId of current.roleIds) { const role = org.roles.find((item) => item.id === roleId); if (role) role.memberCount = Math.max(0, role.memberCount - 1) }
    const { password: _password, ...safeInput } = input
    org.employees[index] = { ...current, ...safeInput, roleIds: nextRoles, id: current.id }
    saveMockPassword(org.employees[index].account, input.password)
    for (const roleId of nextRoles) { const role = org.roles.find((item) => item.id === roleId); if (role) role.memberCount += 1 }
    writeOrg(org)
    return org.employees[index]
  },
  async setEmployeeStatus(ids: string[], status: Employee['status']): Promise<void> {
    if (!useMockData) {
      const [employees, roles] = await Promise.all([organizationApi.employees(), organizationApi.roles()])
      const adminRoleIds = new Set(roles.filter((role) => role.key === 'admin').map((role) => role.id))
      const stopping = employees.filter((item) => ids.includes(item.id) && item.status === '在职' && item.roleIds.some((roleId) => adminRoleIds.has(roleId)))
      const remaining = employees.filter((item) => item.status === '在职' && !ids.includes(item.id) && item.roleIds.some((roleId) => adminRoleIds.has(roleId)))
      if (status === '停用' && stopping.length && remaining.length === 0) error('不能停用最后一位启用状态的超级管理员')
      await http.patch('/system/users/status', { ids, status: status === '在职' ? '0' : '1' })
      return
    }
    const org = ensureOrg()
    const stopping = org.employees.filter((item) => ids.includes(item.id) && item.status === '在职' && item.roleIds.some((roleId) => org.roles.find((role) => role.id === roleId)?.key === 'admin'))
    const remaining = org.employees.filter((item) => item.status === '在职' && !ids.includes(item.id) && item.roleIds.some((roleId) => org.roles.find((role) => role.id === roleId)?.key === 'admin'))
    if (status === '停用' && stopping.length && remaining.length === 0) error('不能停用最后一位启用状态的超级管理员')
    org.employees = org.employees.map((item) => ids.includes(item.id) ? { ...item, status } : item)
    writeOrg(org)
  },
  async departments(): Promise<Department[]> {
    if (!useMockData) return normalizeDepartmentTree(unwrapPayload(await http.get('/system/depts/tree')))
    return ensureOrg().departments
  },
  async ownerOptions(): Promise<string[]> {
    if (!useMockData) return normalizeList<string>(unwrapPayload(await http.get('/options/owners'))).map(String).filter(Boolean)
    return ensureOrg().employees.filter((item) => item.status === '在职').map((item) => item.name)
  },
  async createDepartment(input: Omit<Department, 'id'>): Promise<Department> {
    if (!useMockData) {
      const departments = await organizationApi.departments()
      if (input.parentId && input.parentId !== '0' && !departments.some((item) => item.id === input.parentId)) error('上级部门不存在')
      return normalizeDepartmentTree([unwrapPayload(await http.post('/system/depts', departmentPayload(input)))])[0]
    }
    const org = ensureOrg()
    if (input.parentId && !org.departments.some((item) => item.id === input.parentId)) error('上级部门不存在')
    const dept = { ...input, id: id('d') }
    org.departments.push(dept)
    writeOrg(org)
    return dept
  },
  async updateDepartment(departmentId: string, input: Partial<Department>): Promise<Department> {
    if (!useMockData) {
      const departments = await organizationApi.departments()
      if (!departments.some((item) => item.id === departmentId)) error('部门不存在')
      if (input.parentId && input.parentId !== '0' && departmentDescendantIds(departmentId, departments).includes(input.parentId)) error('上级部门不能选择自身或其下级部门')
      return normalizeDepartmentTree([unwrapPayload(await http.put(`/system/depts/${encodeURIComponent(departmentId)}`, departmentPayload(input)))])[0]
    }
    const org = ensureOrg()
    const index = org.departments.findIndex((item) => item.id === departmentId)
    if (index < 0) error('部门不存在')
    if (input.parentId) {
      const invalidParents = departmentDescendantIds(departmentId, org.departments)
      if (invalidParents.includes(input.parentId)) error('上级部门不能选择自身或其下级部门')
    }
    org.departments[index] = { ...org.departments[index], ...input, id: departmentId }
    writeOrg(org)
    return org.departments[index]
  },
  async deleteDepartment(departmentId: string): Promise<void> {
    if (!useMockData) {
      const [departments, employees] = await Promise.all([organizationApi.departments(), organizationApi.employees()])
      if (departments.some((item) => item.parentId === departmentId)) error('该部门存在下级部门，请先迁移或删除下级部门')
      if (employees.some((item) => item.departmentId === departmentId)) error('该部门仍有直属员工，请先迁移员工')
      await http.delete(`/system/depts/${encodeURIComponent(departmentId)}`)
      return
    }
    const org = ensureOrg()
    if (org.departments.some((item) => item.parentId === departmentId)) error('该部门存在下级部门，请先迁移或删除下级部门')
    if (org.employees.some((item) => item.departmentId === departmentId)) error('该部门仍有直属员工，请先迁移员工')
    org.departments = org.departments.filter((item) => item.id !== departmentId)
    writeOrg(org)
  },
  async roles(): Promise<Role[]> {
    const sortRoles = (items: Role[]): Role[] => [...items].sort((a, b) => a.sort - b.sort || a.name.localeCompare(b.name, 'zh-CN'))
    if (!useMockData) return sortRoles(normalizeList<unknown>(unwrapPayload(await http.get('/system/roles'))).map(normalizeRole))
    return sortRoles(ensureOrg().roles)
  },
  async role(roleId: string): Promise<Role> {
    if (!useMockData) {
      const payload = unwrapPayload<RawEntity>(await http.get(`/system/roles/${encodeURIComponent(roleId)}`))
      const raw = (payload.role ?? payload.data ?? payload) as RawEntity
      if (!Array.isArray(raw.menuIds) && !Array.isArray(raw.menu_ids) && !Array.isArray(raw.menus)) error('角色详情接口未返回 menuIds/menus，已阻止打开授权编辑，避免覆盖现有权限')
      return normalizeRole(raw)
    }
    const role = ensureOrg().roles.find((item) => item.id === roleId)
    if (!role) error('角色不存在')
    return role
  },
  async createRole(input: Omit<Role, 'id' | 'memberCount' | 'menuIds'>): Promise<Role> {
    if (!useMockData) {
      const roles = await organizationApi.roles()
      if (!/^[a-z][a-z0-9_-]*$/.test(input.key)) error('角色标识需以小写字母开头，且只能包含小写字母、数字、下划线或连字符')
      if (roles.some((item) => item.key === input.key)) error('角色标识已存在')
      return normalizeRole(unwrapPayload(await http.post('/system/roles', rolePayload(input))))
    }
    const org = ensureOrg()
    if (!/^[a-z][a-z0-9_-]*$/.test(input.key)) error('角色标识需以小写字母开头，且只能包含小写字母、数字、下划线或连字符')
    if (org.roles.some((item) => item.key === input.key)) error('角色标识已存在')
    const role: Role = { ...input, id: id('r'), memberCount: 0, menuIds: [] }
    org.roles.push(role)
    writeOrg(org)
    return role
  },
  async updateRole(roleId: string, input: Partial<Role>): Promise<Role> {
    if (!useMockData) {
      const roles = await organizationApi.roles()
      const role = roles.find((item) => item.id === roleId)
      if (!role) error('角色不存在')
      if (role.key === 'admin' && input.status === '停用') error('内置超级管理员角色不可停用')
      if (role.key === 'admin' && input.key && input.key !== role.key) error('内置超级管理员角色标识不可修改')
      if (input.key && input.key !== role.key) {
        if (!/^[a-z][a-z0-9_-]*$/.test(input.key)) error('角色标识格式不正确')
        if (roles.some((item) => item.id !== roleId && item.key === input.key)) error('角色标识已存在')
      }
      return normalizeRole(unwrapPayload(await http.put(`/system/roles/${encodeURIComponent(roleId)}`, rolePayload(input))))
    }
    const org = ensureOrg()
    const index = org.roles.findIndex((item) => item.id === roleId)
    if (index < 0) error('角色不存在')
    const role = org.roles[index]
    if (role.key === 'admin' && input.status === '停用') error('内置超级管理员角色不可停用')
    if (role.key === 'admin' && input.key && input.key !== role.key) error('内置超级管理员角色标识不可修改')
    if (input.key && input.key !== role.key) {
      if (!/^[a-z][a-z0-9_-]*$/.test(input.key)) error('角色标识格式不正确')
      if (org.roles.some((item) => item.id !== roleId && item.key === input.key)) error('角色标识已存在')
    }
    org.roles[index] = { ...role, ...input, id: roleId, ...(role.key === 'admin' ? { status: '启用' as const, key: 'admin' } : {}) }
    writeOrg(org)
    return org.roles[index]
  },
  async deleteRole(roleId: string): Promise<void> {
    if (!useMockData) {
      const [roles, employees] = await Promise.all([organizationApi.roles(), organizationApi.employees()])
      const role = roles.find((item) => item.id === roleId)
      if (!role) error('角色不存在')
      if (role.key === 'admin') error('内置超级管理员角色不可删除')
      if (role.memberCount > 0 || employees.some((item) => item.roleIds.includes(roleId))) error('该角色已分配给员工，不能删除')
      await http.delete(`/system/roles/${encodeURIComponent(roleId)}`)
      return
    }
    const org = ensureOrg()
    const role = org.roles.find((item) => item.id === roleId)
    if (!role) error('角色不存在')
    if (role.key === 'admin') error('内置超级管理员角色不可删除')
    if (org.employees.some((item) => item.roleIds.includes(roleId))) error('该角色已分配给员工，不能删除')
    org.roles = org.roles.filter((item) => item.id !== roleId)
    writeOrg(org)
  },
  async assignMenus(roleId: string, menuIds: string[]): Promise<Role> {
    if (!useMockData) {
      const [roles, permissionNodes] = await Promise.all([organizationApi.roles(), organizationApi.menuTree()])
      const role = roles.find((item) => item.id === roleId)
      if (!role) error('角色不存在')
      const allIds = new Set<string>()
      const collect = (nodes: PermissionNode[]): void => nodes.forEach((node) => { allIds.add(node.id); if (node.children) collect(node.children) })
      collect(permissionNodes)
      if (role.key === 'admin' && [...allIds].some((idValue) => !menuIds.includes(idValue))) error('超级管理员角色必须保留全部权限')
      return normalizeRole(unwrapPayload(await http.put(`/system/roles/${encodeURIComponent(roleId)}/menus`, { menuIds: [...new Set(menuIds)] })))
    }
    const org = ensureOrg()
    const role = org.roles.find((item) => item.id === roleId)
    if (!role) error('角色不存在')
    role.menuIds = role.key === 'admin' ? ['all'] : [...new Set(menuIds)]
    writeOrg(org)
    return role
  },
  async menuTree(): Promise<PermissionNode[]> {
    if (!useMockData) return normalizeList<unknown>(unwrapPayload(await http.get('/system/menus/tree'))).map(normalizePermissionNode)
    return applyPermissionCatalogDraft(structuredClone(permissionTreeSeed))
  },
  async roleCategories(roleId: string): Promise<RoleCategories> {
    if (!useMockData) return normalizeRoleCategories(unwrapPayload(await http.get(`/system/roles/${encodeURIComponent(roleId)}/categories`)))
    const role = ensureOrg().roles.find((item) => item.id === roleId)
    return { all: role?.key === 'admin', bindings: [] }
  },
  async saveRoleCategories(roleId: string, input: RoleCategoryInput): Promise<RoleCategories> {
    if (!useMockData) {
      return normalizeRoleCategories(unwrapPayload(await http.put(`/system/roles/${encodeURIComponent(roleId)}/categories`, {
        all: input.all,
        categoryIds: [...new Set(input.categoryIds)],
        includeChildren: input.includeChildren,
      })))
    }
    return { all: input.all, bindings: input.all ? [] : input.categoryIds.map((categoryId) => ({ categoryId, includeChildren: input.includeChildren })) }
  },
  async roleDataScopes(roleId: string): Promise<RoleDataScopeItem[]> {
    if (!useMockData) return normalizeDataScopeItems(unwrapPayload(await http.get(`/system/roles/${encodeURIComponent(roleId)}/data-scopes`)))
    return []
  },
  async saveRoleDataScopes(roleId: string, items: RoleDataScopeItem[]): Promise<RoleDataScopeItem[]> {
    if (!useMockData) return normalizeDataScopeItems(unwrapPayload(await http.put(`/system/roles/${encodeURIComponent(roleId)}/data-scopes`, { items })))
    return items
  },
  async roleCategoryTree(): Promise<CategoryNode[]> {
    if (!useMockData) return normalizeList<unknown>(unwrapPayload(await http.get('/system/roles/categories/tree'))).map(normalizeCategoryNode)
    return []
  },
  async setRoleStatus(roleId: string, status: Role['status']): Promise<Role> {
    return this.updateRole(roleId, { status })
  },
}
