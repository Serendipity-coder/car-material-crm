import { http, unwrapPayload, useMockData } from './client'

export interface PoolAutoRecycleResult {
  days: number
  threshold: string
  leads: number
  customers: number
}

/** 系统参数与公海运维动作（仅管理员/主管可用）。 */
export const systemApi = {
  async configs(): Promise<Record<string, string>> {
    if (useMockData) return { 'pool.contact.full': 'false', 'pool.auto.recycle.enabled': 'true', 'pool.auto.recycle.days': '30', 'pool.auto.recycle.resources': 'leads' }
    const data = unwrapPayload<Record<string, unknown>>(await http.get('/system/configs'))
    return Object.fromEntries(Object.entries(data ?? {}).map(([key, value]) => [key, value === null || value === undefined ? '' : String(value)]))
  },
  async saveConfigs(values: Record<string, string>): Promise<Record<string, string>> {
    if (useMockData) return values
    const data = unwrapPayload<Record<string, unknown>>(await http.put('/system/configs', values))
    return Object.fromEntries(Object.entries(data ?? {}).map(([key, value]) => [key, value === null || value === undefined ? '' : String(value)]))
  },
  async autoRecycle(days?: number): Promise<PoolAutoRecycleResult> {
    if (useMockData) return { days: days ?? 30, threshold: '', leads: 0, customers: 0 }
    return unwrapPayload<PoolAutoRecycleResult>(await http.post('/pool/auto-recycle', null, { params: days ? { days } : {} }))
  },
}

export interface StoreRecord {
  id: string
  customerId: string
  customerName?: string
  storeName: string
  storeType?: string
  region?: string
  address?: string
  contactName?: string
  contactPhone?: string
  bayCount?: number | string
  status?: string
  isDefault?: boolean
}

export interface RecommendationItem {
  skuId: string
  productId: string
  skuCode: string
  productName: string
  displayName: string
  categoryName: string
  spec?: string
  unit?: string
  standardPrice?: number
  repurchaseCycle?: string
  recommendTag: string
  recommendReason: string
}

export interface RecommendationsResult {
  customerId: string
  repurchaseDue: RecommendationItem[]
  dormantAwakening: RecommendationItem[]
  crossSell: RecommendationItem[]
  upsell: RecommendationItem[]
  totalRecommended: number
}

export type DashboardOverview = any

export interface AuditLogRow {
  logId: number
  operatorId: string
  operatorAccount: string
  operatorDeptId?: string
  resourceKey: string
  recordId: string
  recordLabel?: string
  actionType: string
  result?: string
  clientIp?: string
  createdAt: string
}

/** 业务自动化与增强接口（服务端大盘、智能推荐、门店、审批、转单、健康度计算）。 */
export const crmBusinessApi = {
  async getDashboardOverview(departmentId?: string) {
    return unwrapPayload<any>(await http.get('/dashboard/overview', { params: departmentId && departmentId !== '__all__' ? { departmentId } : {} }))
  },
  async autoGenerateTodos() {
    return unwrapPayload<{ createdCount: number; message: string }>(await http.post('/todos/auto-generate'))
  },
  async checkLeadDuplicate(paramsOrPhone?: string | { phone?: string; name?: string; excludeId?: string }, name?: string, excludeId?: string) {
    const params = typeof paramsOrPhone === 'object' && paramsOrPhone !== null ? paramsOrPhone : { phone: paramsOrPhone, name, excludeId }
    return unwrapPayload<{ duplicate: boolean; totalMatches: number; matches: Array<any>; leads?: Array<any>; customers?: Array<any> }>(
      await http.get('/leads/check-duplicate', { params })
    )
  },
  async getCustomerStores(customerId: string) {
    return unwrapPayload<StoreRecord[]>(await http.get(`/customers/${encodeURIComponent(customerId)}/stores`))
  },
  async createCustomerStore(customerId: string, body: Partial<StoreRecord>) {
    return unwrapPayload<StoreRecord>(await http.post(`/customers/${encodeURIComponent(customerId)}/stores`, body))
  },
  async updateCustomerStore(customerId: string, storeId: string, body: Partial<StoreRecord>) {
    return unwrapPayload<StoreRecord>(await http.put(`/customers/${encodeURIComponent(customerId)}/stores/${encodeURIComponent(storeId)}`, body))
  },
  async deleteCustomerStore(customerId: string, storeId: string) {
    return unwrapPayload<void>(await http.delete(`/customers/${encodeURIComponent(customerId)}/stores/${encodeURIComponent(storeId)}`))
  },
  async getProductRecommendations(customerId: string) {
    return unwrapPayload<RecommendationsResult>(await http.get('/products/recommendations', { params: { customerId } }))
  },
  async approveQuote(quoteId: string, note?: string) {
    return unwrapPayload<any>(await http.post(`/quotes/${encodeURIComponent(quoteId)}/approve`, { note }))
  },
  async rejectQuote(quoteId: string, reason?: string) {
    return unwrapPayload<any>(await http.post(`/quotes/${encodeURIComponent(quoteId)}/reject`, { reason }))
  },
  async convertQuoteToOrder(quoteId: string) {
    return unwrapPayload<any>(await http.post(`/quotes/${encodeURIComponent(quoteId)}/convert-to-order`))
  },
  async recalculateCustomerHealth() {
    return unwrapPayload<{ evaluatedTotal: number; updatedCount: number; distribution: Record<string, number>; message: string }>(
      await http.post('/customers/recalculate-health')
    )
  },
  /** 服务端导出：返回 CSV Blob（后端按数据范围/品类过滤并写导出审计）。 */
  async exportModule(key: string, params: Record<string, string>): Promise<Blob> {
    if (useMockData) throw new Error('Mock 模式不支持导出')
    return (await http.get(`/export/${encodeURIComponent(key)}`, { params, responseType: 'blob' })) as unknown as Blob
  },
  async auditLogs(params: { pageNum?: number; pageSize?: number; account?: string; resource?: string; action?: string; start?: string; end?: string }) {
    return unwrapPayload<{ rows: AuditLogRow[]; total: number }>(await http.get('/audit-logs', { params }))
  },
}
