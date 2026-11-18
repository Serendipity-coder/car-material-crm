import { getSeedData, getSupplementalSeedData } from '../data/seed'
import { MOCK_DATA_KEY, MOCK_SEED_VERSION_KEY } from '../data/keys'
import type { CrmRecord, PageResult, QueryState } from './types'
import { http, unwrapPayload, useMockData } from './client'
import { currentDataScope, scopedDepartmentFilters } from '../utils/data-scope'

const resourcePaths: Record<string, string> = {
  'reports-sales': 'reports/sales',
  'reports-customer-purchase': 'reports/customer-purchase',
  'reports-product-sales': 'reports/product-sales',
}

let cachedSerialized: string | null = null
let cachedRecords: Record<string, CrmRecord[]> | null = null
const MOCK_SEED_VERSION = 4

function resolvePath(key: string): string {
  return resourcePaths[key] ?? key
}

function organizationSnapshot(): { employees?: Array<{ id?: string; account?: string; name?: string; departmentId?: string }> } {
  try { return JSON.parse(localStorage.getItem('crm_org_mock_v1') ?? '{}') as ReturnType<typeof organizationSnapshot> } catch { return {} }
}

function inferDepartmentId(key: string, row: CrmRecord, data: Record<string, CrmRecord[]>): string {
  const direct = String(row.departmentId ?? row.deptId ?? row.dept_id ?? '').trim()
  if (direct) return direct
  const seeded = getSeedData(key).find((item) => item.id === row.id)?.departmentId
  if (seeded) return String(seeded)
  const owner = String(row.owner ?? row.assignee ?? '')
  const employee = organizationSnapshot().employees?.find((item) => item.name === owner || item.account === owner || item.id === owner)
  if (employee?.departmentId) return employee.departmentId
  const customerName = String(row.customer ?? row.company ?? '')
  const customer = (data.customers ?? []).find((item) => String(item.name ?? '') === customerName)
  if (customer?.departmentId) return String(customer.departmentId)
  const categoryName = String(row.category ?? '')
  const category = (data['product-categories'] ?? []).find((item) => String(item.name ?? '') === categoryName)
  if (category?.departmentId) return String(category.departmentId)
  const orderNo = String(row.orderNo ?? '')
  const order = (data.orders ?? []).find((item) => String(item.orderNo ?? '') === orderNo)
  if (order?.departmentId) return String(order.departmentId)
  const opportunityName = String(row.opportunity ?? '')
  const opportunity = (data.opportunities ?? []).find((item) => String(item.name ?? '') === opportunityName)
  if (opportunity?.departmentId) return String(opportunity.departmentId)
  if (key === 'reports-sales') return 'd2'
  return ''
}

function migrateDepartmentIds(data: Record<string, CrmRecord[]>): boolean {
  let changed = false
  const priority = ['customers', 'product-categories', 'leads', 'pool', 'contacts', 'followups', 'products', 'customer-prices', 'opportunities', 'quotes', 'orders', 'returns', 'shipments', 'payments', 'receivables', 'service-tickets', 'visits', 'reports-sales', 'reports-customer-purchase', 'reports-product-sales']
  for (const key of priority) {
    data[key] = (data[key] ?? []).map((row) => {
      if (row.departmentId || row.deptId || row.dept_id) return row
      const departmentId = inferDepartmentId(key, row, data)
      if (!departmentId) return row
      changed = true
      return { ...row, departmentId }
    })
  }
  return changed
}

function migrateLegacySalesReports(data: Record<string, CrmRecord[]>): boolean {
  const rows = data['reports-sales'] ?? []
  const groups = new Map<string, CrmRecord[]>()
  for (const row of getSeedData('reports-sales')) {
    const baseId = String(row.id).replace(/-d[234]$/, '')
    groups.set(baseId, [...(groups.get(baseId) ?? []), row])
  }

  const migrated: CrmRecord[] = []
  let changed = false
  for (const row of rows) {
    const id = String(row.id)
    const legacyGroup = groups.get(id)
    if (legacyGroup) {
      migrated.push(...legacyGroup)
      changed = true
      continue
    }
    migrated.push(row)
  }

  if (changed) data['reports-sales'] = migrated
  return changed
}

function readAll(): Record<string, CrmRecord[]> {
  const serialized = localStorage.getItem(MOCK_DATA_KEY)
  const storedVersion = Number(localStorage.getItem(MOCK_SEED_VERSION_KEY) ?? 0)
  const seedMigrationNeeded = !Number.isFinite(storedVersion) || storedVersion < MOCK_SEED_VERSION
  if (serialized && serialized === cachedSerialized && cachedRecords && !seedMigrationNeeded) return cachedRecords

  let changed = !serialized
  let current: Record<string, CrmRecord[]> = {}
  try {
    const parsed: unknown = serialized ? JSON.parse(serialized) : {}
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) current = parsed as Record<string, CrmRecord[]>
    else changed = true
  } catch { changed = true }

  for (const key of Object.keys(awaitSeedCatalog)) {
    if (!Array.isArray(current[key])) { current[key] = getSeedData(key); changed = true }
  }
  const legacyDemoData = Object.values(current).flat().some((row) => {
    const text = JSON.stringify(row)
    return ['云杉科技', '远山医疗', '星河制造', '青禾零售', '知行教育', 'CRM 云平台', '智能数据采集'].some((marker) => text.includes(marker))
  })
  if (seedMigrationNeeded && legacyDemoData) {
    current = {}
    for (const key of Object.keys(awaitSeedCatalog)) current[key] = [...getSeedData(key), ...getSupplementalSeedData(key)]
    changed = true
  } else if (seedMigrationNeeded) {
    for (const key of Object.keys(awaitSeedCatalog)) {
      const existingIds = new Set(current[key].map((row) => String(row.id ?? '')))
      // 给已有记录补充 seed 中的 createdAt 字段（仅当记录缺少该字段，用于「本月新增」统计）
      const seedById = new Map([...getSeedData(key), ...getSupplementalSeedData(key)].map((row) => [String(row.id ?? ''), row]))
      const supplemented = current[key].map((row) => {
        const seed = seedById.get(String(row.id ?? ''))
        if (seed && seed.createdAt && !row.createdAt) return { ...row, createdAt: seed.createdAt }
        return row
      })
      if (supplemented.some((row, index) => row !== current[key][index])) { current[key] = supplemented; changed = true }
      const additions = getSupplementalSeedData(key).filter((row) => !existingIds.has(String(row.id ?? '')))
      if (additions.length) {
        current[key] = [...current[key], ...additions]
        changed = true
      }
    }
    localStorage.setItem(MOCK_SEED_VERSION_KEY, String(MOCK_SEED_VERSION))
  }
  if (migrateLegacySalesReports(current)) changed = true
  if (migrateDepartmentIds(current)) changed = true
  if (changed) {
    cachedSerialized = JSON.stringify(current)
    localStorage.setItem(MOCK_DATA_KEY, cachedSerialized)
  } else cachedSerialized = serialized
  cachedRecords = current
  return current
}

const awaitSeedCatalog: Record<string, true> = {
  todos: true, leads: true, pool: true, customers: true, contacts: true, followups: true,
  'product-categories': true, products: true, 'customer-prices': true, opportunities: true,
  quotes: true, orders: true, returns: true, shipments: true, payments: true, receivables: true,
  'service-tickets': true, visits: true, 'reports-sales': true,
  'reports-customer-purchase': true, 'reports-product-sales': true,
}

function writeAll(data: Record<string, CrmRecord[]>): void {
  cachedSerialized = JSON.stringify(data)
  cachedRecords = data
  localStorage.setItem(MOCK_DATA_KEY, cachedSerialized)
}

function matchesSearch(record: CrmRecord, search: string): boolean {
  const term = search.trim().toLowerCase()
  if (!term) return true
  return Object.values(record).some((value) => String(value ?? '').toLowerCase().includes(term))
}

function getFiltered(key: string, query: QueryState): CrmRecord[] {
  const filters = scopedDepartmentFilters(key, query.filters)
  if (!filters) return []
  let rows = readAll()[key] ?? []
  if (query.search) rows = rows.filter((row) => matchesSearch(row, query.search!))
  for (const [field, value] of Object.entries(filters)) {
    if (value) rows = rows.filter((row) => String(row[field] ?? '') === value)
  }
  if (query.sortBy) {
    const factor = query.sortOrder === 'descending' ? -1 : 1
    rows = [...rows].sort((a, b) => String(a[query.sortBy!] ?? '').localeCompare(String(b[query.sortBy!] ?? ''), 'zh-CN', { numeric: true }) * factor)
  }
  return rows
}

export const moduleApi = {
  async list(key: string, query: QueryState): Promise<PageResult<CrmRecord>> {
    const filters = scopedDepartmentFilters(key, query.filters)
    if (!filters) return { rows: [], total: 0 }
    if (!useMockData) {
      const params = { pageNum: query.page, pageSize: query.pageSize, keyword: query.search, ...filters, sortBy: query.sortBy, sortOrder: query.sortOrder }
      const raw = unwrapPayload<Record<string, unknown>>(await http.get(`/${resolvePath(key)}`, { params }))
      const rows = (raw.rows ?? raw.records ?? raw.list ?? raw.items ?? raw) as CrmRecord[]
      return { rows: Array.isArray(rows) ? rows : [], total: Number(raw.total ?? raw.totalCount ?? (Array.isArray(rows) ? rows.length : 0)) }
    }
    const rows = getFiltered(key, query)
    const start = (query.page - 1) * query.pageSize
    return { rows: rows.slice(start, start + query.pageSize), total: rows.length }
  },
  async listWithAll(key: string, query: QueryState): Promise<{ page: PageResult<CrmRecord>; all: CrmRecord[] }> {
    if (useMockData) {
      const all = getFiltered(key, query)
      const start = (query.page - 1) * query.pageSize
      return { page: { rows: all.slice(start, start + query.pageSize), total: all.length }, all }
    }
    const [page, all] = await Promise.all([
      moduleApi.list(key, query),
      moduleApi.all(key, { search: query.search, filters: query.filters }),
    ])
    return { page, all }
  },
  async all(key: string, query: Partial<QueryState> = {}, extraParams: Record<string, string> = {}): Promise<CrmRecord[]> {
    const allQuery: QueryState = { page: 1, pageSize: 1000, ...query }
    const filters = scopedDepartmentFilters(key, { ...allQuery.filters, ...extraParams })
    if (!filters) return []
    if (!useMockData) {
      const rows: CrmRecord[] = []
      const seen = new Set<string>()
      let pageNum = 1
      let total: number | undefined
      while (pageNum <= 10000) {
        const params = { pageNum, pageSize: allQuery.pageSize, keyword: allQuery.search, ...filters }
        const raw = unwrapPayload<CrmRecord[] | { rows?: CrmRecord[]; records?: CrmRecord[]; list?: CrmRecord[]; items?: CrmRecord[]; total?: number | string; totalCount?: number | string }>(await http.get(`/${resolvePath(key)}`, { params }))
        const pageRows = Array.isArray(raw) ? raw : raw.rows ?? raw.records ?? raw.list ?? raw.items ?? []
        if (!Array.isArray(pageRows) || !pageRows.length) break
        const rawTotal = Array.isArray(raw) ? undefined : raw.total ?? raw.totalCount
        if (rawTotal !== undefined && Number.isFinite(Number(rawTotal))) total = Number(rawTotal)
        let added = 0
        for (const row of pageRows) {
          const recordKey = String(row.id ?? JSON.stringify(row))
          if (seen.has(recordKey)) continue
          seen.add(recordKey)
          rows.push(row)
          added += 1
        }
        if (total !== undefined && total > 0 && rows.length >= total) break
        if (!added) break
        pageNum += 1
      }
      if (pageNum > 10000) throw new Error('资源分页超过 10000 页，无法保证完整读取')
      if (total !== undefined && total > rows.length) throw new Error(`资源分页仅返回 ${rows.length} 条记录，后端报告总数为 ${total}`)
      return rows
    }
    return getFiltered(key, allQuery)
  },
  async create(key: string, input: Partial<CrmRecord>): Promise<CrmRecord> {
    const scope = currentDataScope()
    const scopedInput = { ...input }
    if (key !== 'todos') {
      if (!scope.global && !scope.departmentId) throw new Error('当前账号未关联部门，不能创建部门业务数据，请联系管理员分配部门')
      if (!scope.global) scopedInput.departmentId = scope.departmentId
      if (scope.global && !scopedInput.departmentId) throw new Error('请先选择新记录所属部门')
    }
    if (!useMockData) return unwrapPayload<CrmRecord>(await http.post(`/${resolvePath(key)}`, scopedInput))
    const data = readAll()
    if (key === 'products') {
      const category = (data['product-categories'] ?? []).find((row) => String(row.name ?? '') === String(scopedInput.category ?? ''))
      if (!category || String(category.departmentId ?? '') !== String(scopedInput.departmentId ?? '')) throw new Error('产品分类必须归属于所选部门')
    }
    if (key === 'product-categories' && scopedInput.parent && scopedInput.parent !== '—') {
      const parent = (data['product-categories'] ?? []).find((row) => String(row.name ?? '') === String(scopedInput.parent ?? ''))
      if (parent && String(parent.departmentId ?? '') !== String(scopedInput.departmentId ?? '')) throw new Error('上级产品分类必须属于同一部门')
    }
    const record = { ...scopedInput, id: `mock-${Date.now()}-${Math.random().toString(36).slice(2, 7)}` } as CrmRecord
    data[key] = [record, ...(data[key] ?? [])]
    writeAll(data)
    return record
  },
  async update(key: string, id: string, input: Partial<CrmRecord>): Promise<CrmRecord> {
    const scope = currentDataScope()
    const scopedInput = { ...input }
    if (key !== 'todos') {
      if (!scope.global && !scope.departmentId) throw new Error('当前账号未关联部门，不能修改部门业务数据')
      if (!scope.global) scopedInput.departmentId = scope.departmentId
      if (scope.global && !scopedInput.departmentId) throw new Error('请先选择记录所属部门')
    }
    if (!useMockData) return unwrapPayload<CrmRecord>(await http.put(`/${resolvePath(key)}/${encodeURIComponent(id)}`, scopedInput))
    const data = readAll()
    const index = (data[key] ?? []).findIndex((row) => row.id === id)
    if (index < 0) throw new Error('记录不存在或已被删除')
    if (key !== 'todos' && !scope.global && String(data[key][index].departmentId ?? '') !== scope.departmentId) throw new Error('无权修改其他部门的数据')
    if (key === 'products') {
      const category = (data['product-categories'] ?? []).find((row) => String(row.name ?? '') === String(scopedInput.category ?? data[key][index].category ?? ''))
      if (!category || String(category.departmentId ?? '') !== String(scopedInput.departmentId ?? data[key][index].departmentId ?? '')) throw new Error('产品分类必须归属于所选部门')
    }
    if (key === 'product-categories') {
      const currentCategory = data[key][index]
      const previousName = String(currentCategory.name ?? '')
      const nextName = String(scopedInput.name ?? previousName).trim()
      const nextDepartmentId = String(scopedInput.departmentId ?? currentCategory.departmentId ?? '')
      const parentName = String(scopedInput.parent ?? currentCategory.parent ?? '')
      if (!nextName) throw new Error('请输入分类名称')
      if ((data[key] ?? []).some((row) => row.id !== id && String(row.name ?? '').trim() === nextName)) throw new Error('产品分类名称已存在')
      if (parentName && parentName !== '—') {
        const parent = (data[key] ?? []).find((row) => String(row.name ?? '') === parentName)
        if (parent && String(parent.departmentId ?? '') !== nextDepartmentId) throw new Error('上级产品分类必须属于同一部门')
      }
      const linkedProducts = (data.products ?? []).filter((row) => String(row.category ?? '') === previousName)
      if (linkedProducts.some((row) => String(row.departmentId ?? '') !== nextDepartmentId)) throw new Error('该分类下仍有其他部门的产品，请先调整产品归属')
      const children = (data[key] ?? []).filter((row) => row.id !== id && String(row.parent ?? '') === previousName)
      if (children.some((row) => String(row.departmentId ?? '') !== nextDepartmentId)) throw new Error('该分类下仍有其他部门的子分类，请先调整分类归属')
      if (nextName !== previousName) {
        data.products = (data.products ?? []).map((row) => String(row.category ?? '') === previousName ? { ...row, category: nextName } : row)
        data[key] = (data[key] ?? []).map((row) => row.id !== id && String(row.parent ?? '') === previousName ? { ...row, parent: nextName } : row)
      }
    }
    data[key][index] = { ...data[key][index], ...scopedInput, id }
    writeAll(data)
    return data[key][index]
  },
  async remove(key: string, ids: string[]): Promise<void> {
    const scope = currentDataScope()
    if (key !== 'todos' && !scope.global && !scope.departmentId) throw new Error('当前账号未关联部门，不能删除部门业务数据')
    if (!useMockData) {
      await http.delete(`/${resolvePath(key)}`, { data: { ids } })
      return
    }
    const data = readAll()
    if (key !== 'todos' && !scope.global && (data[key] ?? []).some((row) => ids.includes(row.id) && String(row.departmentId ?? '') !== scope.departmentId)) throw new Error('无权删除其他部门的数据')
    data[key] = (data[key] ?? []).filter((row) => !ids.includes(row.id))
    writeAll(data)
  },
  /** 公海领取：服务端原子校验，已被他人领取时返回 409。 */
  async claimPool(id: string): Promise<CrmRecord> {
    if (!useMockData) return unwrapPayload<CrmRecord>(await http.post(`/pool/${encodeURIComponent(id)}/claim`))
    return moduleApi.update('pool', id, { owner: '我', status: '已领取' })
  },
  /** 线索公海领取。 */
  async claimLead(id: string): Promise<CrmRecord> {
    if (!useMockData) return unwrapPayload<CrmRecord>(await http.post(`/leads/${encodeURIComponent(id)}/claim`))
    return moduleApi.update('leads', id, { owner: '我', status: '进行中' })
  },
  /** 线索回收到公海（主管/管理员）。 */
  async recycleLead(id: string, note = ''): Promise<CrmRecord> {
    if (!useMockData) return unwrapPayload<CrmRecord>(await http.post(`/leads/${encodeURIComponent(id)}/recycle`, { note }))
    return moduleApi.update('leads', id, { owner: '', status: '待分配' })
  },
  async setStatus(key: string, ids: string[], status: string): Promise<void> {
    const scope = currentDataScope()
    if (key !== 'todos' && !scope.global && !scope.departmentId) throw new Error('当前账号未关联部门，不能更新部门业务数据')
    if (!useMockData) {
      await http.patch(`/${resolvePath(key)}/status`, { ids, status })
      return
    }
    const data = readAll()
    if (key !== 'todos' && !scope.global && (data[key] ?? []).some((row) => ids.includes(row.id) && String(row.departmentId ?? '') !== scope.departmentId)) throw new Error('无权更新其他部门的数据')
    data[key] = (data[key] ?? []).map((row) => ids.includes(row.id) ? { ...row, status } : row)
    writeAll(data)
  },
}
