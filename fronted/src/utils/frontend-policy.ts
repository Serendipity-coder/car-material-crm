export type FrontendDataScope = '本人' | '本部门' | '本部门及下级' | '全部可见范围'

export interface FrontendRolePolicy {
  categoryMode: 'all' | 'selected'
  categoryNames: string[]
  dataScope: FrontendDataScope
  viewSensitivePrices: boolean
  /** Older local drafts are not an explicit price grant until saved again. */
  viewSensitivePricesConfirmed?: boolean
}

export interface EffectiveFrontendPolicy {
  configured: boolean
  constrainedCategories: boolean
  categoryNames: string[]
  dataScope: FrontendDataScope
  viewSensitivePrices: boolean
}

const STORAGE_KEY = 'crm_frontend_policy_draft_v1'
const CHANGE_EVENT = 'crm:frontend-policy-change'

type PolicyMap = Record<string, FrontendRolePolicy>

function emptyPolicyMap(): PolicyMap { return {} }

export function loadFrontendPolicies(): PolicyMap {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')
    return value && typeof value === 'object' && !Array.isArray(value) ? value as PolicyMap : emptyPolicyMap()
  } catch { return emptyPolicyMap() }
}

export function getFrontendRolePolicy(roleId: string): FrontendRolePolicy | null {
  return loadFrontendPolicies()[roleId] ?? null
}

export function saveFrontendRolePolicy(roleId: string, policy: FrontendRolePolicy): void {
  const policies = loadFrontendPolicies()
  policies[roleId] = {
    categoryMode: policy.categoryMode === 'selected' ? 'selected' : 'all',
    categoryNames: [...new Set(policy.categoryNames.map((name) => String(name).trim()).filter(Boolean))],
    dataScope: policy.dataScope,
    viewSensitivePrices: Boolean(policy.viewSensitivePrices),
    viewSensitivePricesConfirmed: true,
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(policies))
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT))
}

export function subscribeFrontendPolicyChange(callback: () => void): () => void {
  const handle = () => callback()
  window.addEventListener(CHANGE_EVENT, handle)
  window.addEventListener('storage', handle)
  return () => {
    window.removeEventListener(CHANGE_EVENT, handle)
    window.removeEventListener('storage', handle)
  }
}

export function resolveFrontendPolicy(roleIds: string[] = []): EffectiveFrontendPolicy {
  const policies = loadFrontendPolicies()
  const assigned = [...new Set(roleIds)].map((id) => policies[id]).filter((policy): policy is FrontendRolePolicy => Boolean(policy))
  const hasAllCategories = assigned.some((policy) => policy.categoryMode === 'all')
  const restrictedCategories = assigned.filter((policy) => policy.categoryMode === 'selected')
  const categoryNames = [...new Set(restrictedCategories.flatMap((policy) => policy.categoryNames))]
  const scopeOrder: FrontendDataScope[] = ['本人', '本部门', '本部门及下级', '全部可见范围']
  const dataScope = assigned.length ? assigned.reduce<FrontendDataScope>((widest, policy) => scopeOrder.indexOf(policy.dataScope) > scopeOrder.indexOf(widest) ? policy.dataScope : widest, '本人') : '本部门'
  return {
    configured: assigned.length > 0,
    constrainedCategories: assigned.length > 0 && restrictedCategories.length > 0 && !hasAllCategories,
    categoryNames,
    dataScope,
    // The frontend preview follows permission-union behavior across assigned roles.
    viewSensitivePrices: assigned.some((policy) => policy.viewSensitivePricesConfirmed === true && policy.viewSensitivePrices),
  }
}

function normalized(value: unknown): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value).trim().toLocaleLowerCase()
  return ''
}

function recordValues(record: Record<string, unknown>, keys: string[]): string[] {
  return keys.flatMap((key) => {
    const value = record[key]
    if (typeof value === 'string' || typeof value === 'number') return [normalized(value)].filter(Boolean)
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const nested = value as Record<string, unknown>
      return [normalized(nested.id), normalized(nested.departmentId), normalized(nested.deptId), normalized(nested.name), normalized(nested.departmentName)].filter(Boolean)
    }
    return []
  })
}

/** Applies only a browser-side narrowing to records already returned by authorized APIs. */
export function recordInFrontendDataScope(
  row: Record<string, unknown>,
  policy: EffectiveFrontendPolicy,
  user: unknown,
  departments: unknown[] = [],
): boolean {
  if (!policy.configured || policy.dataScope === '全部可见范围') return true
  if (!user || typeof user !== 'object') return false
  const currentUser = user as Record<string, unknown>
  if (policy.dataScope === '本人') {
    const identity = recordValues(currentUser, ['id', 'userId', 'account', 'username', 'phone', 'name', 'realName'])
    const owner = recordValues(row, ['owner', 'ownerName', 'assignee', 'assignedTo', 'createdBy', 'creator', 'salesperson', 'userId', 'ownerId', 'assigneeId', 'createdById'])
    return identity.length > 0 && owner.some((value) => identity.includes(value))
  }

  const ownDepartment = recordValues(currentUser, ['departmentId', 'deptId', 'department_id', 'dept_id', 'departmentName', 'department'])
  if (!ownDepartment.length) return false
  const allowedDepartments = new Set(ownDepartment)

  if (policy.dataScope === '本部门及下级') {
    const rows = departments.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
    let changed = true
    let passes = 0
    while (changed && passes <= rows.length) {
      changed = false
      passes += 1
      for (const department of rows) {
        const parent = recordValues(department, ['parentId', 'parent_id', 'parentDeptId', 'parent']).filter((value) => value !== '0' && value !== 'null')
        const child = recordValues(department, ['id', 'departmentId', 'deptId', 'name', 'departmentName'])
        if (parent.some((value) => allowedDepartments.has(value))) {
          for (const value of child) if (!allowedDepartments.has(value)) { allowedDepartments.add(value); changed = true }
        }
      }
    }
  }

  const rowDepartment = recordValues(row, ['departmentId', 'deptId', 'department_id', 'dept_id', 'departmentName', 'department'])
  return rowDepartment.some((value) => allowedDepartments.has(value))
}

export function categoryIsVisible(categoryName: unknown, policy: EffectiveFrontendPolicy): boolean {
  if (!policy.constrainedCategories) return true
  return policy.categoryNames.includes(String(categoryName ?? ''))
}

const sensitiveMoneyFieldNames = new Set([
  'price', 'unitprice', 'standardprice', 'customerprice', 'customerunitprice', 'costprice', 'unitcost', 'cost',
  'amount', 'totalamount', 'paidamount', 'receivedamount', 'paymentamount', 'receivableamount', 'unpaidamount',
  'received', 'paid', 'annualvalue', 'targetamount', 'balance', 'creditbalance', 'revenue', 'salesamount',
  'salesrevenue', 'profit', 'grossmargin', 'margin', 'totalprice', 'grandtotal', 'subtotal', 'money', 'taxamount', 'discountamount',
])

export function isSensitiveMoneyFieldKey(key: string): boolean {
  const normalizedKey = key.replace(/[\s_-]/g, '').toLocaleLowerCase()
  return sensitiveMoneyFieldNames.has(normalizedKey) || /(?:price|amount|cost|margin|profit|revenue|balance|money)$/.test(normalizedKey)
}

/** Removes nested financial values from UI/export payloads without mutating API records. */
export function redactSensitiveMoneyFields(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redactSensitiveMoneyFields).filter((item) => item !== undefined)
  if (!value || typeof value !== 'object') return value
  const visibleEntries = Object.entries(value as Record<string, unknown>)
    .filter(([key]) => !isSensitiveMoneyFieldKey(key))
    .map(([key, nested]) => [key, redactSensitiveMoneyFields(nested)] as const)
    .filter((entry): entry is readonly [string, Exclude<unknown, undefined>] => entry[1] !== undefined)
  return visibleEntries.length ? Object.fromEntries(visibleEntries) : undefined
}

export function policyStorageKey(): string { return STORAGE_KEY }
