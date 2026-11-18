<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Calendar, CircleCheck, Clock, Plus, Refresh } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import dayjs from 'dayjs'
import EChart from '../components/EChart.vue'
import StatCard from '../components/StatCard.vue'
import { sourceMix } from '../data/dashboard'
import { moduleApi } from '../api/modules'
import { organizationApi } from '../api/organization'
import { crmBusinessApi, type DashboardOverview } from '../api/system'
import type { CrmRecord } from '../api/types'
import type { Department } from '../data/organization'
import { formatMoney } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { recordInFrontendDataScope, resolveFrontendPolicy, subscribeFrontendPolicyChange } from '../utils/frontend-policy'

const router = useRouter()
const auth = useAuthStore()
type ResourceName = 'todos' | 'customers' | 'opportunities' | 'reports' | 'leads' | 'payments' | 'departments' | 'followups' | 'quotes' | 'receivables' | 'tickets' | 'products' | 'categories' | 'orders'
type ResourceStatus = 'loading' | 'success' | 'error'
const departments = ref<Department[]>([])
const departmentFilter = ref('__all__')
const todoItems = ref<CrmRecord[]>([])
const customerCount = ref(0)
const opportunityRows = ref<CrmRecord[]>([])
const allOpportunityRows = ref<CrmRecord[]>([])
const salesRows = ref<CrmRecord[]>([])
const leadRows = ref<CrmRecord[]>([])
const paymentRows = ref<CrmRecord[]>([])
const reminderRows = ref<Array<{ id: string; title: string; detail: string; due: string; route: string; kind: 'overdue' | 'upcoming' | 'inventory' }>>([])
const resourceStates = ref<Partial<Record<ResourceName, ResourceStatus>>>({})
const finishingTodoIds = ref<Set<string>>(new Set())
const dashboardLoading = ref(true)
const salesPeriod = ref('近 6 个月')
const serverOverview = ref<DashboardOverview | null>(null)
const scanningTodos = ref(false)
const frontendPolicyRevision = ref(0)
const now = dayjs()
const frontendPolicy = computed(() => { frontendPolicyRevision.value; return resolveFrontendPolicy(auth.user?.roleIds ?? []) })
const canViewSensitivePrices = computed(() => frontendPolicy.value.viewSensitivePrices || auth.can('system:data:all'))
const hideSensitiveAmounts = computed(() => !canViewSensitivePrices.value)
const financialAnalyticsUnreliable = computed(() => !auth.can('system:data:all') && frontendPolicy.value.configured && (frontendPolicy.value.dataScope === '本人' || frontendPolicy.value.constrainedCategories))
const sensitiveAmountDescription = computed(() => financialAnalyticsUnreliable.value
  ? '当前浏览器角色草稿为本人或指定品类范围，既有服务端汇总无法在前端可靠重算；金额图表与汇总已隐藏。后端范围控制为最终安全边界。'
  : frontendPolicy.value.configured
    ? '当前浏览器角色策略未授予敏感价格/金额查看权限，已在前端隐藏；此策略不替代服务端字段脱敏。'
    : '尚无明确的敏感价格/金额查看授权，已按安全默认值隐藏；前端显示策略不替代服务端字段脱敏。')
const canViewSales = computed(() => auth.can('report:sales:list'))
const canViewLeads = computed(() => auth.can('crm:lead:list'))
const canViewTodos = computed(() => auth.can('work:todo:list'))
const canEditTodos = computed(() => auth.can('work:todo:edit'))
const canViewCustomers = computed(() => auth.can('crm:customer:list'))
const canViewOpportunities = computed(() => auth.can('crm:opportunity:list'))
const canViewPayments = computed(() => auth.can('delivery:payment:list'))
const canSelectDepartment = computed(() => auth.can('system:data:all'))
function hasResourceError(name: ResourceName): boolean {
  const scopedByDepartment = frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本部门及下级' && !auth.can('system:data:all')
  const scopedByCategory = frontendPolicy.value.constrainedCategories && !auth.can('system:data:all')
  return resourceStates.value[name] === 'error'
    || (scopedByDepartment && resourceStates.value.departments === 'error')
    || (scopedByCategory && (resourceStates.value.categories === 'error' || resourceStates.value.orders === 'error'))
}
const reminderHasError = computed(() => ['todos', 'followups', 'quotes', 'receivables', 'tickets', 'products', 'departments', 'categories', 'orders'].some((key) => hasResourceError(key as ResourceName)))
const greeting = computed(() => {
  const hour = now.hour()
  return hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
})
const currentMonthlySales = computed(() => {
  const source = resourceStates.value.reports === 'success' && !hasResourceError('reports') ? salesRows.value.map((row) => ({ month: String(row.month ?? row.date ?? ''), amount: Number(row.amount ?? 0) })) : []
  const yearRows = source.filter((item) => !/\d{4}/.test(item.month) || item.month.includes(String(now.year())))
  return salesPeriod.value === '本年度' ? yearRows : yearRows.slice(-6)
})
const salesTotal = computed(() => {
  if (serverOverview.value?.sales?.currentYearSales !== undefined) {
    return Number(serverOverview.value.sales.currentYearSales)
  }
  if (resourceStates.value.reports !== 'success' || hasResourceError('reports')) return 0
  const year = String(now.year())
  return salesRows.value
    .filter((row) => {
      const period = String(row.month ?? row.date ?? row.createdAt ?? '')
      return !/\d{4}/.test(period) || period.includes(year)
    })
    .reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
})
const activeOpportunities = computed(() => allOpportunityRows.value.filter((row) => !['赢单', '输单'].includes(String(row.stage ?? row.status ?? ''))))
const weightedOpportunityAmount = computed(() => {
  if (serverOverview.value?.opportunities?.weightedAmount !== undefined) {
    return Number(serverOverview.value.opportunities.weightedAmount)
  }
  return activeOpportunities.value.reduce((sum, row) => sum + Number(row.amount ?? 0) * Number(row.probability ?? 0) / 100, 0)
})
const monthPaymentTotal = computed(() => {
  if (serverOverview.value?.payments?.currentMonthReceived !== undefined) {
    return Number(serverOverview.value.payments.currentMonthReceived)
  }
  return paymentRows.value.filter((row) => String(row.date ?? row.paymentDate ?? row.createdAt ?? '').startsWith(now.format('YYYY-MM'))).reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
})

async function runAutoGenerateTodos() {
  scanningTodos.value = true
  try {
    const res = await crmBusinessApi.autoGenerateTodos()
    ElMessage.success(res.message || `已自动生成 ${res.createdCount ?? 0} 条业务待办`)
    await load()
  } catch (err: any) {
    ElMessage.error(err.message || '待办扫描生成失败')
  } finally {
    scanningTodos.value = false
  }
}
const sourceChartData = computed(() => {
  if (!canViewLeads.value) return []
  if (resourceStates.value.leads !== 'success' || hasResourceError('leads')) return []
  const counts = new Map<string, number>()
  leadRows.value.forEach((row) => { const source = String(row.source ?? '其他'); counts.set(source, (counts.get(source) ?? 0) + 1) })
  const total = [...counts.values()].reduce((sum, value) => sum + value, 0)
  return [...counts.entries()].map(([name, count], index) => ({ name, value: total ? Math.round(count * 100 / total) : 0, color: sourceMix[index % sourceMix.length]?.color ?? '#82ae92' }))
})
const leadTotal = computed(() => !canViewLeads.value || resourceStates.value.leads !== 'success' || hasResourceError('leads') ? 0 : leadRows.value.length)

const salesOption = computed<EChartsOption>(() => ({
  color: ['#528a70'],
  grid: { left: 8, right: 18, top: 24, bottom: 8, containLabel: true },
  tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => formatMoney(value) },
  xAxis: { type: 'category', boundaryGap: false, data: currentMonthlySales.value.map((item) => item.month), axisLine: { lineStyle: { color: '#e5ece5' } }, axisTick: { show: false }, axisLabel: { color: '#849187' } },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf2ed', type: 'dashed' } }, axisLabel: { color: '#9aa59b', formatter: (value: number) => `${Math.round(value / 10000)}万` } },
  series: [{ type: 'line', smooth: true, symbolSize: 7, data: currentMonthlySales.value.map((item) => item.amount), areaStyle: { color: 'rgba(82,138,112,.12)' }, lineStyle: { width: 3, color: '#528a70' }, itemStyle: { color: '#528a70', borderColor: '#fff', borderWidth: 2 } }],
}) as EChartsOption)
const sourceOption = computed<EChartsOption>(() => ({
  color: sourceChartData.value.map((item) => item.color),
  tooltip: { trigger: 'item', formatter: '{b}  {c}%' },
  legend: { bottom: 0, left: 'center', itemWidth: 8, itemHeight: 8, textStyle: { color: '#728076', fontSize: 11 } },
  series: [{ type: 'pie', radius: ['57%', '78%'], center: ['50%', '43%'], label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 4 }, data: sourceChartData.value.map((item) => ({ name: item.name, value: item.value })) }],
}) as EChartsOption)

let loadSequence = 0
let unsubscribeFrontendPolicy: (() => void) | undefined

function makeReminders(rows: { todos: CrmRecord[]; customers: CrmRecord[]; followups: CrmRecord[]; quotes: CrmRecord[]; receivables: CrmRecord[]; tickets: CrmRecord[]; products: CrmRecord[] }): Array<{ id: string; title: string; detail: string; due: string; route: string; kind: 'overdue' | 'upcoming' | 'inventory' }> {
  const reminders: Array<{ id: string; title: string; detail: string; due: string; route: string; kind: 'overdue' | 'upcoming' | 'inventory' }> = []
  const today = dayjs().startOf('day')
  const soon = today.add(7, 'day')
  const addDated = (row: CrmRecord, title: string, detail: string, dateValue: unknown, route: string) => {
    const due = dayjs(String(dateValue ?? ''))
    if (!due.isValid() || due.isAfter(soon, 'day')) return
    reminders.push({ id: `${route}:${row.id}`, title, detail, due: due.format('YYYY-MM-DD'), route, kind: due.isBefore(today, 'day') ? 'overdue' : 'upcoming' })
  }
  for (const row of rows.todos) if (!['已完成', '已取消'].includes(String(row.status ?? ''))) addDated(row, String(row.title ?? '待办事项'), String(row.related ?? row.owner ?? ''), row.dueDate, '/todos')
  for (const row of rows.followups) if (!['已完成', '已取消'].includes(String(row.status ?? ''))) addDated(row, `客户跟进：${String(row.customer ?? '待跟进')}`, String(row.outcome ?? row.type ?? ''), row.nextDate, '/followups')
  for (const row of rows.quotes) {
    if (!['已接受', '已拒绝', '已过期', '已取消'].includes(String(row.status ?? ''))) {
      addDated(row, `报价即将到期：${String(row.quoteNo ?? row.customer ?? '报价单')}`, String(row.customer ?? ''), row.validUntil, '/quotes')
      const created = dayjs(String(row.createdAt ?? row.date ?? ''))
      if (created.isValid() && today.diff(created, 'day') >= 3 && ['已提交', '客户查看', '议价中'].includes(String(row.status ?? ''))) reminders.push({ id: `quote-followup:${row.id}`, title: `报价超过 3 天未确认：${String(row.quoteNo ?? row.customer ?? '报价单')}`, detail: String(row.customer ?? '请联系客户确认报价反馈'), due: created.add(3, 'day').format('YYYY-MM-DD'), route: '/quotes', kind: 'overdue' })
    }
  }
  for (const row of rows.receivables) {
    if (Number(row.amount ?? 0) <= Number(row.received ?? 0)) continue
    addDated(row, `应收款到期：${String(row.invoiceNo ?? row.orderNo ?? '应收记录')}`, String(row.customer ?? ''), row.dueDate, '/receivables')
  }
  for (const row of rows.tickets) if (!['已完成', '已解决', '已关闭'].includes(String(row.status ?? ''))) addDated(row, `售后待处理：${String(row.ticketNo ?? row.name ?? '售后工单')}`, String(row.customer ?? row.type ?? ''), row.dueDate, '/service-tickets')
  for (const row of rows.products) {
    const stock = Number(row.stock)
    if (Number.isFinite(stock) && stock <= 20) reminders.push({ id: `products:${row.id}`, title: `库存关注：${String(row.name ?? row.sku ?? '产品')}`, detail: `当前库存 ${stock}（沿用产品页低库存统计阈值）`, due: '', route: '/products', kind: 'inventory' })
  }
  for (const row of rows.customers) {
    const nextReplenishment = String(row.nextReplenishmentDate ?? '')
    if (nextReplenishment) addDated(row, `客户预计补货：${String(row.name ?? '客户')}`, String(row.purchaseCategories ?? row.storeName ?? ''), nextReplenishment, '/customers')
    const lastContact = dayjs(String(row.lastContact ?? ''))
    if (lastContact.isValid() && today.diff(lastContact, 'day') >= 30 && !['已流失', '暂停合作'].includes(String(row.status ?? ''))) reminders.push({ id: `customer-inactive:${row.id}`, title: `客户超过 30 天未联系：${String(row.name ?? '客户')}`, detail: String(row.owner ?? '请安排销售跟进'), due: lastContact.add(30, 'day').format('YYYY-MM-DD'), route: '/customers', kind: 'overdue' })
  }
  return reminders.sort((a, b) => (a.kind === 'overdue' ? 0 : 1) - (b.kind === 'overdue' ? 0 : 1) || a.due.localeCompare(b.due)).slice(0, 12)
}
function isTodoOwnedByCurrentUser(row: CrmRecord): boolean {
  const identity = [auth.user?.name, auth.user?.id, auth.user?.account, auth.user?.phone]
    .map((value) => String(value ?? '').trim().toLocaleLowerCase())
    .filter(Boolean)
  const ownerValues = [row.owner, row.ownerId, row.assignee, row.assigneeId, row.createdBy, row.createdById]
    .filter((value) => value !== undefined && value !== null && String(value).trim() !== '')
    .map((value) => String(value).trim().toLocaleLowerCase())
  return identity.length > 0 && ownerValues.some((value) => identity.includes(value))
}

async function load(): Promise<void> {
  const request = ++loadSequence
  let failed = false
  dashboardLoading.value = true
  resourceStates.value = {}
  todoItems.value = []
  customerCount.value = 0
  opportunityRows.value = []
  allOpportunityRows.value = []
  salesRows.value = []
  leadRows.value = []
  paymentRows.value = []
  reminderRows.value = []
  const filters: Record<string, string> = canSelectDepartment.value && departmentFilter.value !== '__all__' ? { departmentId: departmentFilter.value } : {}
  const shouldLoadDepartmentTree = auth.can('system:data:all') || (frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本部门及下级' && auth.can('system:dept:list'))
  async function permitted<T>(name: ResourceName, permission: string, fetchResource: () => Promise<T>, fallback: T): Promise<T> {
    if (!auth.can(permission)) return fallback
    if (request === loadSequence) resourceStates.value = { ...resourceStates.value, [name]: 'loading' }
    try {
      const result = await fetchResource()
      if (request === loadSequence) resourceStates.value = { ...resourceStates.value, [name]: 'success' }
      return result
    } catch {
      failed = true
      if (request === loadSequence) resourceStates.value = { ...resourceStates.value, [name]: 'error' }
      return fallback
    }
  }
  const [todos, customers, allOpportunities, reports, leads, payments, departmentRows, followups, quotes, receivables, tickets, products, categories, orders] = await Promise.all([
    permitted('todos', 'work:todo:list', () => moduleApi.all('todos', { filters }), []),
    permitted('customers', 'crm:customer:list', () => moduleApi.all('customers', { filters }), []),
    permitted('opportunities', 'crm:opportunity:list', () => moduleApi.all('opportunities', { filters }), []),
    permitted('reports', 'report:sales:list', () => moduleApi.all('reports-sales', { filters }), []),
    permitted('leads', 'crm:lead:list', () => moduleApi.all('leads', { filters }), []),
    permitted('payments', 'delivery:payment:list', () => moduleApi.all('payments', { filters }), []),
    shouldLoadDepartmentTree ? permitted('departments', auth.can('system:data:all') ? 'system:data:all' : 'system:dept:list', () => organizationApi.departments(), []) : Promise.resolve([] as Department[]),
    permitted('followups', 'crm:followup:list', () => moduleApi.all('followups', { filters }), []),
    permitted('quotes', 'sales:quote:list', () => moduleApi.all('quotes', { filters }), []),
    permitted('receivables', 'delivery:receivable:list', () => moduleApi.all('receivables', { filters }), []),
    permitted('tickets', 'service:ticket:list', () => moduleApi.all('service-tickets', { filters }), []),
    permitted('products', 'product:product:list', () => moduleApi.all('products', { filters }), []),
    frontendPolicy.value.constrainedCategories && !auth.can('system:data:all') ? permitted('categories', 'product:category:list', () => moduleApi.all('product-categories', { filters }), []) : Promise.resolve([] as CrmRecord[]),
    permitted('orders', 'sales:order:list', () => moduleApi.all('orders', { filters }), []),
  ])
  try {
    const dept = canSelectDepartment.value && departmentFilter.value !== '__all__' ? departmentFilter.value : undefined
    serverOverview.value = await crmBusinessApi.getDashboardOverview(dept)
  } catch {
    serverOverview.value = null
  }
  if (request !== loadSequence) return
  const scoped = (rows: CrmRecord[]) => rows.filter((row) => recordInFrontendDataScope(row, frontendPolicy.value, auth.user, departmentRows))
  const visibleCategories = scoped(categories)
  const allowedCategories = new Set(frontendPolicy.value.categoryNames)
  let expanded = true
  while (expanded) {
    expanded = false
    for (const category of visibleCategories) {
      const parent = String(category.parent ?? (category.parentId ? visibleCategories.find((item) => String(item.id) === String(category.parentId))?.name : '') ?? '')
      const name = String(category.name ?? '')
      if (parent && allowedCategories.has(parent) && name && !allowedCategories.has(name)) { allowedCategories.add(name); expanded = true }
    }
  }
  const productAllowed = (value: unknown) => {
    if (!frontendPolicy.value.constrainedCategories || auth.can('system:data:all')) return true
    const product = scoped(products).find((row) => [row.id, row.name, row.productName, row.sku, row.skuCode].some((candidate) => String(candidate ?? '') === String(value ?? '')))
    return Boolean(product && allowedCategories.has(String(product.category ?? product.categoryName ?? '')))
  }
  const categoryAllowedRecord = (row: CrmRecord, isOrder = false): boolean => {
    if (!frontendPolicy.value.constrainedCategories || auth.can('system:data:all')) return true
    const items = Array.isArray(row.items) ? row.items as unknown as Array<Record<string, unknown>> : []
    const lineProducts = items.map((item) => item.product ?? item.productName ?? item.sku ?? item.skuCode).filter((value) => value !== undefined && value !== null && String(value) !== '')
    if (lineProducts.length) return lineProducts.every(productAllowed)
    const rawCategories = row.categories ?? row.categoryNames ?? row.interestCategories
    const categoryNames = Array.isArray(rawCategories) ? rawCategories.map(String) : typeof rawCategories === 'string' ? rawCategories.split(/[,，、]/).map((name) => name.trim()).filter(Boolean) : []
    if (categoryNames.length) return categoryNames.some((name) => allowedCategories.has(name))
    const productName = row.product ?? row.productName ?? row.sku ?? row.skuCode
    if (productName !== undefined && productName !== null && String(productName) !== '') return productAllowed(productName)
    if (row.category || row.categoryName) return allowedCategories.has(String(row.category ?? row.categoryName))
    const orderNo = String(row.orderNo ?? row.orderId ?? '')
    if (!isOrder && orderNo) {
      const order = scoped(orders).find((item) => [item.orderNo, item.id].some((value) => String(value ?? '') === orderNo))
      return Boolean(order && categoryAllowedRecord(order, true))
    }
    return false
  }
  const scopedOpportunities = scoped(allOpportunities).filter((row) => categoryAllowedRecord(row))
  const scopedLeads = scoped(leads).filter((row) => categoryAllowedRecord(row))
  const allowedOrders = scoped(orders).filter((row) => categoryAllowedRecord(row, true))
  const allowedReceivables = scoped(receivables).filter((row) => !frontendPolicy.value.constrainedCategories || allowedOrders.some((order) => String(order.orderNo ?? order.id) === String(row.orderNo ?? row.orderId ?? '')))
  const scopedPayments = scoped(payments).filter((row) => !frontendPolicy.value.constrainedCategories || allowedOrders.some((order) => String(order.orderNo ?? order.id) === String(row.orderNo ?? row.orderId ?? '')))
  const allowedQuotes = scoped(quotes).filter((row) => categoryAllowedRecord(row))
  const allowedFollowups = scoped(followups).filter((row) => categoryAllowedRecord(row))
  const allowedTickets = scoped(tickets).filter((row) => categoryAllowedRecord(row))
  const allowedProducts = scoped(products).filter((row) => !frontendPolicy.value.constrainedCategories || allowedCategories.has(String(row.category ?? row.categoryName ?? '')))
  // “我的待办”按个人负责人过滤；经营概览与待办列表保持同一口径，避免展示后无法在列表中找到。
  const scopedTodos = scoped(todos).filter(isTodoOwnedByCurrentUser)
  todoItems.value = scopedTodos.filter((row) => row.status !== '已完成').slice(0, 5)
  customerCount.value = scoped(customers).length
  opportunityRows.value = scopedOpportunities.slice(0, 4)
  allOpportunityRows.value = scopedOpportunities
  salesRows.value = scoped(reports)
  leadRows.value = scopedLeads
  paymentRows.value = scopedPayments
  reminderRows.value = makeReminders({ todos: scopedTodos, customers: scoped(customers), followups: allowedFollowups, quotes: allowedQuotes, receivables: allowedReceivables, tickets: allowedTickets, products: allowedProducts })
  departments.value = canSelectDepartment.value ? departmentRows.filter((department) => department.status === '启用') : []
  dashboardLoading.value = false
  if (failed) ElMessage.warning('部分经营数据未能载入，请检查对应资源的服务权限或连接状态。')
}

async function finishTodo(row: CrmRecord): Promise<void> {
  if (!canEditTodos.value) { ElMessage.error('当前账号没有完成待办的权限'); return }
  const id = String(row.id)
  if (finishingTodoIds.value.has(id)) return
  finishingTodoIds.value = new Set(finishingTodoIds.value).add(id)
  try {
    await moduleApi.setStatus('todos', [row.id], '已完成')
    todoItems.value = todoItems.value.filter((item) => String(item.id) !== id)
    ElMessage.success('已完成这项待办')
  } catch {
    ElMessage.error('待办完成失败，请检查连接后重试')
  } finally {
    const next = new Set(finishingTodoIds.value)
    next.delete(id)
    finishingTodoIds.value = next
  }
}

onMounted(() => {
  unsubscribeFrontendPolicy = subscribeFrontendPolicyChange(() => { frontendPolicyRevision.value += 1; void load() })
  void load()
})
onBeforeUnmount(() => { loadSequence += 1; unsubscribeFrontendPolicy?.() })
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-welcome">
      <div>
        <div class="page-eyebrow"><span class="eyebrow-mark"></span>YOUR BUSINESS, IN FOCUS</div>
        <h1>{{ greeting }}，{{ auth.user?.name ?? '伙伴' }} <span class="welcome-leaf">✳</span></h1>
        <p>今天是 {{ now.format('YYYY年MM月DD日 dddd') }}。这里汇总了当前授权数据范围内的经营进展。</p>
      </div>
      <div class="dashboard-welcome__controls">
        <div v-if="canSelectDepartment" class="dashboard-scope-filter"><span>数据范围</span><el-select v-model="departmentFilter" class="dashboard-scope-select" size="small" @change="load"><el-option label="全部部门" value="__all__" /><el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" /></el-select></div>
        <div v-if="canViewTodos || auth.can('crm:lead:add')" class="welcome-actions">
          <el-button v-if="canViewTodos" class="button-secondary" :icon="Calendar" @click="router.push('/todos')">查看待办</el-button>
          <el-button v-if="auth.can('crm:lead:add') && canViewLeads" type="primary" :icon="Plus" @click="router.push('/leads?create=1')">新增线索</el-button>
        </div>
      </div>
    </div>

    <el-alert v-if="financialAnalyticsUnreliable || hideSensitiveAmounts" class="dashboard-scope-note" title="部分金额指标已隐藏" :description="sensitiveAmountDescription" type="warning" :closable="false" show-icon />

    <section class="stats-grid dashboard-stats">
      <StatCard label="本年销售额" :value="canViewSales ? (dashboardLoading ? '—' : hasResourceError('reports') ? formatMoney(0) : financialAnalyticsUnreliable ? '—' : hideSensitiveAmounts ? '••••' : formatMoney(salesTotal)) : '—'" :note="canViewSales ? (dashboardLoading ? '正在读取当前范围数据' : hasResourceError('reports') ? '统计暂不可用，已按 0 显示' : financialAnalyticsUnreliable ? '服务端汇总无法按当前前端范围重算' : hideSensitiveAmounts ? '敏感金额已隐藏' : '按授权的销售统计计算') : '无销售统计访问权限'" tone="mint" />
      <StatCard label="客户总数" :value="canViewCustomers ? (dashboardLoading ? '—' : hasResourceError('customers') ? '0' : customerCount.toLocaleString('zh-CN')) : '—'" :note="canViewCustomers ? (dashboardLoading ? '正在读取当前范围数据' : hasResourceError('customers') ? '客户数据暂不可用，已按 0 显示' : '基于客户档案资源') : '无客户档案访问权限'" tone="mint" />
      <StatCard label="进行中商机" :value="canViewOpportunities ? (dashboardLoading ? '—' : hasResourceError('opportunities') ? '0' : String(activeOpportunities.length)) : '—'" :note="canViewOpportunities ? (dashboardLoading ? '正在读取当前范围数据' : hasResourceError('opportunities') ? '商机数据暂不可用，已按 0 显示' : financialAnalyticsUnreliable ? '加权金额不可按本地范围重算' : hideSensitiveAmounts ? '加权金额已隐藏' : `加权金额 ${formatMoney(weightedOpportunityAmount)}`) : '无商机访问权限'" tone="gold" />
      <StatCard label="本月回款" :value="canViewPayments ? (dashboardLoading ? '—' : hasResourceError('payments') ? formatMoney(0) : financialAnalyticsUnreliable ? '—' : hideSensitiveAmounts ? '••••' : formatMoney(monthPaymentTotal)) : '—'" :note="canViewPayments ? (dashboardLoading ? '正在读取当前范围数据' : hasResourceError('payments') ? '回款数据暂不可用，已按 0 显示' : financialAnalyticsUnreliable ? '服务端汇总无法按本地范围重算' : hideSensitiveAmounts ? '敏感金额已隐藏' : '按当前月份已登记回款统计') : '无收款记录访问权限'" tone="rose" />
    </section>

    <section v-if="canViewSales || canViewLeads" class="dashboard-grid dashboard-grid--main">
      <article v-if="canViewSales && !financialAnalyticsUnreliable && !hideSensitiveAmounts" class="panel chart-panel sales-chart-panel">
        <div class="panel-heading">
          <div><div class="panel-kicker">REVENUE TREND</div><h2>销售额趋势</h2><p>按当前授权的数据范围统计</p></div>
          <el-select v-model="salesPeriod" class="period-select" size="small" aria-label="选择统计周期"><el-option label="近 6 个月" value="近 6 个月" /><el-option label="本年度" value="本年度" /></el-select>
        </div>
        <el-alert v-if="hasResourceError('reports')" class="resource-alert" title="销售趋势暂不可用，当前显示为空" type="info" :closable="false" show-icon><template #default><el-button text size="small" :loading="dashboardLoading" @click="load">重试</el-button></template></el-alert>
        <el-skeleton v-else-if="dashboardLoading" :rows="5" animated />
        <EChart v-else :option="salesOption" height="286px" />
      </article>
      <article v-if="canViewLeads" class="panel chart-panel source-chart-panel">
        <div class="panel-heading"><div><div class="panel-kicker">LEAD SOURCES</div><h2>线索来源</h2><p>不同渠道带来的有效线索占比</p></div><el-button text class="icon-button" @click="router.push('/leads')"><ArrowRight /></el-button></div>
        <el-alert v-if="hasResourceError('leads')" class="resource-alert" title="线索来源暂不可用，当前显示为空" type="info" :closable="false" show-icon><template #default><el-button text size="small" :loading="dashboardLoading" @click="load">重试</el-button></template></el-alert>
        <el-skeleton v-else-if="dashboardLoading" :rows="5" animated />
        <template v-else><EChart :option="sourceOption" height="286px" /><div class="source-center"><strong>{{ leadTotal.toLocaleString('zh-CN') }}</strong><span>线索总数</span></div></template>
      </article>
    </section>

    <section v-if="canViewTodos || canViewOpportunities" class="dashboard-grid dashboard-grid--lower">
      <article v-if="canViewTodos" class="panel task-panel">
        <div class="panel-heading">
          <div><div class="panel-kicker">UP NEXT</div><h2>待办事项</h2><p>当前授权范围内的待办记录，可能包含逾期与未来日期。</p></div>
          <div style="display: flex; gap: 8px; align-items: center;">
            <el-button size="small" type="primary" plain :icon="Refresh" :loading="scanningTodos" @click="runAutoGenerateTodos">自动扫描业务待办</el-button>
            <el-button text class="text-link" @click="router.push('/todos')">全部待办 <ArrowRight /></el-button>
          </div>
        </div>
        <el-alert v-if="hasResourceError('todos')" class="resource-alert" title="待办数据暂不可用，当前显示为空" type="info" :closable="false" show-icon><template #default><el-button text size="small" :loading="dashboardLoading" @click="load">重试</el-button></template></el-alert>
        <el-skeleton v-else-if="dashboardLoading" :rows="3" animated />
        <div v-else-if="todoItems.length" class="task-list">
          <div v-for="(task, index) in todoItems" :key="task.id" class="task-row">
            <button v-if="canEditTodos" class="task-check" type="button" :aria-label="`完成${task.title}`" :disabled="finishingTodoIds.has(String(task.id))" :aria-busy="finishingTodoIds.has(String(task.id))" @click="finishTodo(task)"><CircleCheck /></button>
            <div class="task-copy"><strong>{{ task.title }}</strong><span>{{ task.related }}</span></div>
            <span class="task-due"><Clock />{{ task.dueDate }}</span>
            <span v-if="task.priority === '高'" class="priority-dot" title="高优先级"></span>
            <div v-if="index === 0" class="task-accent"></div>
          </div>
        </div>
        <el-empty v-else description="当前授权范围内暂无待办" :image-size="70" />
      </article>
      <article v-if="canViewOpportunities" class="panel opportunity-panel">
        <div class="panel-heading">
          <div><div class="panel-kicker">ACTIVE DEALS</div><h2>重点商机</h2><p>离成交更近的几个机会</p></div>
          <el-button text class="text-link" @click="router.push('/opportunities')">查看商机 <ArrowRight /></el-button>
        </div>
        <el-alert v-if="hasResourceError('opportunities')" class="resource-alert" title="重点商机暂不可用，当前显示为空" type="info" :closable="false" show-icon><template #default><el-button text size="small" :loading="dashboardLoading" @click="load">重试</el-button></template></el-alert>
        <el-skeleton v-else-if="dashboardLoading" :rows="3" animated />
        <div v-else-if="opportunityRows.length" class="opportunity-list">
          <router-link v-for="deal in opportunityRows.slice(0, 3)" :key="deal.id" to="/opportunities" class="opportunity-row">
            <div class="deal-avatar">{{ String(deal.customer ?? '客').slice(0, 1) }}</div>
            <div class="deal-copy"><strong>{{ deal.name }}</strong><span>{{ deal.customer }} · {{ deal.stage }}</span></div>
            <div class="deal-value"><strong>{{ hideSensitiveAmounts || financialAnalyticsUnreliable ? '••••' : formatMoney(deal.amount) }}</strong><span>{{ deal.probability ?? 65 }}% 成交概率</span></div>
          </router-link>
        </div>
        <el-empty v-else description="当前授权范围内暂无重点商机" :image-size="60" />
      </article>
    </section>

    <section v-if="canViewTodos || auth.can('crm:followup:list') || auth.can('sales:quote:list') || auth.can('delivery:receivable:list') || auth.can('service:ticket:list') || auth.can('product:product:list')" class="panel dashboard-reminders">
      <div class="panel-heading"><div><div class="panel-kicker">ACTIONABLE SIGNALS</div><h2>经营提醒</h2><p>仅根据当前授权接口已返回的待办、有效期、到期日和库存字段生成，不写入提醒记录。</p></div><el-tag type="info" effect="plain" round>{{ dashboardLoading ? '读取中' : `${reminderRows.length} 项` }}</el-tag></div>
      <el-skeleton v-if="dashboardLoading" :rows="3" animated />
      <div v-else-if="reminderRows.length" class="dashboard-reminder-list">
        <button v-for="reminder in reminderRows" :key="reminder.id" class="dashboard-reminder-row" type="button" @click="router.push(reminder.route)">
          <el-tag :type="reminder.kind === 'overdue' ? 'danger' : reminder.kind === 'inventory' ? 'warning' : 'success'" effect="light" round>{{ reminder.kind === 'overdue' ? '已逾期' : reminder.kind === 'inventory' ? '库存关注' : '即将到期' }}</el-tag>
          <span class="dashboard-reminder-copy"><strong>{{ reminder.title }}</strong><small>{{ reminder.detail || '来自当前授权业务数据' }}</small></span>
          <time>{{ reminder.due || '查看库存' }}</time><ArrowRight />
        </button>
      </div>
      <el-alert v-else-if="reminderHasError" class="resource-alert" title="经营提醒暂不可用，当前显示为空" type="info" :closable="false" show-icon><template #default><el-button text size="small" :loading="dashboardLoading" @click="load">重试</el-button></template></el-alert>
      <el-empty v-else description="当前授权资源内暂无需要处理的经营提醒" :image-size="60" />
    </section>

    <div class="dashboard-footnote"><span class="status-pulse"></span>数据更新于刚刚 <span>·</span> 您可以通过左侧导航访问所有经营模块</div>
  </div>
</template>

<style scoped>
.dashboard-scope-note { margin-bottom: 14px; }
.dashboard-reminders { margin-top: 14px; padding: 18px; }
.dashboard-reminders :deep(.panel-heading) { margin-bottom: 12px; }
.dashboard-reminder-list { display: grid; }
.dashboard-reminder-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto 18px; align-items: center; gap: 12px; width: 100%; min-width: 0; padding: 11px 2px; border: 0; border-top: 1px solid #edf1ed; color: inherit; background: transparent; text-align: left; cursor: pointer; }
.dashboard-reminder-row:hover { background: #f9fbf8; }
.dashboard-reminder-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.dashboard-reminder-copy strong { overflow: hidden; color: #43584a; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-reminder-copy small { overflow: hidden; color: #8f9c91; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-reminder-row time { color: #8f9a90; font-size: 10px; white-space: nowrap; }
.dashboard-reminder-row > :last-child { color: #9daa9f; font-size: 14px; }
.opportunity-panel .opportunity-row { color: inherit; text-decoration: none; }
.opportunity-panel .opportunity-row:focus-visible { outline: 2px solid #528a70; outline-offset: 2px; border-radius: 6px; }
.task-check:disabled { cursor: wait; opacity: .55; }
@media (max-width: 640px) {
  .dashboard-reminders { padding: 13px; }
  .dashboard-reminder-row { grid-template-columns: auto minmax(0, 1fr) 18px; gap: 8px; }
  .dashboard-reminder-row time { grid-column: 2; grid-row: 2; }
  .dashboard-reminder-row > :last-child { grid-column: 3; grid-row: 1 / span 2; }
}
</style>
