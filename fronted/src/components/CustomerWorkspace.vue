<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Shop, Goods, Check } from '@element-plus/icons-vue'
import type { CrmLineItem, CrmRecord } from '../api/types'
import { moduleApi } from '../api/modules'
import { organizationApi } from '../api/organization'
import { crmBusinessApi, type StoreRecord, type RecommendationsResult } from '../api/system'
import { formatDate, formatMoney } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import dayjs from 'dayjs'
import { categoryIsVisible, isSensitiveMoneyFieldKey, redactSensitiveMoneyFields, recordInFrontendDataScope, resolveFrontendPolicy, subscribeFrontendPolicyChange } from '../utils/frontend-policy'

type ResourceKey =
  | 'contacts'
  | 'followups'
  | 'opportunities'
  | 'quotes'
  | 'orders'
  | 'shipments'
  | 'payments'
  | 'receivables'
  | 'service-tickets'
  | 'visits'
  | 'returns'

type TabKey = 'overview' | 'stores' | 'contacts' | 'followups' | 'opportunities' | 'quotes' | 'orders' | 'delivery' | 'aftersales' | 'recommendations' | 'timeline'

const props = withDefaults(defineProps<{ customer: CrmRecord; showSensitiveAmounts?: boolean }>(), { showSensitiveAmounts: false })
const auth = useAuthStore()
const router = useRouter()
const policyRevision = ref(0)
const frontendPolicy = computed(() => { policyRevision.value; return resolveFrontendPolicy(auth.user?.roleIds ?? []) })
const productCatalog = ref<CrmRecord[]>([])
const categoryCatalog = ref<CrmRecord[]>([])
const departmentCatalog = ref<unknown[]>([])

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'overview', label: '概览' },
  { key: 'stores', label: '门店列表' },
  { key: 'contacts', label: '联系人' },
  { key: 'followups', label: '跟进' },
  { key: 'opportunities', label: '商机' },
  { key: 'quotes', label: '报价' },
  { key: 'orders', label: '订单' },
  { key: 'delivery', label: '交付回款' },
  { key: 'aftersales', label: '售后' },
  { key: 'recommendations', label: '智能推荐' },
  { key: 'timeline', label: '客户时间线' },
]

const resourceLabels: Record<ResourceKey, string> = {
  contacts: '联系人',
  followups: '跟进记录',
  opportunities: '商机',
  quotes: '报价单',
  orders: '订单',
  shipments: '发货记录',
  payments: '收款记录',
  receivables: '应收记录',
  'service-tickets': '售后工单',
  visits: '客户回访',
  returns: '退换货记录',
}
const resourcePermissions: Record<ResourceKey, string> = {
  contacts: 'crm:contact:list', followups: 'crm:followup:list', opportunities: 'crm:opportunity:list',
  quotes: 'sales:quote:list', orders: 'sales:order:list', shipments: 'delivery:shipment:list',
  payments: 'delivery:payment:list', receivables: 'delivery:receivable:list', 'service-tickets': 'service:ticket:list',
  visits: 'service:visit:list', returns: 'sales:return:list',
}

const resourceKeys: ResourceKey[] = [
  'contacts', 'followups', 'opportunities', 'quotes', 'orders', 'shipments',
  'payments', 'receivables', 'service-tickets', 'visits', 'returns',
]

const tabResources: Partial<Record<TabKey, ResourceKey[]>> = {
  contacts: ['contacts'],
  followups: ['followups'],
  opportunities: ['opportunities'],
  quotes: ['quotes'],
  orders: ['orders'],
  delivery: ['shipments', 'payments', 'receivables'],
  aftersales: ['service-tickets', 'visits', 'returns'],
  timeline: ['contacts', 'followups', 'opportunities', 'quotes', 'orders', 'shipments', 'payments', 'receivables', 'service-tickets', 'visits', 'returns'],
}

const titleFields: Record<ResourceKey, string[]> = {
  contacts: ['name', 'contactName'],
  followups: ['outcome', 'title', 'result', 'type'],
  opportunities: ['name', 'opportunityName'],
  quotes: ['quoteNo', 'quoteCode', 'name'],
  orders: ['orderNo', 'orderCode'],
  shipments: ['shipmentNo', 'trackingNo', 'orderNo'],
  payments: ['receiptNo', 'orderNo'],
  receivables: ['invoiceNo', 'orderNo'],
  'service-tickets': ['ticketNo', 'name', 'type'],
  visits: ['date', 'method', 'satisfaction'],
  returns: ['returnNo', 'orderNo'],
}

const dateFields = ['date', 'createdAt', 'orderDate', 'shipmentDate', 'dueDate', 'validUntil', 'closingDate', 'lastContact']
const amountFields = ['amount', 'annualValue', 'received']
const dateLabels: Record<string, string> = {
  date: '日期', createdAt: '创建日期', orderDate: '签订日期', shipmentDate: '发货日期',
  dueDate: '到期日期', validUntil: '有效期', closingDate: '预计成交', lastContact: '最近联系',
}
const amountLabels: Record<string, string> = { amount: '金额', annualValue: '年度采购额', received: '已收金额' }

const activeTab = ref<TabKey>('overview')
const records = ref<Record<ResourceKey, CrmRecord[]>>({
  contacts: [], followups: [], opportunities: [], quotes: [], orders: [], shipments: [],
  payments: [], receivables: [], 'service-tickets': [], visits: [], returns: [],
})
const loading = ref<Record<ResourceKey, boolean>>({
  contacts: false, followups: false, opportunities: false, quotes: false, orders: false,
  shipments: false, payments: false, receivables: false, 'service-tickets': false, visits: false, returns: false,
})
const errors = ref<Partial<Record<ResourceKey, string>>>({})
const readStats = ref<Record<ResourceKey, { successful: number; failed: number }>>(Object.fromEntries(resourceKeys.map((key) => [key, { successful: 0, failed: 0 }])) as Record<ResourceKey, { successful: number; failed: number }>)
const skippedReads = ref<Partial<Record<ResourceKey, string>>>({})
let requestSequence = 0
let unsubscribeFrontendPolicy: (() => void) | undefined

const selectedResources = computed(() => tabResources[activeTab.value] ?? [])
const hasAnyRecords = computed(() => selectedResources.value.some((resource) => records.value[resource].length > 0))
const timelineFailedResources = computed(() => selectedResources.value.filter((resource) => readStats.value[resource].failed > 0))
const timelineSkippedResources = computed(() => selectedResources.value.filter((resource) => skippedReads.value[resource]))
const storesList = ref<StoreRecord[]>([])
const storesLoading = ref(false)
const recommendationsData = ref<RecommendationsResult | null>(null)
const recommendationsLoading = ref(false)
const evaluatingHealth = ref(false)

const storeDialogOpen = ref(false)
const storeForm = reactive({
  storeName: '',
  storeType: '快修快保',
  region: '',
  address: '',
  contactName: '',
  contactPhone: '',
  bayCount: 4,
  status: '营业中',
})

async function loadStores() {
  if (!props.customer?.id) return
  storesLoading.value = true
  try {
    storesList.value = await crmBusinessApi.getCustomerStores(String(props.customer.id))
  } catch {
    // fallback
  } finally {
    storesLoading.value = false
  }
}

async function loadRecommendations() {
  if (!props.customer?.id) return
  recommendationsLoading.value = true
  try {
    recommendationsData.value = await crmBusinessApi.getProductRecommendations(String(props.customer.id))
  } catch {
    // fallback
  } finally {
    recommendationsLoading.value = false
  }
}

async function triggerHealthRecalculate() {
  evaluatingHealth.value = true
  try {
    const res = await crmBusinessApi.recalculateCustomerHealth()
    ElMessage.success(res.message || '客户健康度已重算更新')
    await loadRelatedRecords()
  } catch (err: any) {
    ElMessage.error(err.message || '健康度重算失败')
  } finally {
    evaluatingHealth.value = false
  }
}

function openAddStore() {
  Object.assign(storeForm, {
    storeName: '',
    storeType: '快修快保',
    region: String(props.customer.region ?? ''),
    address: String(props.customer.address ?? ''),
    contactName: String(props.customer.contact ?? ''),
    contactPhone: String(props.customer.phone ?? ''),
    bayCount: 4,
    status: '营业中',
  })
  storeDialogOpen.value = true
}

async function saveStore() {
  if (!storeForm.storeName.trim()) {
    ElMessage.warning('请输入门店名称')
    return
  }
  try {
    await crmBusinessApi.createCustomerStore(String(props.customer.id), storeForm)
    ElMessage.success('门店已成功添加')
    storeDialogOpen.value = false
    await loadStores()
  } catch (err: any) {
    ElMessage.error(err.message || '添加门店失败')
  }
}

watch(() => props.customer?.id, () => {
  void loadStores()
  void loadRecommendations()
}, { immediate: true })

const stores = computed<unknown[] | null>(() => {
  if (storesList.value.length) return storesList.value
  const value = props.customer.stores ?? props.customer.locations
  return Array.isArray(value) ? value : null
})
const customerOrders = computed(() => records.value.orders)
const orderAmount = computed<number | null>(() => {
  const values = customerOrders.value.map((row) => row.amount).filter((value): value is string | number => (typeof value === 'string' || typeof value === 'number') && Number.isFinite(Number(value)))
  return values.length ? values.reduce<number>((sum, value) => sum + Number(value), 0) : null
})
const latestOrderDate = computed(() => customerOrders.value.map((row) => String(row.orderDate ?? row.createdAt ?? '')).filter(Boolean).sort((a, b) => b.localeCompare(a))[0] ?? '')
const healthAssessment = computed(() => {
  const reasons: string[] = []
  let score = 70
  const latest = latestOrderDate.value ? dayjs(latestOrderDate.value) : null
  const daysSinceOrder = latest?.isValid() ? dayjs().diff(latest, 'day') : null
  const overdue = records.value.receivables.some((row) => String(row.status ?? '') === '逾期' || (Number(row.amount ?? 0) > Number(row.received ?? 0) && String(row.dueDate ?? '') < dayjs().format('YYYY-MM-DD')))
  const unresolvedTickets = records.value['service-tickets'].filter((row) => !['已解决', '已关闭'].includes(String(row.status ?? ''))).length
  if (daysSinceOrder !== null && daysSinceOrder > 30) { score -= 25; reasons.push(`已 ${daysSinceOrder} 天未下单`) }
  else if (daysSinceOrder !== null && daysSinceOrder > 15) { score -= 10; reasons.push('接近预计复购周期') }
  if (overdue) { score -= 20; reasons.push('存在逾期回款') }
  if (unresolvedTickets >= 2) { score -= 20; reasons.push(`${unresolvedTickets} 个售后问题未关闭`) }
  if (customerOrders.value.length >= 3) { score += 10; reasons.push('历史采购频率稳定') }
  const label = score >= 80 ? '健康' : score >= 60 ? '正常' : score >= 40 ? '需要关注' : '高风险'
  const nextDate = String(props.customer.nextReplenishmentDate ?? '') || (daysSinceOrder !== null && daysSinceOrder >= 15 ? dayjs().add(7, 'day').format('YYYY-MM-DD') : '')
  return { score: Math.max(0, Math.min(100, score)), label, reasons, nextDate }
})
const recommendations = computed(() => {
  const items: string[] = []
  if (healthAssessment.value.nextDate) items.push(`预计 ${formatDate(healthAssessment.value.nextDate)} 前联系客户确认补货`)
  if (healthAssessment.value.reasons.some((reason) => reason.includes('逾期'))) items.push('优先跟进逾期回款，再安排新订单发货')
  if (records.value['service-tickets'].some((row) => !['已解决', '已关闭'].includes(String(row.status ?? '')))) items.push('完成未关闭售后工单并安排满意度回访')
  if (customerOrders.value.length) items.push('根据历史订单查看交叉销售和替代产品机会')
  const purchasedProducts = new Set(customerOrders.value.flatMap((order) => Array.isArray(order.items) ? order.items.filter((item): item is CrmLineItem => Boolean(item && typeof item === 'object' && 'product' in item)).map((item) => String(item.product ?? item.sku ?? '')) : []).filter(Boolean))
  const replenishmentCandidate = productCatalog.value.find((product) => !purchasedProducts.has(String(product.name ?? product.productName ?? product.sku ?? '')) && categoryAllowed(product.category ?? product.categoryName))
  if (replenishmentCandidate) items.push(`可尝试推荐：${String(replenishmentCandidate.name ?? replenishmentCandidate.productName ?? replenishmentCandidate.sku)}`)
  return items.slice(0, 4)
})
interface TimelineItem { key: string; resource: ResourceKey; label: string; title: string; date: string; status: string; amount: string | null; id: string }
const timelineItems = computed<TimelineItem[]>(() => resourceKeys.flatMap((resource) => records.value[resource].map((record) => {
  const dateKey = dateFields.find((key) => hasValue(record[key]))
  if (!dateKey) return null
  return {
    key: `${resource}:${record.id}`, resource, label: resourceLabels[resource], title: recordTitle(record, resource),
    date: String(record[dateKey]), status: recordStatus(record), amount: recordAmount(record)?.value ?? null, id: String(record.id),
  }
}).filter((item): item is TimelineItem => item !== null)).sort((a, b) => b.date.localeCompare(a.date)))

function hasValue(value: unknown): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function valueText(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—'
  const safeValue = props.showSensitiveAmounts ? value : redactSensitiveMoneyFields(value)
  if (safeValue === undefined || safeValue === null || safeValue === '') return '—'
  if (Array.isArray(safeValue)) return safeValue.map((item) => valueText(item)).filter((item) => item !== '—').join('、') || '—'
  if (typeof safeValue === 'object') {
    const object = safeValue as Record<string, unknown>
    const label = object.name ?? object.title ?? object.label ?? object.storeName ?? object.address
    return label === undefined ? JSON.stringify(object) || '—' : String(label)
  }
  return String(safeValue)
}

function storeTitle(store: unknown): string {
  if (store && typeof store === 'object' && !Array.isArray(store)) {
    const value = store as Record<string, unknown>
    return valueText(value.name ?? value.storeName ?? value.title ?? value.address)
  }
  return valueText(store)
}

function storeDetails(store: unknown): Array<{ label: string; value: string }> {
  if (!store || typeof store !== 'object' || Array.isArray(store)) return []
  return Object.entries(store as Record<string, unknown>)
    .filter(([key, value]) => key !== 'name' && key !== 'storeName' && key !== 'title' && hasValue(value) && (props.showSensitiveAmounts || !isSensitiveMoneyFieldKey(key)))
    .map(([key, value]) => ({ label: key, value: valueText(value) }))
    .filter((item) => item.value !== '—')
}

function firstValue(record: CrmRecord, keys: string[]): string {
  const key = keys.find((candidate) => hasValue(record[candidate]))
  return key ? valueText(record[key]) : '—'
}

function recordTitle(record: CrmRecord, resource: ResourceKey): string {
  return firstValue(record, titleFields[resource])
}

function recordStatus(record: CrmRecord): string {
  return firstValue(record, ['status', 'stage', 'deliveryStatus', 'paymentStatus', 'satisfaction'])
}

function recordDate(record: CrmRecord): { label: string; value: string } | null {
  const key = dateFields.find((candidate) => hasValue(record[candidate]))
  return key ? { label: dateLabels[key] ?? '日期', value: formatDate(record[key]) } : null
}

function recordAmount(record: CrmRecord): { label: string; value: string } | null {
  if (!props.showSensitiveAmounts) return null
  const key = amountFields.find((candidate) => hasValue(record[candidate]))
  return key ? { label: amountLabels[key] ?? '金额', value: formatMoney(record[key]) } : null
}

const resourceRoutes: Record<ResourceKey, string> = {
  contacts: '/contacts', followups: '/followups', opportunities: '/opportunities', quotes: '/quotes', orders: '/orders',
  shipments: '/shipments', payments: '/payments', receivables: '/receivables', 'service-tickets': '/service-tickets', visits: '/visits', returns: '/returns',
}
function openTimelineRecord(item: TimelineItem): void { void router.push(resourceRoutes[item.resource]) }

function matchesCustomer(record: CrmRecord, customerName: string, customerId: string): boolean {
  const reference = record.customer
  const referenceObject = reference && typeof reference === 'object' && !Array.isArray(reference) ? reference as Record<string, unknown> : null
  const recordId = String(record.customerId ?? record.customer_id ?? record.clientId ?? record.client_id ?? referenceObject?.id ?? '').trim()
  if (recordId) return Boolean(customerId && recordId === customerId)
  const recordName = String(record.customerName ?? record.clientName ?? (typeof reference === 'string' ? reference : referenceObject?.name ?? referenceObject?.customerName ?? '') ?? '').trim().toLocaleLowerCase()
  return Boolean(customerName && recordName === customerName.trim().toLocaleLowerCase())
}

function matchesCurrentUser(record: CrmRecord): boolean {
  return recordInFrontendDataScope(record, frontendPolicy.value, auth.user, departmentCatalog.value)
}

function categoryAllowed(value: unknown): boolean {
  if (!frontendPolicy.value.constrainedCategories || auth.can('system:data:all')) return true
  const allowed = new Set(frontendPolicy.value.categoryNames)
  let changed = true
  while (changed) {
    changed = false
    for (const category of categoryCatalog.value) {
      const parentId = String(category.parentId ?? '')
      const parent = String(category.parent ?? (parentId ? categoryCatalog.value.find((candidate) => String(candidate.id) === parentId)?.name : '') ?? '')
      const name = String(category.name ?? '')
      if (parent && allowed.has(parent) && name && !allowed.has(name)) { allowed.add(name); changed = true }
    }
  }
  return categoryIsVisible(value, { ...frontendPolicy.value, categoryNames: [...allowed] })
}

function productValueAllowed(value: unknown): boolean {
  if (!frontendPolicy.value.constrainedCategories || auth.can('system:data:all')) return true
  const needle = String(value ?? '')
  const product = productCatalog.value.find((row) => [row.id, row.name, row.productName, row.sku, row.skuCode].some((candidate) => String(candidate ?? '') === needle))
  return Boolean(product && categoryAllowed(product.category ?? product.categoryName))
}

function matchesCategoryScope(record: CrmRecord, resource: ResourceKey): boolean {
  if (!frontendPolicy.value.constrainedCategories || auth.can('system:data:all')) return true
  if (resource === 'contacts' || resource === 'followups' || resource === 'opportunities') {
    const categories = record.categories ?? record.categoryNames ?? record.interestCategories
    const names = Array.isArray(categories) ? categories : typeof categories === 'string' ? categories.split(/[,，、]/).map((name) => name.trim()) : []
    return names.some(categoryAllowed)
  }
  const items = Array.isArray(record.items) ? record.items as unknown as Array<Record<string, unknown>> : []
  const lineProducts = items.map((item) => item.product ?? item.productName ?? item.sku ?? item.skuCode).filter(hasValue)
  if (lineProducts.length) return lineProducts.every(productValueAllowed)
  const directProduct = record.product ?? record.productName ?? record.sku ?? record.skuCode
  if (hasValue(directProduct)) return productValueAllowed(directProduct)
  const directCategory = record.category ?? record.categoryName
  if (hasValue(directCategory)) return categoryAllowed(directCategory)
  const orderNo = String(record.orderNo ?? record.orderId ?? '')
  if (orderNo) {
    const order = records.value.orders.find((row) => String(row.orderNo ?? row.orderId ?? row.id) === orderNo)
    return Boolean(order && matchesCategoryScope(order, 'orders'))
  }
  return false
}

async function loadRelatedRecords(): Promise<void> {
  const sequence = ++requestSequence
  const customerName = String(props.customer.name ?? '').trim()
  const customerId = String(props.customer.id ?? '')
  records.value = {
    contacts: [], followups: [], opportunities: [], quotes: [], orders: [], shipments: [],
    payments: [], receivables: [], 'service-tickets': [], visits: [], returns: [],
  }
  errors.value = {}
  skippedReads.value = {}
  readStats.value = Object.fromEntries(resourceKeys.map((key) => [key, { successful: 0, failed: 0 }])) as Record<ResourceKey, { successful: number; failed: number }>
  productCatalog.value = []
  categoryCatalog.value = []
  departmentCatalog.value = []
  loading.value = Object.fromEntries(resourceKeys.map((key) => [key, auth.can(resourcePermissions[key])])) as Record<ResourceKey, boolean>

  if (frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本部门及下级' && auth.can('system:dept:list')) {
    try { departmentCatalog.value = await organizationApi.departments() }
    catch { departmentCatalog.value = [] }
    if (sequence !== requestSequence) return
  }

  if (frontendPolicy.value.constrainedCategories && !auth.can('system:data:all')) {
    const [categories, products] = await Promise.all([
      auth.can('product:category:list') ? moduleApi.all('product-categories').catch(() => [] as CrmRecord[]) : Promise.resolve([] as CrmRecord[]),
      auth.can('product:product:list') ? moduleApi.all('products').catch(() => [] as CrmRecord[]) : Promise.resolve([] as CrmRecord[]),
    ])
    if (sequence !== requestSequence) return
    categoryCatalog.value = categories.filter(matchesCurrentUser)
    productCatalog.value = products.filter(matchesCurrentUser)
  }
  else if (auth.can('product:product:list')) {
    const products = await moduleApi.all('products').catch(() => [] as CrmRecord[])
    if (sequence !== requestSequence) return
    productCatalog.value = products.filter(matchesCurrentUser)
  }

  await Promise.all(resourceKeys.filter((resource) => auth.can(resourcePermissions[resource])).map(async (resource) => {
    try {
      const filters: Array<Record<string, string>> = []
      if (customerId) filters.push({ customerId })
      if (customerName) filters.push({ customer: customerName })
      if (!filters.length) {
        if (sequence === requestSequence) skippedReads.value[resource] = '缺少客户标识，未发起关联查询'
        return
      }
      let successfulReads = 0
      const failures: string[] = []
      const resultSets = await Promise.all(filters.map(async (filter) => {
        try {
          const result = await moduleApi.all(resource, { filters: filter })
          successfulReads += 1
          return result
        } catch (error) {
          failures.push(error instanceof Error ? error.message : '暂时无法读取该资源')
          return null
        }
      }))
      if (sequence !== requestSequence) return
      readStats.value[resource] = { successful: successfulReads, failed: failures.length }
      if (!successfulReads) {
        errors.value[resource] = `全部关联查询失败（${failures.length} 项）：${failures[0] ?? '暂时无法读取该资源'}`
        return
      }
      const uniqueRows = [...new Map(resultSets.filter((rows): rows is CrmRecord[] => rows !== null).flat().map((row) => [String(row.id ?? JSON.stringify(row)), row])).values()]
      records.value[resource] = uniqueRows.filter((row) => matchesCustomer(row, customerName, customerId))
      if (failures.length) errors.value[resource] = `部分关联查询失败（${successfulReads} 项成功，${failures.length} 项失败）：${failures[0]}`
    } catch (error) {
      if (sequence !== requestSequence) return
      errors.value[resource] = error instanceof Error ? error.message : '暂时无法读取该资源'
    } finally {
      if (sequence === requestSequence) loading.value[resource] = false
    }
  }))
  if (sequence !== requestSequence) return
  for (const resource of resourceKeys) records.value[resource] = records.value[resource].filter((record) => matchesCurrentUser(record) && matchesCategoryScope(record, resource))
}

onMounted(() => {
  unsubscribeFrontendPolicy = subscribeFrontendPolicyChange(() => { policyRevision.value += 1; void loadRelatedRecords() })
  void loadRelatedRecords()
})
onBeforeUnmount(() => { requestSequence += 1; unsubscribeFrontendPolicy?.() })
watch(() => [props.customer.id, props.customer.name], () => { void loadRelatedRecords() })
</script>

<template>
  <section class="customer-workspace" aria-label="客户经营中心">
    <header class="workspace-header">
      <div class="workspace-heading">
        <span class="workspace-kicker">CUSTOMER WORKSPACE</span>
        <h2>{{ customer.name || '客户经营中心' }}</h2>
        <p>基于当前客户档案与已读取的关联业务记录展示。</p>
      </div>
      <div class="workspace-id"><span>客户 ID</span><strong>{{ customer.id || '—' }}</strong></div>
    </header>

    <nav class="workspace-tabs" role="tablist" aria-label="客户信息分类">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab.key"
        :class="['workspace-tab', { 'workspace-tab--active': activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >{{ tab.label }}</button>
    </nav>

    <div v-if="activeTab === 'overview'" class="overview-content" role="tabpanel">
      <section class="customer-summary-grid">
        <article class="customer-summary-tile"><span>客户主体</span><strong>{{ customer.name || '—' }}</strong><small>{{ customer.customerType ?? customer.level ?? '当前档案字段' }}</small></article>
        <article class="customer-summary-tile"><span>关联门店</span><strong>{{ stores === null ? '接口未提供' : stores.length }}</strong><small>仅统计接口返回的门店记录</small></article>
        <article class="customer-summary-tile"><span>关联订单</span><strong>{{ auth.can('sales:order:list') ? (loading.orders ? '读取中' : errors.orders ? (readStats.orders.successful ? '部分数据' : '读取失败') : customerOrders.length) : '无访问权限' }}</strong><small>{{ errors.orders ? '订单关联数据未完整读取' : latestOrderDate ? `最近下单 ${formatDate(latestOrderDate)}` : '来源：现有订单资源' }}</small></article>
        <article class="customer-summary-tile customer-summary-tile--health"><span>客户健康度</span><strong>{{ healthAssessment.label }} · {{ healthAssessment.score }}</strong><small>{{ healthAssessment.nextDate ? `预计补货 ${formatDate(healthAssessment.nextDate)}` : '根据采购、回款和售后记录计算' }}</small></article>
        <article v-if="showSensitiveAmounts" class="customer-summary-tile"><span>订单金额合计</span><strong>{{ !auth.can('sales:order:list') ? '无访问权限' : loading.orders ? '读取中' : errors.orders ? '数据不完整' : orderAmount === null ? '暂无金额数据' : formatMoney(orderAmount) }}</strong><small>基于成功读取的订单 amount 字段</small></article>
        <article v-else class="customer-summary-tile customer-summary-tile--masked"><span>订单金额合计</span><strong>••••</strong><small>当前角色前端演示策略已隐藏</small></article>
      </section>
      <section class="workspace-panel">
        <div class="panel-title"><div><span class="panel-kicker">STORES</span><h3>门店信息</h3></div></div>
        <div v-if="stores?.length" class="store-list">
          <article v-for="(store, index) in stores" :key="index" class="store-card">
            <h4>{{ storeTitle(store) }}</h4>
            <dl v-if="storeDetails(store).length" class="store-details">
              <div v-for="detail in storeDetails(store)" :key="detail.label"><dt>{{ detail.label }}</dt><dd>{{ detail.value }}</dd></div>
            </dl>
          </article>
        </div>
        <el-empty v-else description="当前客户数据未提供门店信息" :image-size="58" />
      </section>
      <section class="workspace-panel health-panel">
        <div class="panel-title">
          <div><span class="panel-kicker">CUSTOMER HEALTH</span><h3>经营建议与健康度</h3></div>
          <div style="display: flex; gap: 8px; align-items: center;">
            <el-button size="small" type="primary" plain :loading="evaluatingHealth" @click="triggerHealthRecalculate">智能健康评估</el-button>
            <el-tag :type="healthAssessment.label === '高风险' ? 'danger' : healthAssessment.label === '需要关注' ? 'warning' : 'success'" effect="light" round>{{ healthAssessment.label }}</el-tag>
          </div>
        </div>
        <div class="health-grid"><div><strong>{{ healthAssessment.score }}</strong><span>健康度评分</span></div><div><strong>{{ healthAssessment.reasons.length }}</strong><span>判断依据</span></div><div><strong>{{ recommendations.length }}</strong><span>建议动作</span></div></div>
        <ul v-if="healthAssessment.reasons.length" class="health-reasons"><li v-for="reason in healthAssessment.reasons" :key="reason">{{ reason }}</li></ul>
        <el-empty v-else description="暂未发现明显风险" :image-size="42" />
        <div v-if="recommendations.length" class="recommendation-list"><div v-for="recommendation in recommendations" :key="recommendation" class="recommendation-row"><span class="recommendation-dot"></span>{{ recommendation }}</div></div>
      </section>
    </div>

    <!-- 独立门店列表 Tab -->
    <section v-else-if="activeTab === 'stores'" class="workspace-panel stores-panel" role="tabpanel">
      <div class="panel-title">
        <div><span class="panel-kicker">STORE NETWORK</span><h3>客户独立门店列表（{{ storesList.length }} 家）</h3></div>
        <el-button size="small" type="primary" :icon="Plus" @click="openAddStore">新增门店</el-button>
      </div>
      <el-skeleton v-if="storesLoading" :rows="3" animated />
      <div v-else-if="storesList.length" class="store-grid">
        <article v-for="store in storesList" :key="store.id" class="store-card store-card--full">
          <div class="store-card__header">
            <h4>{{ store.storeName }}</h4>
            <el-tag :type="store.status === '营业中' ? 'success' : 'info'" size="small" round>{{ store.status || '营业中' }}</el-tag>
          </div>
          <div class="store-card__tags">
            <el-tag size="small" effect="plain">{{ store.storeType || '综合维修' }}</el-tag>
            <span v-if="store.bayCount" class="bay-tag">工位：{{ store.bayCount }} 间</span>
            <span v-if="store.isDefault" class="default-badge">总店</span>
          </div>
          <dl class="store-details">
            <div><dt>所属区域</dt><dd>{{ store.region || '—' }}</dd></div>
            <div><dt>门店地址</dt><dd>{{ store.address || '—' }}</dd></div>
            <div><dt>门店负责人</dt><dd>{{ store.contactName || '—' }}</dd></div>
            <div><dt>联系电话</dt><dd>{{ store.contactPhone || '—' }}</dd></div>
          </dl>
        </article>
      </div>
      <el-empty v-else description="暂无独立门店，点击右上角按钮新增门店" :image-size="58" />
    </section>

    <!-- 智能耗材推荐 Tab -->
    <section v-else-if="activeTab === 'recommendations'" class="workspace-panel recommendations-panel" role="tabpanel">
      <div class="panel-title">
        <div><span class="panel-kicker">SMART RECOMMENDATIONS</span><h3>AI / 规则智能耗材推荐</h3></div>
        <el-button size="small" plain :icon="Refresh" :loading="recommendationsLoading" @click="loadRecommendations">刷新推荐</el-button>
      </div>
      <el-skeleton v-if="recommendationsLoading" :rows="4" animated />
      <div v-else-if="recommendationsData && recommendationsData.totalRecommended > 0" class="recommendations-container">
        <!-- 临期补货预测 -->
        <div v-if="recommendationsData.repurchaseDue.length" class="rec-section">
          <div class="rec-section__title"><span class="rec-badge rec-badge--due">临期补货预测</span><small>依据已购耗材复购周期与上次订单时间预测</small></div>
          <div class="rec-cards">
            <article v-for="item in recommendationsData.repurchaseDue" :key="item.skuCode" class="rec-card">
              <div class="rec-card__top"><strong>{{ item.displayName }}</strong><span class="rec-price">¥{{ item.standardPrice }}</span></div>
              <p class="rec-reason">{{ item.recommendReason }}</p>
              <div class="rec-footer"><el-tag size="small" type="warning">{{ item.recommendTag }}</el-tag><small>周期：{{ item.repurchaseCycle }}</small></div>
            </article>
          </div>
        </div>
        <!-- 沉睡耗材唤醒 -->
        <div v-if="recommendationsData.dormantAwakening.length" class="rec-section">
          <div class="rec-section__title"><span class="rec-badge rec-badge--dormant">沉睡耗材唤醒</span><small>曾采购但超 60 天未复购的高频耗材</small></div>
          <div class="rec-cards">
            <article v-for="item in recommendationsData.dormantAwakening" :key="item.skuCode" class="rec-card">
              <div class="rec-card__top"><strong>{{ item.displayName }}</strong><span class="rec-price">¥{{ item.standardPrice }}</span></div>
              <p class="rec-reason">{{ item.recommendReason }}</p>
              <div class="rec-footer"><el-tag size="small" type="info">{{ item.recommendTag }}</el-tag><span>{{ item.categoryName }}</span></div>
            </article>
          </div>
        </div>
        <!-- 交叉连带辅料 -->
        <div v-if="recommendationsData.crossSell.length" class="rec-section">
          <div class="rec-section__title"><span class="rec-badge rec-badge--cross">交叉连带辅料</span><small>依据主耗材推荐施工必备配套物料与连带品</small></div>
          <div class="rec-cards">
            <article v-for="item in recommendationsData.crossSell" :key="item.skuCode" class="rec-card">
              <div class="rec-card__top"><strong>{{ item.displayName }}</strong><span class="rec-price">¥{{ item.standardPrice }}</span></div>
              <p class="rec-reason">{{ item.recommendReason }}</p>
              <div class="rec-footer"><el-tag size="small" type="success">{{ item.recommendTag }}</el-tag><span>{{ item.categoryName }}</span></div>
            </article>
          </div>
        </div>
        <!-- 品质升级款 -->
        <div v-if="recommendationsData.upsell.length" class="rec-section">
          <div class="rec-section__title"><span class="rec-badge rec-badge--upsell">品质升级推荐</span><small>全合成、高端配方长效耗材，提升客单价</small></div>
          <div class="rec-cards">
            <article v-for="item in recommendationsData.upsell" :key="item.skuCode" class="rec-card">
              <div class="rec-card__top"><strong>{{ item.displayName }}</strong><span class="rec-price">¥{{ item.standardPrice }}</span></div>
              <p class="rec-reason">{{ item.recommendReason }}</p>
              <div class="rec-footer"><el-tag size="small" type="danger">{{ item.recommendTag }}</el-tag><span>{{ item.categoryName }}</span></div>
            </article>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无特定推荐，该客户耗材采购规律良好" :image-size="58" />
    </section>

    <section v-else-if="activeTab === 'timeline'" class="workspace-panel timeline-panel" role="tabpanel">
      <div class="panel-title"><div><span class="panel-kicker">CUSTOMER JOURNEY</span><h3>客户时间线</h3></div><span class="record-count">{{ timelineItems.length }} 条</span></div>
      <el-alert title="时间线仅整合当前账号可读取且接口已返回的关联记录；不包含未提供的门店事件或服务端操作审计。" type="info" :closable="false" show-icon />
      <el-skeleton v-if="selectedResources.some((resource) => loading[resource])" :rows="3" animated />
      <template v-else>
        <el-alert v-if="timelineFailedResources.length" :title="`时间线数据不完整：${timelineFailedResources.map((resource) => resourceLabels[resource]).join('、')}关联请求存在失败；仅展示已成功读取的数据。`" type="warning" :closable="false" show-icon>
          <el-button link type="primary" @click="loadRelatedRecords">重试关联读取</el-button>
        </el-alert>
        <el-alert v-if="timelineSkippedResources.length" :title="`未发起以下关联查询：${timelineSkippedResources.map((resource) => `${resourceLabels[resource]}（${skippedReads[resource]}）`).join('；')}`" type="info" :closable="false" show-icon />
        <el-empty v-if="!timelineItems.length && !timelineFailedResources.length && !timelineSkippedResources.length" description="当前客户暂无可按日期展示的关联记录" :image-size="58" />
        <ol v-else-if="timelineItems.length" class="customer-timeline">
          <li v-for="item in timelineItems" :key="item.key" class="customer-timeline__item">
            <span class="timeline-dot"></span><time>{{ formatDate(item.date) }}</time>
            <div class="timeline-content"><div class="timeline-heading"><el-tag size="small" effect="plain" round>{{ item.label }}</el-tag><strong>{{ item.title }}</strong><span v-if="item.status !== '—'" class="timeline-status">{{ item.status }}</span></div><div class="timeline-facts"><code>{{ item.id }}</code><span v-if="item.amount">{{ item.amount }}</span><el-button link type="primary" @click="openTimelineRecord(item)">打开模块</el-button></div></div>
          </li>
        </ol>
      </template>
    </section>

    <div v-else class="records-content" role="tabpanel">
      <section v-for="resource in selectedResources" :key="resource" class="workspace-panel records-panel">
        <div class="panel-title">
          <div><span class="panel-kicker">RELATED RECORDS</span><h3>{{ resourceLabels[resource] }}</h3></div>
          <span v-if="!loading[resource] && records[resource].length" class="record-count">{{ records[resource].length }} 条</span>
        </div>
        <el-alert v-if="!auth.can(resourcePermissions[resource])" :title="`当前账号没有${resourceLabels[resource]}访问权限`" type="info" :closable="false" show-icon />
        <div v-else-if="loading[resource]" class="records-loading"><el-skeleton :rows="2" animated /></div>
        <template v-else>
          <el-alert v-if="errors[resource]" :title="`${resourceLabels[resource]}${readStats[resource].successful ? '部分读取失败' : '读取失败'}`" :description="errors[resource]" type="warning" :closable="false" show-icon />
          <el-button v-if="errors[resource]" class="retry-button" link type="primary" @click="loadRelatedRecords">重试关联读取</el-button>
          <el-alert v-if="skippedReads[resource]" :title="`${resourceLabels[resource]}：${skippedReads[resource]}`" type="info" :closable="false" show-icon />
          <el-empty v-if="!records[resource].length && !errors[resource] && !skippedReads[resource]" :description="`暂无匹配的${resourceLabels[resource]}记录`" :image-size="58" />
          <ul v-else-if="records[resource].length" class="record-list">
          <li v-for="record in records[resource]" :key="record.id" class="record-row">
            <div class="record-main">
              <span class="record-id">{{ record.id }}</span>
              <strong class="record-title">{{ recordTitle(record, resource) }}</strong>
            </div>
            <div class="record-facts">
              <span v-if="recordStatus(record) !== '—'" class="record-status">{{ recordStatus(record) }}</span>
              <span v-if="recordDate(record)" class="record-fact"><small>{{ recordDate(record)?.label }}</small>{{ recordDate(record)?.value }}</span>
              <span v-if="recordAmount(record)" class="record-fact record-amount"><small>{{ recordAmount(record)?.label }}</small>{{ recordAmount(record)?.value }}</span>
            </div>
          </li>
          </ul>
        </template>
      </section>
      <el-empty v-if="selectedResources.length && selectedResources.every((resource) => auth.can(resourcePermissions[resource])) && !hasAnyRecords && selectedResources.every((resource) => !loading[resource]) && selectedResources.every((resource) => !errors[resource] && !skippedReads[resource])" description="当前分类暂无关联记录" :image-size="58" />
    </div>

    <!-- 弹窗：新增独立门店 -->
    <el-dialog v-model="storeDialogOpen" title="新增独立门店" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="门店名称" required>
          <el-input v-model="storeForm.storeName" placeholder="例如：朝阳区酒仙桥旗舰店" />
        </el-form-item>
        <el-form-item label="门店类型">
          <el-select v-model="storeForm.storeType" style="width: 100%">
            <el-option label="快修快保" value="快修快保" />
            <el-option label="综合维修" value="综合维修" />
            <el-option label="钣喷中心" value="钣喷中心" />
            <el-option label="轮胎养护" value="轮胎养护" />
            <el-option label="连锁分店" value="连锁分店" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属区域">
          <el-input v-model="storeForm.region" placeholder="例如：北京市朝阳区" />
        </el-form-item>
        <el-form-item label="门店地址">
          <el-input v-model="storeForm.address" placeholder="详细街道门牌号" />
        </el-form-item>
        <el-form-item label="门店负责人">
          <el-input v-model="storeForm.contactName" placeholder="店长或负责人姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="storeForm.contactPhone" placeholder="手机号或座机" />
        </el-form-item>
        <el-form-item label="工位数量">
          <el-input-number v-model="storeForm.bayCount" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="storeDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveStore">保存门店</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.customer-workspace { min-width: 0; color: #31473a; }
.workspace-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 18px; padding: 18px 20px; border: 1px solid #e3ece3; border-radius: 14px; background: linear-gradient(120deg, #f7fbf6 0%, #fff 70%); }
.workspace-heading { min-width: 0; }
.workspace-kicker, .panel-kicker { color: #829589; font-size: 9px; font-weight: 700; letter-spacing: 1.35px; }
.workspace-heading h2 { overflow: hidden; margin: 5px 0 4px; color: #254234; font-size: 20px; font-weight: 700; text-overflow: ellipsis; }
.workspace-heading p { margin: 0; color: #89968d; font-size: 11px; }
.workspace-id { display: flex; flex: 0 0 auto; flex-direction: column; gap: 4px; align-items: flex-end; }
.workspace-id span { color: #92a097; font-size: 10px; }
.workspace-id strong { color: #4e765d; font-size: 11px; font-weight: 600; }
.workspace-tabs { display: flex; gap: 3px; overflow-x: auto; margin-bottom: 15px; border-bottom: 1px solid #e5ece5; scrollbar-width: thin; }
.workspace-tab { position: relative; flex: 0 0 auto; padding: 11px 13px 12px; border: 0; color: #7e8d82; background: transparent; font-size: 12px; white-space: nowrap; }
.workspace-tab:hover { color: #528a70; }
.workspace-tab--active { color: #528a70; font-weight: 600; }
.workspace-tab--active::after { position: absolute; right: 10px; bottom: -1px; left: 10px; height: 2px; border-radius: 2px 2px 0 0; background: #528a70; content: ''; }
.overview-content, .records-content { display: grid; gap: 13px; }
.customer-summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; }
.customer-summary-tile { display: flex; min-width: 0; min-height: 84px; flex-direction: column; gap: 5px; justify-content: center; padding: 11px 13px; border: 1px solid #e6ece5; border-radius: 10px; background: #fff; }
.customer-summary-tile span { color: #91a096; font-size: 10px; }
.customer-summary-tile strong { overflow: hidden; color: #3d5947; font-size: 14px; font-variant-numeric: tabular-nums; text-overflow: ellipsis; white-space: nowrap; }
.customer-summary-tile small { overflow: hidden; color: #9aa59b; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.customer-summary-tile--masked strong { color: #ad8a54; }
.customer-summary-tile--health strong { color: #528a70; }
.workspace-panel { min-width: 0; padding: 16px; border: 1px solid #e6ece5; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(47, 71, 52, .025); }
.panel-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 13px; }
.panel-title h3 { margin: 4px 0 0; color: #30483a; font-size: 14px; font-weight: 650; }
.store-details dt { margin-bottom: 5px; color: #91a096; font-size: 10px; }
.store-details dd { overflow-wrap: anywhere; margin: 0; color: #42584a; font-size: 12px; line-height: 1.5; }
.store-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.store-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.store-card { min-width: 0; padding: 12px; border: 1px solid #edf1ed; border-radius: 9px; background: #fbfcfa; }
.store-card--full { border-color: #e4ebe4; background: #ffffff; }
.store-card__header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.store-card__tags { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; }
.bay-tag { font-size: 11px; color: #597061; background: #f0f5f0; padding: 2px 6px; border-radius: 4px; }
.default-badge { font-size: 10px; color: #b88230; background: #fdf5e8; padding: 2px 6px; border-radius: 4px; font-weight: bold; }
.store-card h4 { overflow-wrap: anywhere; margin: 0; color: #45644e; font-size: 13px; font-weight: 650; }
.store-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px 12px; margin: 10px 0 0; }
.store-details > div { min-width: 0; }
.health-panel { display: grid; gap: 12px; }
.health-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.health-grid > div { display: flex; flex-direction: column; gap: 3px; padding: 11px; border-radius: 9px; background: #f7fbf7; }
.health-grid strong { color: #3d5947; font-size: 18px; font-variant-numeric: tabular-nums; }
.health-grid span { color: #91a096; font-size: 10px; }
.health-reasons { display: flex; flex-wrap: wrap; gap: 7px; margin: 0; padding: 0; list-style: none; }
.health-reasons li { padding: 5px 8px; border-radius: 12px; color: #9a7440; background: #fff8e9; font-size: 11px; }
.recommendation-list { display: grid; gap: 7px; padding-top: 2px; border-top: 1px solid #eef2ed; }
.recommendation-row { display: flex; align-items: center; gap: 8px; color: #52675a; font-size: 11px; }
.recommendation-dot { width: 6px; height: 6px; flex: 0 0 auto; border-radius: 50%; background: #78a487; }
.records-panel { padding-bottom: 8px; }
.record-count { padding: 4px 8px; border-radius: 12px; color: #528a70; background: #eff6ef; font-size: 10px; }
.record-list { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.record-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 0; border-top: 1px solid #f0f3ef; }
.record-main { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.record-id { color: #9aa69d; font-size: 10px; overflow-wrap: anywhere; }
.record-title { overflow: hidden; color: #3a5142; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-facts { display: flex; flex: 0 0 auto; align-items: center; gap: 12px; color: #627369; font-size: 11px; }
.record-status { padding: 4px 7px; border-radius: 11px; color: #528a70; background: #f0f6f0; font-size: 10px; }
.record-fact { display: flex; flex-direction: column; gap: 3px; text-align: right; }
.record-fact small { color: #9aa69d; font-size: 9px; }
.record-amount { color: #4c785b; font-weight: 600; }
.records-loading { padding: 6px 0 12px; }
.customer-workspace :deep(.el-empty) { padding: 13px 0; }
.customer-workspace :deep(.el-empty__description p) { color: #96a198; font-size: 11px; }
.timeline-panel > .el-alert { margin-bottom: 12px; }
.customer-timeline { margin: 0; padding: 7px 0 2px; list-style: none; }
.customer-timeline__item { position: relative; display: grid; grid-template-columns: 95px 14px minmax(0, 1fr); gap: 10px; min-width: 0; padding: 0 0 15px; }
.customer-timeline__item:not(:last-child)::after { position: absolute; top: 12px; bottom: -1px; left: 111px; width: 1px; background: #e5ede5; content: ''; }
.customer-timeline__item time { color: #8d9b90; font-size: 11px; }
.timeline-dot { z-index: 1; width: 8px; height: 8px; margin-top: 4px; border: 2px solid #78a487; border-radius: 50%; background: white; }
.timeline-content { min-width: 0; padding-bottom: 11px; border-bottom: 1px solid #f0f3ef; }
.timeline-heading, .timeline-facts { display: flex; min-width: 0; flex-wrap: wrap; align-items: center; gap: 7px; }
.timeline-heading strong { overflow: hidden; color: #44594a; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.timeline-status { color: #839086; font-size: 10px; }
.timeline-facts { margin-top: 6px; color: #8e9b91; font-size: 10px; }
.timeline-facts code { color: #9aa59b; }

/* 智能耗材推荐样式 */
.recommendations-container { display: flex; flex-direction: column; gap: 18px; margin-top: 8px; }
.rec-section { background: #fafcfa; border: 1px solid #e7efe7; border-radius: 10px; padding: 14px 16px; }
.rec-section__title { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.rec-section__title small { color: #88998d; font-size: 11px; }
.rec-badge { font-size: 12px; font-weight: 600; padding: 3px 8px; border-radius: 6px; }
.rec-badge--due { background: #fdf5e8; color: #c48227; }
.rec-badge--dormant { background: #f1f4f8; color: #4e6f96; }
.rec-badge--cross { background: #eff7f1; color: #3e8854; }
.rec-badge--upsell { background: #fbf0f0; color: #b84141; }
.rec-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.rec-card { background: #ffffff; border: 1px solid #e5ede5; border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.02); }
.rec-card__top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; font-size: 13px; color: #2c4234; }
.rec-price { font-weight: 700; color: #3a7550; font-size: 13px; }
.rec-reason { margin: 0; font-size: 11px; color: #697a6f; line-height: 1.4; }
.rec-footer { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #93a297; margin-top: auto; padding-top: 6px; border-top: 1px dashed #eef2ee; }

@media (max-width: 720px) {
  .workspace-header { align-items: flex-start; padding: 15px; }
  .workspace-id { padding-top: 3px; }
  .record-row { align-items: flex-start; flex-direction: column; gap: 9px; }
  .record-facts { width: 100%; flex-wrap: wrap; justify-content: flex-start; }
  .record-fact { text-align: left; }
  .customer-summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .health-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .customer-timeline__item { grid-template-columns: 78px 12px minmax(0, 1fr); gap: 7px; }
  .customer-timeline__item:not(:last-child)::after { left: 88px; }
}
@media (max-width: 460px) {
  .store-list { grid-template-columns: minmax(0, 1fr); }
}
</style>
