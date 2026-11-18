import { MOCK_PASSWORDS_KEY, MOCK_REGISTERED_USERS_KEY, PERMISSION_KEY, USER_KEY } from '../data/keys'
import { organizationApi } from './organization'
import type { Employee, PermissionNode, Role } from '../data/organization'
import { http, unwrapPayload, useMockData } from './client'

export interface AuthUser {
  id: string
  name: string
  account: string
  role: string
  roles: string[]
  roleIds?: string[]
  department: string
  departmentId: string
  position: string
  phone?: string
  email?: string
  permissionCodes: string[]
}
export interface LoginInput { account: string; password: string; phone: string; verificationCode: string }
export interface AuthSession { accessToken: string; user: AuthUser }
interface PasswordHashRecord { algorithm: 'PBKDF2-SHA-256'; iterations: number; salt: string; hash: string }
type StoredCredential = string | PasswordHashRecord
interface RegisteredMockUser { phone: string; password?: string; credential?: PasswordHashRecord }

const PASSWORD_HASH_ITERATIONS = 210_000
const PASSWORD_HASH_BYTES = 32

function toHex(bytes: Uint8Array): string { return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('') }
function fromHex(value: string): Uint8Array | null {
  if (!/^(?:[0-9a-f]{2})+$/i.test(value)) return null
  const bytes = new Uint8Array(value.length / 2)
  for (let index = 0; index < bytes.length; index += 1) bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16)
  return bytes
}
function getSubtleCrypto(): SubtleCrypto {
  if (!globalThis.crypto?.subtle || typeof globalThis.crypto.getRandomValues !== 'function') {
    throw new Error('当前浏览器不支持安全的 Mock 密码存储，已安全停止此操作')
  }
  return globalThis.crypto.subtle
}
async function hashPassword(password: string): Promise<PasswordHashRecord> {
  const subtle = getSubtleCrypto()
  const salt = globalThis.crypto.getRandomValues(new Uint8Array(16))
  const key = await subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await subtle.deriveBits({ name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations: PASSWORD_HASH_ITERATIONS, hash: 'SHA-256' }, key, PASSWORD_HASH_BYTES * 8)
  return { algorithm: 'PBKDF2-SHA-256', iterations: PASSWORD_HASH_ITERATIONS, salt: toHex(salt), hash: toHex(new Uint8Array(bits)) }
}
async function verifyHash(password: string, record: PasswordHashRecord): Promise<boolean> {
  const salt = fromHex(record.salt)
  const expected = fromHex(record.hash)
  if (record.algorithm !== 'PBKDF2-SHA-256' || !Number.isInteger(record.iterations) || record.iterations < 100_000 || record.iterations > 1_000_000 || !salt || salt.length < 16 || !expected || expected.length !== PASSWORD_HASH_BYTES) return false
  const subtle = getSubtleCrypto()
  const key = await subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await subtle.deriveBits({ name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations: record.iterations, hash: 'SHA-256' }, key, PASSWORD_HASH_BYTES * 8)
  const actual = new Uint8Array(bits)
  let difference = 0
  for (let index = 0; index < actual.length; index += 1) difference |= actual[index] ^ expected[index]
  return difference === 0
}
async function verifyCredential(password: string, stored: StoredCredential | undefined): Promise<{ valid: boolean; legacy: boolean }> {
  if (typeof stored === 'string') return { valid: stored === password, legacy: true }
  if (!stored) return { valid: false, legacy: false }
  return { valid: await verifyHash(password, stored), legacy: false }
}

type RawUser = Record<string, unknown>

function readRegisteredUsers(): RegisteredMockUser[] {
  try { return JSON.parse(localStorage.getItem(MOCK_REGISTERED_USERS_KEY) ?? '[]') as RegisteredMockUser[] } catch { return [] }
}
function readMockPasswords(): Record<string, StoredCredential> {
  try { return JSON.parse(localStorage.getItem(MOCK_PASSWORDS_KEY) ?? '{}') as Record<string, StoredCredential> } catch { return {} }
}
function normalizeUser(value: unknown): AuthUser {
  const user = value as RawUser
  const roles = Array.isArray(user.roles) ? user.roles.map((role) => typeof role === 'string' ? role : String((role as RawUser).roleName ?? (role as RawUser).name ?? '')) : []
  const permissionCodes = Array.isArray(user.permissionCodes) ? user.permissionCodes.map(String) : Array.isArray(user.perms) ? user.perms.map(String) : []
  return {
    id: String(user.id ?? user.userId ?? user.user_id ?? ''), name: String(user.name ?? user.realName ?? user.nickName ?? user.nick_name ?? user.account ?? user.username ?? user.userName ?? 'CRM 用户'),
    account: String(user.account ?? user.username ?? user.userName ?? user.user_name ?? ''), role: String(user.role ?? user.roleName ?? roles.join(' / ')),
    roles: roles.length ? roles : user.role ? [String(user.role)] : [],
    roleIds: Array.isArray(user.roleIds) ? user.roleIds.map(String) : [],
    department: String(user.department ?? user.deptName ?? user.dept_name ?? ''),
    departmentId: String(user.departmentId ?? user.deptId ?? user.dept_id ?? ''),
    position: String(user.position ?? user.postName ?? user.postNames ?? ''),
    phone: String(user.phone ?? user.phonenumber ?? user.phoneNumber ?? ''), email: String(user.email ?? ''), permissionCodes,
  }
}
function normalizeSession(value: unknown): AuthSession {
  const response = value as RawUser
  return { accessToken: String(response.accessToken ?? response.access_token ?? response.token ?? ''), user: normalizeUser(response.user ?? response.userInfo ?? response.user_info ?? response) }
}
function flattenPermissionTree(nodes: PermissionNode[], output: PermissionNode[] = []): PermissionNode[] {
  for (const node of nodes) { output.push(node); if (node.children) flattenPermissionTree(node.children, output) }
  return output
}
function effectiveMockPermissions(roleIds: string[], roles: Role[], permissionTree: PermissionNode[]): string[] {
  const assigned = roles.filter((role) => roleIds.includes(role.id) && role.status === '启用')
  if (assigned.some((role) => role.key === 'admin' || role.menuIds.includes('all'))) return ['*']
  const selectedMenus = new Set(assigned.flatMap((role) => role.menuIds))
  return [...new Set(flattenPermissionTree(permissionTree).filter((node) => selectedMenus.has(node.id) && node.permission).map((node) => node.permission))]
}
async function makeMockUser(account: string, employee?: Employee, registeredPhone?: string, fallbackRoleIds: string[] = []): Promise<AuthUser> {
  const [allRoles, permissionTree, departments] = await Promise.all([organizationApi.roles(), organizationApi.menuTree(), organizationApi.departments()])
  const defaultSalesRole = allRoles.find((role) => role.key === 'sales' && role.status === '启用')
  const roleIds = employee?.roleIds ?? (fallbackRoleIds.length ? fallbackRoleIds : defaultSalesRole ? [defaultSalesRole.id] : [])
  const assignedRoles = allRoles.filter((role) => roleIds.includes(role.id) && role.status === '启用')
  const department = employee ? departments.find((item) => item.id === employee.departmentId)?.name ?? '未分配部门' : '新注册账号'
  const roleNames = assignedRoles.map((role) => role.name)
  return {
    id: employee?.id ?? registeredPhone ?? account,
    name: employee?.name ?? '新注册用户',
    account,
    role: roleNames.join(' / ') || '未分配角色',
    roles: roleNames,
    roleIds,
    department,
    departmentId: employee?.departmentId ?? '',
    position: employee?.position ?? '客户经理',
    phone: employee?.phone ?? registeredPhone ?? '',
    email: employee?.email ?? '',
    permissionCodes: effectiveMockPermissions(roleIds, allRoles, permissionTree),
  }
}
async function makeMockSession(account: string, employee?: Employee, registeredPhone?: string, roleIds: string[] = []): Promise<AuthSession> {
  return { accessToken: `mock-${account}-${Date.now()}`, user: await makeMockUser(account, employee, registeredPhone, roleIds) }
}
function saveMockSession(session: AuthSession): AuthSession {
  localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  localStorage.setItem(PERMISSION_KEY, JSON.stringify(session.user.permissionCodes))
  return session
}
function demoCode(): string | null { return localStorage.getItem('crm_demo_sms_code') }
function setDemoCode(): void { localStorage.setItem('crm_demo_sms_code', '2468') }

type CodeResponse = { message: string; demoCode?: string; code?: string; expireSeconds?: number }

export const authApi = {
  seedDemoCode(): void { if (useMockData) setDemoCode() },
  async sendLoginCode(input: { account: string; phone: string }): Promise<CodeResponse> {
    if (!useMockData) return unwrapPayload<CodeResponse>(await http.post('/auth/sms/send-code', input))
    await new Promise((resolve) => setTimeout(resolve, 250))
    const employees = await organizationApi.employees()
    const employee = employees.find((item) => item.account === input.account)
    const registered = readRegisteredUsers().find((item) => item.phone === input.account)
    if (!employee && !registered) throw new Error('未找到该账号，请检查账号或先注册')
    if (employee?.status === '停用') throw new Error('该员工账号已停用，请联系管理员')
    if (employee && employee.phone !== input.phone) throw new Error('手机号与账号不匹配')
    if (registered && registered.phone !== input.phone) throw new Error('手机号与账号不匹配')
    setDemoCode()
    return { message: '演示验证码已生成', demoCode: '2468' }
  },
  async sendRegistrationCode(input: { phone: string }): Promise<CodeResponse> {
    if (!useMockData) return unwrapPayload<CodeResponse>(await http.post('/auth/register/send-code', input))
    await new Promise((resolve) => setTimeout(resolve, 250))
    if (!/^\d{7,15}$/.test(input.phone)) throw new Error('请输入有效的手机号')
    const employees = await organizationApi.employees()
    if (employees.some((item) => item.phone === input.phone) || readRegisteredUsers().some((item) => item.phone === input.phone)) throw new Error('该手机号已注册')
    setDemoCode()
    return { message: '演示验证码已生成', demoCode: '2468' }
  },
  async login(input: LoginInput): Promise<AuthSession> {
    if (!useMockData) return normalizeSession(unwrapPayload(await http.post('/auth/login/code', input)))
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (demoCode() !== input.verificationCode) throw new Error('验证码不正确，请使用当前演示验证码')
    const employees = await organizationApi.employees()
    const employee = employees.find((item) => item.account === input.account)
    const registered = readRegisteredUsers().find((item) => item.phone === input.account)
    if (!employee && !registered) throw new Error('账号不存在')
    if (employee?.status === '停用') throw new Error('该员工账号已停用，请联系管理员')
    if (employee && employee.phone !== input.phone) throw new Error('手机号与账号不匹配')
    if (registered && registered.phone !== input.phone) throw new Error('手机号与账号不匹配')
    const defaultPassword = employee?.account === 'admin' ? 'admin123' : '123456'
    const passwords = readMockPasswords()
    const storedCredential = registered ? registered.credential ?? registered.password : passwords[input.account]
    const verification = !registered && storedCredential === undefined
      ? { valid: input.password === defaultPassword, legacy: false }
      : await verifyCredential(input.password, storedCredential)
    if (!verification.valid) throw new Error('账号或密码不正确')
    if (verification.legacy) {
      const credential = await hashPassword(input.password)
      if (registered) {
        delete registered.password
        registered.credential = credential
        localStorage.setItem(MOCK_REGISTERED_USERS_KEY, JSON.stringify(readRegisteredUsers().map((user) => user.phone === registered.phone ? registered : user)))
      } else {
        passwords[input.account] = credential
        localStorage.setItem(MOCK_PASSWORDS_KEY, JSON.stringify(passwords))
      }
    }
    return saveMockSession(await makeMockSession(input.account, employee, registered?.phone))
  },
  async register(input: { account: string; phone: string; verificationCode: string; password: string; confirmPassword: string }): Promise<AuthSession> {
    if (!useMockData) return normalizeSession(unwrapPayload(await http.post('/auth/register', input)))
    if (demoCode() !== input.verificationCode) throw new Error('验证码不正确')
    if (!/^\d{4}$/.test(input.verificationCode)) throw new Error('验证码必须为 4 位数字')
    if (!/^\d{7,15}$/.test(input.phone)) throw new Error('请输入有效的手机号')
    if (input.password.length < 6) throw new Error('密码至少需要 6 位')
    const [users, employees] = [readRegisteredUsers(), await organizationApi.employees()]
    if (users.some((user) => user.phone === input.phone) || employees.some((user) => user.phone === input.phone)) throw new Error('该手机号已注册')
    const credential = await hashPassword(input.password)
    localStorage.setItem(MOCK_REGISTERED_USERS_KEY, JSON.stringify([...users, { phone: input.phone, credential }]))
    return saveMockSession(await makeMockSession(input.account, undefined, input.phone))
  },
  async changePassword(input: { oldPassword: string; newPassword: string }): Promise<void> {
    if (!useMockData) { await http.post('/auth/password', input); return }
    if (input.newPassword.length < 6) throw new Error('新密码至少需要 6 位')
    const rawCurrent = localStorage.getItem(USER_KEY)
    if (!rawCurrent) throw new Error('当前登录会话无效，请重新登录')
    const current = normalizeUser(JSON.parse(rawCurrent))
    if (!current.account) throw new Error('当前登录会话无效，请重新登录')
    const registeredUsers = readRegisteredUsers()
    const registeredIndex = registeredUsers.findIndex((item) => item.phone === current.account)
    if (registeredIndex >= 0) {
      const registeredUser = registeredUsers[registeredIndex]
      const verification = await verifyCredential(input.oldPassword, registeredUser.credential ?? registeredUser.password)
      if (!verification.valid) throw new Error('当前密码不正确')
      registeredUser.credential = await hashPassword(input.newPassword)
      delete registeredUser.password
      localStorage.setItem(MOCK_REGISTERED_USERS_KEY, JSON.stringify(registeredUsers))
      return
    }
    const employee = (await organizationApi.employees()).find((item) => item.account === current.account)
    if (!employee) throw new Error('当前账号不存在')
    const passwords = readMockPasswords()
    const existingPassword = passwords[current.account] ?? (current.account === 'admin' ? 'admin123' : '123456')
    const verification = await verifyCredential(input.oldPassword, existingPassword)
    if (!verification.valid) throw new Error('当前密码不正确')
    passwords[current.account] = await hashPassword(input.newPassword)
    localStorage.setItem(MOCK_PASSWORDS_KEY, JSON.stringify(passwords))
  },
  async me(): Promise<AuthUser | null> {
    if (!useMockData) {
      const response = normalizeUser(unwrapPayload(await http.get('/auth/me')))
      localStorage.setItem(USER_KEY, JSON.stringify(response))
      localStorage.setItem(PERMISSION_KEY, JSON.stringify(response.permissionCodes))
      return response
    }
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    const saved = normalizeUser(JSON.parse(raw))
    const employees = await organizationApi.employees()
    const employee = employees.find((item) => item.id === saved.id || item.account === saved.account)
    const registered = readRegisteredUsers().find((item) => item.phone === (saved.phone || saved.account))
    const refreshed = await makeMockUser(saved.account, employee, registered?.phone, saved.roleIds)
    localStorage.setItem(USER_KEY, JSON.stringify(refreshed))
    localStorage.setItem(PERMISSION_KEY, JSON.stringify(refreshed.permissionCodes))
    return refreshed
  },
  async logout(): Promise<void> {
    if (!useMockData) await http.post('/auth/logout')
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(PERMISSION_KEY)
    localStorage.removeItem('crm_demo_sms_code')
  },
}
