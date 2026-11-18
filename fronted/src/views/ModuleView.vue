<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormItemRule, type FormRules, type TableInstance } from 'element-plus'
import { ArrowDown, ArrowRight, Check, Delete, Download, Edit, MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import dayjs from 'dayjs'
import PageHeading from '../components/PageHeading.vue'
import StatCard from '../components/StatCard.vue'
import BusinessRelationsPanel from '../components/BusinessRelationsPanel.vue'
import LeadJourneyPanel from '../components/LeadJourneyPanel.vue'
import ProductSkuPanel from '../components/ProductSkuPanel.vue'
import { systemApi, crmBusinessApi } from '../api/system'
import CustomerWorkspace from '../components/CustomerWorkspace.vue'
import { moduleDefinitions, type FormField, type ModuleDefinition, type SelectOption, type TableColumn } from '../data/modules'
import { moduleApi } from '../api/modules'
import { organizationApi } from '../api/organization'
import type { Department } from '../data/organization'
import type { CrmLineItem, CrmRecord, CrmValue, QueryState } from '../api/types'
import { formatDate, formatMoney, formatNumber, escapeCsv } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { isSensitiveMoneyFieldKey, redactSensitiveMoneyFields, recordInFrontendDataScope, resolveFrontendPolicy, subscribeFrontendPolicyChange, type EffectiveFrontendPolicy } from '../utils/frontend-policy'
import { useDelayedLoading } from '../utils/useDelayedLoading'

const EChart = defineAsyncComponent(() => import('../components/EChart.vue'))
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const tableRef = ref<TableInstance>()
const formRef = ref<FormInstance>()
const loadIndicator = useDelayedLoading()
const currentModule = computed<ModuleDefinition>(() => moduleDefinitions[String(route.meta.moduleKey)] ?? moduleDefinitions.todos)
const loading = loadIndicator.visible
const loadFailed = ref(false)
const loadErrorMessage = ref('')
const pageRows = ref<CrmRecord[]>([])
const allRows = ref<CrmRecord[]>([])
const total = ref(0)
const selectedRows = ref<CrmRecord[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const search = ref('')
const filters = reactive<Record<string, string>>({})
const reportRange = ref<string[]>([])
const dialogOpen = ref(false)
const drawerOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formModel = ref<Record<string, CrmValue>>({})
const lineItems = ref<CrmLineItem[]>([])
const detailRecord = ref<CrmRecord | null>(null)
const saving = ref(false)
const linkedOptions = ref<Record<string, SelectOption[]>>({})
const linkedProductPrices = ref<Record<string, number>>({})
const linkedOptionsLoaded = ref<Record<string, boolean>>({})
const linkedOptionsRequested = ref<Record<string, boolean>>({})
const linkedOptionsErrors = ref<Record<string, boolean>>({})
const departments = ref<Department[]>([])
const departmentFilter = ref('')
const productCatalog = ref<CrmRecord[]>([])
const productCategoryRows = ref<CrmRecord[]>([])
const selectedCategoryName = ref('')
const categoryTreeOpen = ref(false)
const leadView = ref<'all' | 'mine' | 'unassigned'>('all')
const currentSort = ref<{ prop?: string; order?: 'ascending' | 'descending' }>({})
const quoteApprovalDrafts = ref<Record<string, '已通过' | '已拒绝'>>({})
const opportunityViewMode = ref<'table' | 'kanban'>('kanban')
const KANBAN_STAGES = ['需求发现', '产品推荐', '试用/样品', '报价', '议价', '待确认', '赢单', '输单'] as const

const kanbanColumns = computed(() => {
  return KANBAN_STAGES.map((stage) => {
    const items = allRows.value.filter((row) => String(row.stage ?? '需求发现') === stage)
    const amount = items.reduce((sum, item) => sum + Number(item.amount ?? 0), 0)
    return {
      stage,
      items,
      count: items.length,
      amount,
    }
  })
})

const draggingOpportunity = ref<CrmRecord | null>(null)

function onKanbanDragStart(e: DragEvent, item: CrmRecord) {
  draggingOpportunity.value = item
  if (e.dataTransfer) {
    e.dataTransfer.setData('text/plain', String(item.id))
    e.dataTransfer.effectAllowed = 'move'
  }
}

async function onKanbanDrop(targetStage: string) {
  const item = draggingOpportunity.value
  draggingOpportunity.value = null
  if (!item || String(item.stage) === targetStage) return
  if (!canEdit.value) {
    ElMessage.warning('当前账号无权推进商机阶段')
    return
  }

  let lossReason = ''
  if (targetStage === '输单') {
    try {
      const { value } = await ElMessageBox.prompt('商机推进至输单阶段，请必须填写丢单原因：', '填写丢单原因', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '丢单原因不能为空',
      })
      lossReason = value
    } catch {
      return
    }
  }

  try {
    const probabilityMap: Record<string, number> = {
      '需求发现': 20,
      '产品推荐': 40,
      '试用/样品': 60,
      '报价': 75,
      '议价': 85,
      '待确认': 95,
      '赢单': 100,
      '输单': 0,
    }
    const updateData: Record<string, any> = {
      stage: targetStage,
      probability: probabilityMap[targetStage] ?? 50,
      status: targetStage === '赢单' ? '已完成' : targetStage === '输单' ? '已关闭' : '进行中',
    }
    if (lossReason) updateData.lossReason = lossReason

    await moduleApi.update('opportunities', item.id, updateData)
    ElMessage.success(`商机「${item.name || item.id}」已移动至【${targetStage}】`)
    await load()
  } catch (err: any) {
    ElMessage.error(err.message || '阶段更新失败')
  }
}
const frontendPolicyRevision = ref(0)
let linkedOptionsRequest = 0
let unsubscribeFrontendPolicy: (() => void) | undefined
const isReport = computed(() => currentModule.value.key.startsWith('reports-'))
const hasDepartmentScope = computed(() => currentModule.value.key !== 'todos')
const canSelectDepartment = computed(() => hasDepartmentScope.value && auth.can('system:data:all'))
const selectedDepartmentId = computed(() => canSelectDepartment.value ? departmentFilter.value : auth.user?.departmentId ?? '')
const canAdd = computed(() => !isReport.value && auth.can(currentModule.value.permission.replace(':list', ':add')))
const actionPermission = computed(() => currentModule.value.permission.replace(':list', ':edit'))
const removePermission = computed(() => currentModule.value.permission.replace(':list', ':remove'))
const canEdit = computed(() => auth.can(actionPermission.value))
const canRemove = computed(() => auth.can(removePermission.value))
const exportPermission = computed(() => isReport.value ? 'report:export' : currentModule.value.permission.replace(/:list$/, ':export'))
const canExport = computed(() => Boolean(exportPermission.value) && auth.can(exportPermission.value))
const frontendPolicy = computed<EffectiveFrontendPolicy>(() => { frontendPolicyRevision.value; return resolveFrontendPolicy(auth.user?.roleIds ?? []) })
const canViewSensitivePrices = computed(() => frontendPolicy.value.viewSensitivePrices || auth.can('system:data:all'))
const hasCategoryRestriction = computed(() => frontendPolicy.value.constrainedCategories && !auth.can('system:data:all'))
const localPagination = computed(() => currentModule.value.key === 'todos' || hasCategoryRestriction.value || (frontendPolicy.value.configured && frontendPolicy.value.dataScope !== '全部可见范围') || (currentModule.value.key === 'product-categories' && Boolean(selectedCategoryName.value)) || (currentModule.value.key === 'leads' && leadView.value !== 'all'))
const canBatchChangeStatus = computed(() => currentModule.value.key === 'todos')
const hasActiveFilters = computed(() => Boolean(search.value.trim() || Object.values(filters).some(Boolean) || departmentFilter.value || reportRange.value.length || selectedCategoryName.value || (currentModule.value.key === 'leads' && leadView.value !== 'all')))
const productScopeNotice = computed(() => hasCategoryRestriction.value ? `当前角色前端演示策略限制为 ${frontendPolicy.value.categoryNames.length} 个品类；真实安全范围必须由后端授权校验。` : '')
const permittedCategoryNames = computed(() => {
  const permitted = new Set(frontendPolicy.value.categoryNames)
  if (!hasCategoryRestriction.value) return permitted
  const sourceRows = currentModule.value.key === 'product-categories' ? (productCategoryRows.value.length ? productCategoryRows.value : allRows.value) : productCategoryRows.value
  let changed = true
  while (changed) {
    changed = false
    for (const category of sourceRows) {
      const name = String(category.name ?? '')
      if (permitted.has(String(category.parent ?? '')) && !permitted.has(name)) { permitted.add(name); changed = true }
    }
  }
  return permitted
})
const ownerScopeNotice = computed(() => frontendPolicy.value.configured && frontendPolicy.value.dataScope !== '全部可见范围' ? `当前浏览器前端演示策略按“${frontendPolicy.value.dataScope}”进一步收窄已授权结果；后端数据范围仍为最终安全边界。` : '')
interface CategoryTreeNode { id: string; name: string; children?: CategoryTreeNode[] }
const categoryTreeData = computed<CategoryTreeNode[]>(() => {
  const nodes = allRows.value.map((row) => ({ id: String(row.id), name: String(row.name ?? ''), children: [] as CategoryTreeNode[] }))
  const byName = new Map(nodes.map((node) => [node.name, node]))
  const roots: CategoryTreeNode[] = []
  for (const node of nodes) {
    const parentName = String(allRows.value.find((row) => String(row.id) === node.id)?.parent ?? '')
    const parent = byName.get(parentName)
    if (parent && parent.id !== node.id) parent.children?.push(node)
    else roots.push(node)
  }
  const clean = (items: CategoryTreeNode[]): CategoryTreeNode[] => items.map(({ id, name, children }) => ({ id, name, ...(children?.length ? { children: clean(children) } : {}) }))
  return clean(roots)
})
const categoryDescendantNames = computed(() => {
  const result = new Set<string>()
  if (!selectedCategoryName.value) return result
  const walk = (name: string) => {
    if (result.has(name)) return
    result.add(name)
    for (const row of allRows.value.filter((item) => String(item.parent ?? '') === name)) walk(String(row.name ?? ''))
  }
  walk(selectedCategoryName.value)
  return result
})
const hasLineItems = computed(() => currentModule.value.fields.some((field) => field.kind === 'lines'))
const lineItemsTotal = computed(() => lineItems.value.reduce((sum, item) => sum + Math.max(0, Number(item.quantity)) * Math.max(0, Number(item.unitPrice)) * Math.max(0, Math.min(100, Number(item.discount ?? 100))) / 100, 0))
const linkedResources: Record<string, string> = { customer: 'customers', contact: 'contacts', lead: 'leads', opportunity: 'opportunities', quoteNo: 'quotes', orderNo: 'orders', product: 'products', items: 'products', category: 'product-categories', parent: 'product-categories', owner: 'owners', assignee: 'owners' }
const linkedValueFields: Record<string, string[]> = { customer: ['name', 'customerName'], contact: ['name', 'contactName'], lead: ['name', 'title', 'company'], opportunity: ['name', 'opportunityName'], quoteNo: ['quoteNo', 'quoteCode'], orderNo: ['orderNo', 'orderCode'], product: ['name', 'productName'], items: ['name', 'productName'], category: ['name', 'categoryName'], parent: ['name', 'categoryName'], owner: ['name'], assignee: ['name'] }

const formRules = computed<FormRules>(() => Object.fromEntries(currentModule.value.fields.map((field) => {
  const rules: FormItemRule[] = []
  if (field.required) rules.push({ required: true, message: `请填写${field.label}`, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'opportunities' && field.key === 'probability') rules.push({ validator: (_rule, value, callback) => { const number = Number(value); if (value !== '' && value !== undefined && (!Number.isFinite(number) || number < 0 || number > 100)) callback(new Error('成交概率须在 0–100 之间')); else callback() }, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'returns' && field.key === 'quantity') rules.push({ validator: (_rule, value, callback) => { const number = Number(value); if (!Number.isInteger(number) || number <= 0) callback(new Error('退换货数量须为正整数')); else callback() }, trigger: ['blur', 'change'] })
  if (['returns', 'payments'].includes(currentModule.value.key) && field.key === 'amount') rules.push({ validator: (_rule, value, callback) => { const blank = value === '' || value === undefined || value === null; if (currentModule.value.key === 'payments' && (blank || !Number.isFinite(Number(value)) || Number(value) <= 0)) callback(new Error('收款金额须大于 0')); else if (currentModule.value.key === 'returns' && !blank && (!Number.isFinite(Number(value)) || Number(value) <= 0)) callback(new Error('涉及金额须大于 0，或留空')); else callback() }, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'receivables' && field.key === 'received') rules.push({ validator: (_rule, value, callback) => { const received = Number(value || 0); const amount = Number(formModel.value.amount || 0); if (received < 0 || (amount > 0 && received > amount)) callback(new Error('已收金额不能小于 0，也不能超过应收金额')); else callback() }, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'quotes' && field.key === 'validUntil') rules.push({ validator: (_rule, value, callback) => { const date = String(value || ''); if (date && date < dayjs().format('YYYY-MM-DD')) callback(new Error('报价有效期不能早于今天')); else callback() }, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'opportunities' && field.key === 'lossReason') rules.push({ validator: (_rule, value, callback) => { if (String(formModel.value.stage || '') === '输单' && !String(value || '').trim()) callback(new Error('输单时请填写丢单原因')); else callback() }, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'customer-prices' && field.key === 'price') rules.push({ validator: (_rule, value, callback) => { const minimum = Number(formModel.value.minimumPrice || 0); if (minimum > 0 && Number(value || 0) < minimum) callback(new Error('客户单价不能低于最低成交价')); else callback() }, trigger: ['blur', 'change'] })
  if (currentModule.value.key === 'customer-prices' && field.key === 'maxQuantity') rules.push({ validator: (_rule, value, callback) => { const max = Number(value || 0); const min = Number(formModel.value.minQuantity || 0); if (max > 0 && max < min) callback(new Error('数量上限不能小于起订数量')); else callback() }, trigger: ['blur', 'change'] })
  return [field.key, rules]
})))
const titleAction = computed(() => currentModule.value.primaryAction ?? `新建${currentModule.value.singular}`)
const hasFilters = computed(() => currentModule.value.filters.length > 0)
const visibleFormFields = computed(() => currentModule.value.fields.filter((field) => canViewSensitivePrices.value || (field.kind !== 'money' && !isSensitiveMoneyFieldKey(field.key))))
function numberInputMin(field: FormField): number { return currentModule.value.key === 'returns' && field.key === 'quantity' ? 1 : 0 }
function numberInputMax(field: FormField): number | undefined { return currentModule.value.key === 'opportunities' && field.key === 'probability' ? 100 : undefined }

function isCurrentMonth(value: unknown): boolean {
  const raw = String(value ?? '')
  if (raw.startsWith(dayjs().format('YYYY-MM'))) return true
  const match = raw.match(/^(\d{4})\D+(\d{1,2})/)
  return Boolean(match && match[1] === dayjs().format('YYYY') && Number(match[2]) === dayjs().month() + 1)
}
function productNameOfSku(value: string): string {
  const row = productCatalog.value.find((item) => String(item.sku ?? '') === value)
  return String(row?.name ?? row?.productName ?? value)
}
function fieldOptions(field: FormField): SelectOption[] {
  if (field.key === 'parent' && linkedOptionsLoaded.value.parent) return [{ label: '无上级分类', value: '—' }, ...(linkedOptions.value.parent ?? []).filter((option) => isCategoryNamePermitted(option.value) && !excludedCategoryNames.value.has(option.value))]
  if (linkedOptionsLoaded.value[field.key]) {
    const options = linkedOptions.value[field.key] ?? []
    if (hasCategoryRestriction.value && ['items', 'product'].includes(field.key)) return options.filter((option) => isCategoryPermittedByProductName(productNameOfSku(option.value)))
    if (hasCategoryRestriction.value && ['category', 'parent'].includes(field.key)) return options.filter((option) => isCategoryNamePermitted(option.value))
    return options
  }
  if (linkedOptionsRequested.value[field.key]) return []
  return field.options ?? []
}
const excludedCategoryNames = computed(() => {
  const excluded = new Set<string>()
  const editingId = String(detailRecord.value?.id ?? '')
  if (currentModule.value.key !== 'product-categories' || !editingId) return excluded
  const editing = allRows.value.find((row) => String(row.id) === editingId)
  if (!editing) return excluded
  const walk = (name: string) => {
    if (excluded.has(name)) return
    excluded.add(name)
    for (const row of allRows.value.filter((item) => String(item.parent ?? '') === name)) walk(String(row.name ?? ''))
  }
  walk(String(editing.name ?? ''))
  return excluded
})
function isCategoryNamePermitted(categoryName: unknown): boolean {
  if (!hasCategoryRestriction.value) return true
  return permittedCategoryNames.value.has(String(categoryName ?? ''))
}
function isCategoryPermittedByProductName(productName: unknown): boolean {
  const product = productCatalog.value.find((row) => String(row.name ?? row.productName ?? '') === String(productName ?? ''))
  return Boolean(product && isCategoryNamePermitted(product.category ?? product.categoryName))
}
function isRecordInCurrentUserScope(row: CrmRecord): boolean {
  return recordInFrontendDataScope(row, frontendPolicy.value, auth.user, departments.value)
}
function sortRecords(rows: CrmRecord[], sort = currentSort.value): CrmRecord[] {
  if (!sort.prop || !sort.order) return rows
  const factor = sort.order === 'descending' ? -1 : 1
  return [...rows].sort((a, b) => {
    const left = a[sort.prop!]
    const right = b[sort.prop!]
    const leftNumber = Number(left)
    const rightNumber = Number(right)
    const numeric = left !== undefined && left !== null && String(left) !== '' && right !== undefined && right !== null && String(right) !== '' && Number.isFinite(leftNumber) && Number.isFinite(rightNumber)
    const comparison = numeric ? leftNumber - rightNumber : String(left ?? '').localeCompare(String(right ?? ''), 'zh-CN', { numeric: true })
    return comparison * factor
  })
}
function isLeadOwnedByCurrentUser(row: CrmRecord): boolean {
  const identity = [auth.user?.name, auth.user?.id, auth.user?.account, auth.user?.phone].map((value) => String(value ?? '').trim().toLocaleLowerCase()).filter(Boolean)
  const rowValues = [row.owner, row.ownerId, row.assignee, row.assigneeId].filter((value) => value !== undefined && value !== null && String(value).trim() !== '').map((value) => String(value).trim().toLocaleLowerCase())
  return identity.length > 0 && rowValues.some((value) => identity.includes(value))
}
function isTodoOwnedByCurrentUser(row: CrmRecord): boolean {
  const identity = [auth.user?.name, auth.user?.id, auth.user?.account, auth.user?.phone].map((value) => String(value ?? '').trim().toLocaleLowerCase()).filter(Boolean)
  const ownerValues = [row.owner, row.ownerId, row.assignee, row.assigneeId].filter((value) => value !== undefined && value !== null && String(value).trim() !== '').map((value) => String(value).trim().toLocaleLowerCase())
  return identity.length > 0 && ownerValues.some((value) => identity.includes(value))
}
function matchesLeadView(row: CrmRecord): boolean {
  if (currentModule.value.key !== 'leads' || leadView.value === 'all') return true
  if (leadView.value === 'mine') return isLeadOwnedByCurrentUser(row)
  const hasOwner = [row.owner, row.ownerId, row.assignee, row.assigneeId].some((value) => value !== undefined && value !== null && String(value).trim() !== '')
  return !hasOwner || ['待认领', '待分配', '公海'].includes(String(row.status ?? row.stage ?? ''))
}
function matchesPoolView(row: CrmRecord): boolean {
  if (currentModule.value.key !== 'pool') return true
  const hasOwner = [row.owner, row.ownerId, row.assignee, row.assigneeId].some((value) => value !== undefined && value !== null && String(value).trim() !== '')
  return !hasOwner && ['待认领', '待分配', '公海', ''].includes(String(row.status ?? ''))
}
function onLeadViewChange(value: unknown): void {
  if (value !== 'all' && value !== 'mine' && value !== 'unassigned') return
  leadView.value = value
  currentPage.value = 1
  selectedRows.value = []
  void load()
}
function isRecordPermittedByCategory(row: CrmRecord): boolean {
  if (!hasCategoryRestriction.value) return true
  const key = currentModule.value.key
  if (key === 'product-categories') {
    const visibleNames = new Set(frontendPolicy.value.categoryNames)
    const categoryRows = allRows.value.length ? allRows.value : pageRows.value
    let changed = true
    while (changed) {
      changed = false
      for (const category of categoryRows) {
        const name = String(category.name ?? '')
        if (visibleNames.has(String(category.parent ?? '')) && !visibleNames.has(name)) { visibleNames.add(name); changed = true }
      }
    }
    return visibleNames.has(String(row.name ?? ''))
  }
  if (key === 'products') return isCategoryNamePermitted(row.category ?? row.categoryName)
  if (key === 'reports-product-sales') return isCategoryPermittedByProductName(row.product ?? row.productName) || isCategoryNamePermitted(row.category ?? row.categoryName)
  const itemProducts = asLineItems(row.items).map((item) => item.product)
  const productNames = [row.product, row.productName, ...itemProducts].filter((value) => value !== undefined && value !== null && String(value) !== '').map(String)
  if (productNames.length) return productNames.every(isCategoryPermittedByProductName)
  if (row.category || row.categoryName) return isCategoryNamePermitted(row.category ?? row.categoryName)
  // Related records lacking product/category lineage cannot be classified safely by this UI-only policy.
  return !['customer-prices', 'quotes', 'orders', 'returns', 'shipments'].includes(key)
}
function selectCategoryNode(node: CategoryTreeNode): void {
  selectedCategoryName.value = selectedCategoryName.value === node.name ? '' : node.name
  currentPage.value = 1
  void load()
}
async function loadLinkedOptions(): Promise<void> {
  const request = ++linkedOptionsRequest
  const fields = [...currentModule.value.fields, ...currentModule.value.filters].filter((field) => linkedResources[field.key])
  const resources = [...new Set(fields.map((field) => linkedResources[field.key]))]
  linkedOptions.value = {}
  linkedProductPrices.value = {}
  linkedOptionsRequested.value = Object.fromEntries(fields.map((field) => [field.key, true]))
  linkedOptionsLoaded.value = {}
  linkedOptionsErrors.value = {}
  const canIncludeChildDepartments = frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本部门及下级'
  const departmentId = canSelectDepartment.value ? String(dialogOpen.value ? formModel.value.departmentId ?? '' : departmentFilter.value) : canIncludeChildDepartments ? '' : auth.user?.departmentId ?? ''
  const scopedFilters: Record<string, string> = departmentId ? { departmentId } : {}
  await Promise.all(resources.map(async (resource) => {
    try {
      const records: CrmRecord[] = resource === 'owners'
        ? (await organizationApi.ownerOptions()).map((name) => ({ id: name, name }))
        : (await moduleApi.all(resource, { filters: scopedFilters })).filter(isRecordInCurrentUserScope)
      if (request !== linkedOptionsRequest) return
      if (resource === 'products') linkedProductPrices.value = Object.fromEntries(records.map((record) => [String(record.sku ?? record.name ?? record.productName ?? ''), Number(record.price ?? record.standardPrice ?? 0)]).filter(([key]) => key))
      if (resource === 'products') productCatalog.value = records
      if (resource === 'product-categories') productCategoryRows.value = records
      for (const field of fields.filter((item) => linkedResources[item.key] === resource)) {
        // 产品档案已按 SKU 返回：报价/订单明细与客户价格按「SKU 编码」选品，展示为「产品名 + 规格（SKU）」
        const skuPicked = resource === 'products' && (field.key === 'items' || (field.key === 'product' && currentModule.value.key === 'customer-prices'))
        if (skuPicked) {
          const skuOptions = records.map((record) => {
            const sku = String(record.sku ?? '')
            const label = String(record.displayName ?? record.name ?? record.productName ?? '')
            if (!sku) return label ? { label, value: label } : null
            return { label: label ? `${label}（${sku}）` : sku, value: sku }
          }).filter((option): option is SelectOption => option !== null)
          linkedOptions.value[field.key] = skuOptions
          linkedOptionsLoaded.value[field.key] = true
          continue
        }
        const keys = linkedValueFields[field.key] ?? ['name']
        const options = records.map((record) => {
          const value = keys.map((key) => record[key]).find((candidate) => candidate !== undefined && candidate !== null && String(candidate) !== '')
          return value === undefined ? null : { label: String(value), value: String(value) }
        }).filter((option): option is SelectOption => option !== null)
        linkedOptions.value[field.key] = [...new Map(options.map((option) => [option.value, option])).values()]
        linkedOptionsLoaded.value[field.key] = true
      }
    } catch {
      if (request !== linkedOptionsRequest) return
      for (const field of fields.filter((item) => linkedResources[item.key] === resource)) linkedOptionsErrors.value[field.key] = true
    }
  }))
}
async function loadDepartments(): Promise<void> {
  const canReadTreeForPreview = frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本部门及下级' && auth.can('system:dept:list')
  if (!auth.can('system:data:all') && !canReadTreeForPreview) { departments.value = []; return }
  try {
    const rows = await organizationApi.departments()
    departments.value = auth.can('system:data:all') ? rows.filter((department) => department.status === '启用') : rows
  }
  catch (error) { departments.value = []; ElMessage.warning(error instanceof Error ? error.message : '部门选项暂时无法载入') }
}
function departmentName(id: unknown): string {
  const value = String(id ?? '')
  if (value === '__all__') return '全部部门'
  return departments.value.find((department) => department.id === value)?.name ?? (value === auth.user?.departmentId ? auth.user.department : value || '—')
}
function onDepartmentFilterChange(): void { currentPage.value = 1; selectedRows.value = []; void loadLinkedOptions(); void load() }
function retryLinkedOptions(): void { void loadLinkedOptions() }
async function onFormDepartmentChange(value: unknown): Promise<void> {
  formModel.value.departmentId = String(value ?? '')
  for (const field of currentModule.value.fields) if (linkedResources[field.key]) formModel.value[field.key] = ''
  if (hasLineItems.value) lineItems.value = []
  await loadLinkedOptions()
}
function uniqueCustomers(rows: CrmRecord[]): number { return new Set(rows.map((row) => String(row.customer ?? row.company ?? row.name ?? '')).filter(Boolean)).size }
function statNumber(key: string): number | string {
  const rows = allRows.value
  const statuses = (status: string) => rows.filter((row) => String(row.status ?? '') === status).length
  const amount = rows.reduce((sum, row) => sum + Number(row.amount ?? row.annualValue ?? 0), 0)
  const monthlyRows = rows.filter((row) => isCurrentMonth(row.month ?? row.date ?? row.orderDate ?? row.createdAt))
  switch (key) {
    case '总数': case '客户总数': return total.value
    case '有效线索': return rows.filter((row) => row.stage !== '转化成功' && row.status !== '已暂停').length
    case '新线索': return rows.filter((row) => ['新线索', '待分配', '已分配'].includes(String(row.stage ?? ''))).length
    case 'today': return rows.filter((row) => String(row.dueDate ?? '') === dayjs().format('YYYY-MM-DD')).length
    case '待处理': {
      const moduleKey = currentModule.value.key
      if (moduleKey === 'followups') return rows.filter((row) => !['已完成', '已暂停'].includes(String(row.status ?? '')) && Boolean(row.nextDate)).length
      // 订单与发货按履约状态判断「待履约」，退换货按处理节点判断，其余按状态字段
      if (moduleKey === 'orders' || moduleKey === 'shipments') return rows.filter((row) => ['未发货', '部分发货', '待处理'].includes(String(row.deliveryStatus ?? row.status ?? ''))).length
      if (moduleKey === 'returns') return rows.filter((row) => ['待审核', '待寄回', '处理中', '待发出'].includes(String(row.status ?? ''))).length
      return rows.filter((row) => String(row.status ?? '') === '待处理').length
    }
    case '待认领': case '待分配': case '待跟进': return rows.filter((row) => String(row.status ?? '') === key).length
    case '已完成': return rows.filter((row) => ['已完成', '已签收', '已结清', '已解决'].includes(String(row.status ?? '')) || row.stage === '赢单').length
    case '进行中': return rows.filter((row) => ['进行中', '需求确认', '方案评估', '商务谈判'].includes(String(row.status ?? row.stage ?? ''))).length
    case '本月': {
      const datedRows = rows.filter((row) => row.month || row.createdAt || row.date || row.orderDate || row.hireDate)
      return datedRows.filter((row) => isCurrentMonth(row.month ?? row.createdAt ?? row.date ?? row.orderDate ?? row.hireDate)).length
    }
    case '本周': {
      const dateKey = currentModule.value.key === 'followups' ? 'date' : 'createdAt'
      const weekStart = dayjs().startOf('week').add(dayjs().day() === 0 ? -6 : 1, 'day')
      const start = weekStart.format('YYYY-MM-DD')
      const end = weekStart.add(6, 'day').format('YYYY-MM-DD')
      return rows.filter((row) => { const date = String(row[dateKey] ?? ''); return date >= start && date <= end }).length
    }
    case '高': case '紧急': return rows.filter((row) => row.priority === key).length
    case '重点客户': {
      const withLevel = rows.filter((row) => row.level !== undefined && row.level !== null && String(row.level).trim() !== '')
      if (withLevel.length) return withLevel.filter((row) => String(row.level).includes('重点') || String(row.level).includes('战略')).length
      // 没有客户等级时（如报表聚合行）按采购金额是否高于平均值判定高价值客户
      const average = rows.length ? rows.reduce((sum, row) => sum + Number(row.amount ?? 0), 0) / rows.length : 0
      return average > 0 ? rows.filter((row) => Number(row.amount ?? 0) >= average).length : 0
    }
    case '决策人': return currentModule.value.key === 'contacts' ? rows.filter((row) => ['老板', '店长', '决策人'].includes(String(row.role ?? ''))).length : statuses(key)
    case '新增商机': return rows.reduce((sum, row) => sum + Number(row.opportunities ?? 0), 0)
    case '客户数': case '覆蓋客户': case '覆盖客户': case '关联客户': return uniqueCustomers(rows)
    case '转化率': case '成交率': {
      const measured = rows.map((row) => Number(row.conversion ?? row.probability)).filter((value) => Number.isFinite(value) && value > 0)
      if (measured.length) return Math.round(measured.reduce((sum, value) => sum + value, 0) / measured.length)
      const completed = rows.filter((row) => ['转化成功', '赢单'].includes(String(row.stage ?? ''))).length
      return rows.length ? Math.round(completed / rows.length * 100) : 0
    }
    case '年度签约': {
      const year = dayjs().format('YYYY')
      return rows.filter((row) => {
        const period = String(row.month ?? row.date ?? row.createdAt ?? '')
        return !/\d{4}/.test(period) || period.includes(year)
      }).reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
    }
    case '总额': case '销售额': case '采购总额': return amount
    case '订单总额': return amount
    case '目标达成': {
      const target = monthlyRows.reduce((sum, row) => sum + Number(row.targetAmount ?? row.target ?? 0), 0)
      const achieved = monthlyRows.reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
      return target ? Math.round(achieved / target * 100) : 0
    }
    case '客单价': {
      const orderCount = rows.reduce((sum, row) => sum + Number(row.orders ?? 0), 0)
      return orderCount ? Math.round(amount / orderCount) : 0
    }
    case '待回款': return rows.filter((row) => row.paymentStatus !== '已结清').length
    case '余额': return rows.reduce((sum, row) => sum + Math.max(0, Number(row.amount ?? 0) - Number(row.received ?? 0)), 0)
    case '逾期': return rows.filter((row) => row.status === '逾期').length
    case '回款率': {
      const receivable = rows.reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
      const received = rows.reduce((sum, row) => sum + Number(row.received ?? 0), 0)
      return receivable ? Math.round(received / receivable * 100) : 0
    }
    case '运输中': return rows.filter((row) => String(row.status ?? '') === '运输中').length
    case '启用中': case '在售': return rows.filter((row) => ['启用', '启用中', '在售', '进行中'].includes(String(row.status ?? ''))).length
    case '低库存': return rows.filter((row) => Number(row.stock ?? 999) <= 20).length
    case '产品数': return currentModule.value.key === 'product-categories' ? rows.reduce((sum, row) => sum + Number(row.productCount ?? 0), 0) : rows.length
    case '分类数': return new Set(rows.map((row) => String(row.category ?? '')).filter(Boolean)).size
    case '有效': {
      const activeStatuses = ['进行中', '启用', '在售']
      const today = dayjs().format('YYYY-MM-DD')
      if (currentModule.value.key === 'customer-prices') {
        return rows.filter((row) => activeStatuses.includes(String(row.status ?? '')) && (!row.startDate || String(row.startDate) <= today) && (!row.endDate || String(row.endDate) >= today)).length
      }
      return rows.filter((row) => activeStatuses.includes(String(row.status ?? ''))).length
    }
    case '即将到期': return rows.filter((row) => { const date = String(row.endDate ?? row.validUntil ?? ''); return date && date >= dayjs().format('YYYY-MM-DD') && date <= dayjs().add(30, 'day').format('YYYY-MM-DD') }).length
    case '赢单': return rows.filter((row) => row.stage === '赢单').length
    case '本月金额':
      return rows.filter((row) => isCurrentMonth(row.date ?? row.createdAt ?? row.month)).reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
    case '本月到期': return rows.filter((row) => isCurrentMonth(row.dueDate)).length
    case '复购率': { const repeat = rows.filter((row) => Number(row.orders ?? 0) > 1).length; return rows.length ? Math.round(repeat / rows.length * 100) : 0 }
    case '畅销品': return String([...rows].sort((a, b) => Number(b.quantity ?? 0) - Number(a.quantity ?? 0))[0]?.product ?? '暂无数据')
    case '毛利率': { const weighted = rows.reduce((sum, row) => sum + Number(row.margin ?? 0) * Number(row.amount ?? 0), 0); const base = rows.reduce((sum, row) => sum + Number(row.amount ?? 0), 0); return base ? Math.round(weighted / base) : 0 }
    case '满意': return rows.filter((row) => ['非常满意', '满意'].includes(String(row.satisfaction ?? ''))).length
    default: return statuses(key)
  }
}

function displayStat(stat: ModuleDefinition['stats'][number]): string {
  const value = statNumber(stat.key)
  if (!canViewSensitivePrices.value && (stat.prefix === '¥' || /总额|销售额|签约|客单价|余额|金额/.test(stat.key))) return '••••'
  if (stat.prefix === '¥') return formatMoney(value)
  if (stat.prefix === '%') return `${value}%`
  return typeof value === 'number' ? formatNumber(value) : value
}
function matchesCurrentFilters(row: CrmRecord): boolean {
  const term = search.value.trim().toLowerCase()
  if (term && !Object.values(row).some((value) => String(value ?? '').toLowerCase().includes(term))) return false
  if (Object.entries(filters).some(([key, value]) => value && String(row[key] ?? '') !== value)) return false
  if (currentModule.value.key === 'product-categories' && selectedCategoryName.value && !categoryDescendantNames.value.has(String(row.name ?? ''))) return false
  if (isReport.value && reportRange.value.length === 2) {
    const rawDate = String(row.month ?? row.date ?? row.orderDate ?? row.lastPurchase ?? row.createdAt ?? row.purchaseDate ?? '')
    const monthParts = rawDate.match(/^(\d{4})\D+(\d{1,2})/)
    const rangeStart = reportRange.value[0]
    const rangeEnd = reportRange.value[1]
    if (monthParts || rawDate.length === 7) {
      const year = monthParts?.[1] ?? rawDate.slice(0, 4)
      const month = monthParts?.[2] ?? rawDate.slice(5, 7)
      const monthStart = dayjs(`${year}-${month.padStart(2, '0')}-01`).format('YYYY-MM-DD')
      const monthEnd = dayjs(monthStart).endOf('month').format('YYYY-MM-DD')
      if (monthEnd < rangeStart || monthStart > rangeEnd) return false
    } else {
      const rowDate = rawDate.slice(0, 10)
      if (!rowDate || rowDate < rangeStart || rowDate > rangeEnd) return false
    }
  }
  return true
}
function aggregateSalesRows(rows: CrmRecord[]): CrmRecord[] {
  const grouped = new Map<string, CrmRecord[]>()
  for (const row of rows) {
    const month = String(row.month ?? '')
    grouped.set(month, [...(grouped.get(month) ?? []), row])
  }
  return [...grouped.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([month, records]) => {
    const sum = (key: string) => records.reduce((totalValue, row) => totalValue + Number(row[key] ?? 0), 0)
    const conversionValues = records.map((row) => Number(row.conversion ?? 0)).filter((value) => Number.isFinite(value))
    return {
      ...records[0], id: `sales-month-${month}`, month,
      departmentId: canSelectDepartment.value && !departmentFilter.value ? '__all__' : records[0].departmentId,
      amount: sum('amount'), targetAmount: sum('targetAmount'), orders: sum('orders'), newCustomers: sum('newCustomers'),
      conversion: conversionValues.length ? Math.round(conversionValues.reduce((a, b) => a + b, 0) / conversionValues.length) : 0,
    } as CrmRecord
  })
}
// ---------------- 公海系统设置（管理员） ----------------
const poolConfig = ref<Record<string, string>>({})
const poolConfigLoaded = ref(false)
const poolConfigSaving = ref(false)
const poolRunning = ref(false)
const poolContactFull = ref(false)
const poolAutoEnabled = ref(true)
const poolAutoDays = ref(30)
const canConfigurePool = computed(() => currentModule.value.key === 'pool' && auth.can('system:config:edit'))
function applyPoolConfig(values: Record<string, string>): void {
  poolConfig.value = values
  poolContactFull.value = values['pool.contact.full'] === 'true'
  poolAutoEnabled.value = (values['pool.auto.recycle.enabled'] ?? 'true') !== 'false'
  poolAutoDays.value = Number(values['pool.auto.recycle.days'] ?? 30) || 30
}
async function loadPoolConfig(): Promise<void> {
  try {
    applyPoolConfig(await systemApi.configs())
    poolConfigLoaded.value = true
  } catch (error) { ElMessage.warning(error instanceof Error ? error.message : '系统设置读取失败') }
}
async function savePoolConfig(): Promise<void> {
  poolConfigSaving.value = true
  try {
    applyPoolConfig(await systemApi.saveConfigs({
      'pool.contact.full': String(poolContactFull.value),
      'pool.auto.recycle.enabled': String(poolAutoEnabled.value),
      'pool.auto.recycle.days': String(poolAutoDays.value),
    }))
    ElMessage.success('公海设置已保存')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { poolConfigSaving.value = false }
}
async function runAutoRecycle(): Promise<void> {
  try {
    await ElMessageBox.confirm(`将回收超过 ${poolAutoDays.value} 天未跟进的线索/客户到公海，是否继续？`, '立即回收', { type: 'warning', confirmButtonText: '开始回收', cancelButtonText: '取消' })
  } catch { return }
  poolRunning.value = true
  try {
    const result = await systemApi.autoRecycle(poolAutoDays.value)
    ElMessage.success(`已回收线索 ${result.leads} 条、客户 ${result.customers} 条`)
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '回收失败') }
  finally { poolRunning.value = false }
}

async function load(): Promise<void> {
  const request = loadIndicator.start()
  if (loadIndicator.isCurrent(request)) {
    loadFailed.value = false
    loadErrorMessage.value = ''
    pageRows.value = []
    allRows.value = []
    total.value = 0
    selectedRows.value = []
  }
  try {
    if (canConfigurePool.value && !poolConfigLoaded.value) void loadPoolConfig()
    const scopedFilters = { ...filters, ...(currentModule.value.key === 'leads' && leadView.value !== 'all' ? { view: leadView.value } : {}), ...(canSelectDepartment.value && departmentFilter.value ? { departmentId: departmentFilter.value } : {}) }
    const categoryScopedResources = ['products', 'customer-prices', 'quotes', 'orders', 'returns', 'shipments', 'reports-product-sales']
    if (hasCategoryRestriction.value && categoryScopedResources.includes(currentModule.value.key) && auth.can('product:product:list')) {
      productCatalog.value = await moduleApi.all('products', { filters: canSelectDepartment.value && departmentFilter.value ? { departmentId: departmentFilter.value } : {} })
    } else productCatalog.value = []
    if (!loadIndicator.isCurrent(request)) return
      const query: QueryState = { page: currentPage.value, pageSize: pageSize.value, search: search.value, filters: scopedFilters, sortBy: currentSort.value.prop, sortOrder: currentSort.value.order }
    const dateParams: Record<string, string> = { ...(isReport.value && reportRange.value.length === 2 ? { startDate: reportRange.value[0], endDate: reportRange.value[1] } : {}), ...(canSelectDepartment.value && departmentFilter.value ? { departmentId: departmentFilter.value } : {}) }
    if (isReport.value) {
      const all = await moduleApi.all(currentModule.value.key, { search: search.value, filters: scopedFilters }, dateParams)
      if (!loadIndicator.isCurrent(request)) return
      const filtered = all.filter(matchesCurrentFilters)
      const visible = filtered.filter(isRecordPermittedByCategory).filter(isRecordInCurrentUserScope).filter(matchesLeadView).filter(matchesPoolView).filter((row) => currentModule.value.key !== 'todos' || isTodoOwnedByCurrentUser(row))
      const reportRows = currentModule.value.key === 'reports-sales' ? aggregateSalesRows(visible) : visible
      allRows.value = sortRecords(reportRows)
      total.value = allRows.value.length
      pageRows.value = allRows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value)
    } else {
      const { page, all } = await moduleApi.listWithAll(currentModule.value.key, query)
      if (!loadIndicator.isCurrent(request)) return
      if (currentModule.value.key === 'product-categories') productCategoryRows.value = all
      const filtered = all.filter(matchesCurrentFilters)
      // The product-category access preview is client-only; paginate its already scoped result set locally.
      const visible = filtered.filter(isRecordPermittedByCategory).filter(isRecordInCurrentUserScope).filter(matchesLeadView).filter(matchesPoolView).filter((row) => currentModule.value.key !== 'todos' || isTodoOwnedByCurrentUser(row))
      allRows.value = visible
      const sortedVisible = sortRecords(visible)
      allRows.value = sortedVisible
      pageRows.value = localPagination.value ? sortedVisible.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value) : page.rows
      total.value = localPagination.value ? sortedVisible.length : page.total
    }
  } catch (error) {
    if (loadIndicator.isCurrent(request)) {
      loadFailed.value = true
      loadErrorMessage.value = error instanceof Error ? error.message : '列表暂时无法载入'
      pageRows.value = []
      allRows.value = []
      total.value = 0
      selectedRows.value = []
      ElMessage.error(loadErrorMessage.value)
    }
  }
  finally { loadIndicator.finish(request) }
}
function onReportRangeChange(value: unknown): void {
  reportRange.value = Array.isArray(value) ? value.map(String) : []
  currentPage.value = 1
  void load()
}

function resetFilters(): void {
  search.value = ''
  Object.keys(filters).forEach((key) => delete filters[key])
  if (canSelectDepartment.value) departmentFilter.value = ''
  currentPage.value = 1
  void load()
}
function onSearch(): void { currentPage.value = 1; void load() }
function onSelectionChange(rows: CrmRecord[]): void { selectedRows.value = rows }
function beginCreate(): void {
  formMode.value = 'create'
  detailRecord.value = null
  formModel.value = Object.fromEntries(currentModule.value.fields.map((field) => [field.key, defaultFieldValue(field)]))
  if (currentModule.value.key === 'service-tickets') formModel.value.status = '待处理'
  if (hasDepartmentScope.value) formModel.value.departmentId = selectedDepartmentId.value
  lineItems.value = []
  dialogOpen.value = true
  void loadLinkedOptions()
}
function defaultFieldValue(field: FormField): string | number | string[] {
  if (currentModule.value.key === 'customer-prices' && field.key === 'endDate') return ''
  if (currentModule.value.key === 'returns' && field.key === 'amount') return ''
  if (field.kind === 'date') return dayjs().format('YYYY-MM-DD')
  if (field.kind === 'number' || field.kind === 'money') return 0
  if (field.kind === 'lines') return ''
  if (field.kind === 'multiselect') return []
  if (field.kind === 'select') return ''
  if (field.key.toLowerCase().endsWith('no') || field.key.toLowerCase().includes('code')) return `${field.key.slice(0, 2).toUpperCase()}-${dayjs().format('YYMMDD')}-${Math.floor(100 + Math.random() * 900)}`
  if (field.key === 'owner' || field.key === 'assignee') return auth.user?.name ?? ''
  return ''
}
const lineProductOptions = computed(() => { const field = currentModule.value.fields.find((item) => item.kind === 'lines'); return field ? fieldOptions(field) : [] })
function lineSkuValue(line: CrmLineItem): string {
  if (line.sku) return String(line.sku)
  const matched = productCatalog.value.find((item) => String(item.name ?? item.productName ?? '') === String(line.product ?? '') && item.sku)
  return String(matched?.sku ?? line.product ?? '')
}
function applyLineProduct(line: CrmLineItem, sku: string): void {
  const row = productCatalog.value.find((item) => String(item.sku ?? '') === sku)
  if (row) {
    line.product = String(row.name ?? row.productName ?? '')
    line.sku = String(row.sku)
    line.spec = row.spec ? String(row.spec) : undefined
    line.unit = row.unit ? String(row.unit) : undefined
    line.unitPrice = Number(row.price ?? 0)
    return
  }
  line.product = sku
  line.sku = sku
  line.unitPrice = linkedProductPrices.value[sku] ?? line.unitPrice
}
function addProductLine(): void {
  const sku = lineProductOptions.value[0]?.value
  if (!sku) { ElMessage.warning('产品规格选项尚未加载完成，请稍候再试'); return }
  const line: CrmLineItem = { product: '', quantity: 1, unitPrice: 0, discount: 100 }
  applyLineProduct(line, sku)
  lineItems.value.push(line)
}
function setLineProduct(line: CrmLineItem, sku: string): void { applyLineProduct(line, sku) }
function asLineItems(value: CrmValue | undefined): CrmLineItem[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is CrmLineItem => Boolean(item && typeof item === 'object' && 'product' in item))
}
function formatLineItems(value: CrmValue): string {
  const items = asLineItems(value)
  const label = (item: CrmLineItem): string => `${item.product}${item.spec ? ` ${item.spec}` : ''}`
  return items.map((item) => canViewSensitivePrices.value ? `${label(item)} × ${item.quantity}（${formatMoney(item.unitPrice * item.quantity * Number(item.discount ?? 100) / 100)}）` : `${label(item)} × ${item.quantity}`).join('；') || '—'
}
function dateFieldValue(key: string): string | number {
  const value = formModel.value[key]
  return typeof value === 'string' || typeof value === 'number' ? value : ''
}
function setDateFieldValue(key: string, value: unknown): void {
  if (typeof value === 'string' || typeof value === 'number') formModel.value[key] = value
  else if (value instanceof Date) formModel.value[key] = dayjs(value).format('YYYY-MM-DD')
  else formModel.value[key] = ''
}
function beginEdit(row: CrmRecord): void {
  formMode.value = 'edit'
  formModel.value = Object.fromEntries(currentModule.value.fields.map((field) => [field.key, field.kind === 'lines' ? '' : row[field.key] as string | number ?? '']))
  // 客户价格按 SKU 维护：下拉选项值是 SKU 编码，编辑时用编码回显（列表仍展示产品名+规格）
  if (currentModule.value.key === 'customer-prices') formModel.value.product = String(row.sku ?? row.product ?? '')
  if (hasDepartmentScope.value) formModel.value.departmentId = String(row.departmentId ?? row.deptId ?? '')
  lineItems.value = asLineItems(row.items).map((item) => ({ product: item.product, quantity: Number(item.quantity), unitPrice: Number(item.unitPrice), sku: item.sku, spec: item.spec, unit: item.unit, discount: Number(item.discount ?? 100) }))
  dialogOpen.value = true
  void loadLinkedOptions()
}
function openDetail(row: CrmRecord): void { detailRecord.value = row; drawerOpen.value = true }
async function syncOrderDerivedStatus(moduleKey: string, input: Partial<CrmRecord>): Promise<void> {
  const orderNo = String(input.orderNo ?? '')
  if (!orderNo || !auth.can('sales:order:edit')) return
  const orders = await moduleApi.all('orders').catch(() => [] as CrmRecord[])
  const order = orders.find((row) => String(row.orderNo ?? row.id) === orderNo)
  if (!order) return
  if (moduleKey === 'shipments') {
    const shipments = (await moduleApi.all('shipments').catch(() => [] as CrmRecord[])).filter((row) => String(row.orderNo ?? '') === orderNo)
    const statuses = shipments.map((row) => String(row.status ?? ''))
    const deliveryStatus = statuses.length && statuses.every((status) => status === '已签收') ? '已签收' : statuses.some((status) => ['运输中', '部分发货', '全部发货'].includes(status)) ? (statuses.some((status) => status === '部分发货') ? '部分发货' : '运输中') : statuses.some((status) => status === '发货异常') ? '发货异常' : '未发货'
    await moduleApi.update('orders', order.id, { deliveryStatus })
  }
  if (moduleKey === 'payments') {
    const payments = (await moduleApi.all('payments').catch(() => [] as CrmRecord[])).filter((row) => String(row.orderNo ?? '') === orderNo)
    const received = payments.reduce((sum, row) => sum + Number(row.amount ?? 0), 0)
    const amount = Number(order.amount ?? 0)
    const paymentStatus = received >= amount && amount > 0 ? '已结清' : received > 0 ? '部分回款' : '未回款'
    await moduleApi.update('orders', order.id, { received, paymentStatus })
    const receivables = await moduleApi.all('receivables').catch(() => [] as CrmRecord[])
    const receivable = receivables.find((row) => String(row.orderNo ?? '') === orderNo)
    if (receivable && auth.can('delivery:receivable:edit')) await moduleApi.update('receivables', receivable.id, { received, outstanding: Math.max(0, amount - received), status: paymentStatus === '已结清' ? '已结清' : paymentStatus === '部分回款' ? '部分回款' : String(receivable.dueDate ?? '') < dayjs().format('YYYY-MM-DD') ? '逾期' : '未到期', agingDays: String(receivable.dueDate ?? '') < dayjs().format('YYYY-MM-DD') ? dayjs().diff(dayjs(String(receivable.dueDate)), 'day') : 0 })
  }
}
async function saveForm(): Promise<void> {
  const moduleKeyAtStart = currentModule.value.key
  if (isReport.value) { ElMessage.warning('报表为只读资源，不能保存记录'); dialogOpen.value = false; return }
  const hiddenRequiredMoney = !canViewSensitivePrices.value && currentModule.value.fields.some((field) => field.required && (field.kind === 'money' || isSensitiveMoneyFieldKey(field.key)))
  if (formMode.value === 'create' && hiddenRequiredMoney) { ElMessage.warning('当前账号无法查看必填金额字段，不能安全创建该记录；请联系有相应数据权限的管理员。'); return }
  if (formMode.value === 'edit' && hasLineItems.value && !canViewSensitivePrices.value) { ElMessage.warning('当前账号无法查看产品单价，不能安全编辑产品明细。'); return }
  if (hasDepartmentScope.value && !String(formModel.value.departmentId ?? '').trim()) { ElMessage.warning('请先选择记录所属部门'); return }
  if (currentModule.value.key === 'product-categories' && String(formModel.value.parent ?? '') !== '—' && excludedCategoryNames.value.has(String(formModel.value.parent ?? ''))) { ElMessage.warning('分类上级不能选择自身或其下级'); return }
  if (currentModule.value.key === 'product-categories' && allRows.value.some((row) => String(row.name ?? '').trim() === String(formModel.value.name ?? '').trim() && (formMode.value === 'create' || String(row.id) !== String(detailRecord.value?.id)))) { ElMessage.warning('该分类名称已存在，请使用唯一名称'); return }
  if (currentModule.value.key === 'customer-prices' && formModel.value.endDate && String(formModel.value.endDate) < String(formModel.value.startDate ?? '')) { ElMessage.warning('失效日期不能早于生效日期'); return }
    if (['quotes', 'orders'].includes(currentModule.value.key) && !String(formModel.value.customer ?? '').trim()) { ElMessage.warning('报价单和订单必须关联客户'); return }
    if (['quotes', 'orders'].includes(currentModule.value.key) && !String(formModel.value.owner ?? '').trim()) { ElMessage.warning('报价单和订单必须指定负责人'); return }
    if (currentModule.value.key === 'opportunities' && (Number(formModel.value.probability ?? 0) < 0 || Number(formModel.value.probability ?? 0) > 100)) { ElMessage.warning('成交概率必须在 0–100 之间'); return }
    if (currentModule.value.key === 'payments' && Number(formModel.value.amount ?? 0) <= 0) { ElMessage.warning('到账金额必须大于 0'); return }
    if (currentModule.value.key === 'receivables' && Number(formModel.value.received ?? 0) > Number(formModel.value.amount ?? 0)) { ElMessage.warning('已收金额不能大于应收金额'); return }
    if (currentModule.value.key === 'service-tickets' && formModel.value.createdAt && formModel.value.dueDate && String(formModel.value.dueDate) < String(formModel.value.createdAt)) { ElMessage.warning('处理时限不能早于创建时间'); return }
    if (currentModule.value.key === 'receivables') {
      const amount = Number(formModel.value.amount ?? 0)
      const received = Number(formModel.value.received ?? 0)
      const dueDate = String(formModel.value.dueDate ?? '')
      formModel.value.outstanding = Math.max(0, amount - received)
      formModel.value.agingDays = dueDate && dueDate < dayjs().format('YYYY-MM-DD') && amount > received ? dayjs().diff(dayjs(dueDate), 'day') : 0
      formModel.value.status = received >= amount && amount > 0 ? '已结清' : received > 0 ? '部分回款' : dueDate && dueDate < dayjs().format('YYYY-MM-DD') ? '逾期' : '未到期'
    }
    if (hasLineItems.value) {
    if (!lineItems.value.length) { ElMessage.warning('请至少添加一条产品明细'); return }
    if (lineItems.value.some((item) => !item.product || Number(item.quantity) < 1 || Number(item.unitPrice) < 0 || Number(item.discount ?? 100) < 0 || Number(item.discount ?? 100) > 100)) { ElMessage.warning('请检查产品、数量、单价和折扣（0–100）'); return }
    formModel.value.items = lineItems.value.length ? '已添加产品明细' : ''
    formModel.value.amount = lineItemsTotal.value + Number(formModel.value.tax ?? 0) + Number(formModel.value.freight ?? 0)
    }
    try { await formRef.value?.validate() } catch { return }
    if (currentModule.value.key === 'leads' && !String(formModel.value.source ?? '').trim()) { ElMessage.warning('新线索必须填写来源'); return }
    if (['leads', 'customers', 'pool'].includes(currentModule.value.key) && currentModule.value.key !== 'pool' && !['待分配', '公海'].includes(String(formModel.value.stage ?? formModel.value.status ?? '')) && !String(formModel.value.owner ?? '').trim()) { ElMessage.warning('业务记录必须指定负责人，或明确进入待分配/公海状态'); return }
    if (currentModule.value.key === 'leads' && formMode.value === 'create' && auth.can('crm:lead:list')) {
      const phone = String(formModel.value.phone ?? '').replace(/\s+/g, '')
      const storeName = String(formModel.value.storeName ?? '').trim()
      try {
        const dupRes = await crmBusinessApi.checkLeadDuplicate({ phone, name: storeName })
        if (dupRes.duplicate) {
          const dupLeads = dupRes.leads ?? []
          const dupCustomers = dupRes.customers ?? []
          const detail = [
            dupLeads.length ? `线索库重复: ${dupLeads.map((l) => (l.storeName || '未命名') + ' (' + l.phone + ')').join('; ')}` : '',
            dupCustomers.length ? `客户档案重复: ${dupCustomers.map((c) => (c.name || '未命名') + ' (' + c.phone + ')').join('; ')}` : '',
          ].filter(Boolean).join('\n')
          try {
            await ElMessageBox.confirm(
              `服务端查重检测到潜在重复记录：\n${detail}\n\n是否确认强制新增该线索？`,
              '线索查重提醒',
              { type: 'warning', confirmButtonText: '确认强制新增', cancelButtonText: '返回修改' }
            )
            formModel.value.ignoreDuplicate = true
          } catch {
            return
          }
        }
      } catch {
        // network or server error fallback
      }
    }
    if (currentModule.value.key === 'customers' && formMode.value === 'create' && auth.can('crm:customer:list')) {
      const phone = String(formModel.value.phone ?? '').replace(/\s+/g, '')
      const name = String(formModel.value.name ?? '').trim().toLowerCase()
      const address = String(formModel.value.address ?? '').trim().toLowerCase()
      const existingCustomers = await moduleApi.all('customers').catch(() => [] as CrmRecord[])
      const duplicated = existingCustomers.some((row) => (phone && String(row.phone ?? '').replace(/\s+/g, '') === phone) || (name && String(row.name ?? '').trim().toLowerCase() === name && address && String(row.address ?? '').trim().toLowerCase() === address))
      if (duplicated) {
        try { await ElMessageBox.confirm('现有客户档案中已出现相同联系电话或相同客户名称与地址。仍要继续新增吗？', '发现重复客户', { type: 'warning', confirmButtonText: '仍然新增', cancelButtonText: '返回检查' }) }
        catch { return }
      }
    }
  if (isReport.value || currentModule.value.key !== moduleKeyAtStart || !dialogOpen.value) { ElMessage.warning('页面已切换，已取消此次保存'); return }
  saving.value = true
  try {
    const input = { ...formModel.value, ...(hasLineItems.value ? { items: structuredClone(lineItems.value), amount: lineItemsTotal.value + Number(formModel.value.tax ?? 0) + Number(formModel.value.freight ?? 0) } : {}) } as Partial<CrmRecord>
    // 客户价格的下拉值已是 SKU 编码，额外带上 sku 字段，后端按 SKU 落库
    if (currentModule.value.key === 'customer-prices' && typeof input.product === 'string' && productCatalog.value.some((row) => String(row.sku ?? '') === input.product)) input.sku = input.product
    if (!canViewSensitivePrices.value) for (const field of currentModule.value.fields) if (field.kind === 'money' || isSensitiveMoneyFieldKey(field.key)) delete input[field.key]
    if (formMode.value === 'create') await moduleApi.create(currentModule.value.key, input)
    else if (detailRecord.value) await moduleApi.update(currentModule.value.key, detailRecord.value.id, input)
    await syncOrderDerivedStatus(currentModule.value.key, input)
    ElMessage.success(formMode.value === 'create' ? '已创建记录' : '已保存修改')
    dialogOpen.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function removeRows(ids: string[]): Promise<void> {
  if (!ids.length) return
  if (isReport.value) { ElMessage.error('报表为只读资源，不能删除'); return }
  if (!canRemove.value) { ElMessage.error('当前账号没有删除权限'); return }
  if (currentModule.value.key === 'orders') {
    const locked = allRows.value.find((row) => ids.includes(String(row.id)) && ['已确认', '已完成', '已关闭'].includes(String(row.status ?? '')))
    if (locked) { ElMessage.warning('已确认、已完成或已关闭的订单不能直接删除，请通过关闭或作废流程处理'); return }
  }
  if (currentModule.value.key === 'product-categories') {
    const targets = allRows.value.filter((row) => ids.includes(String(row.id)))
    const names = new Set(targets.map((row) => String(row.name ?? '')))
    let categories: CrmRecord[]
    let products: CrmRecord[]
    try {
      const [categoryRows, productRows] = await Promise.all([moduleApi.all('product-categories'), moduleApi.all('products')])
      categories = categoryRows.filter(isRecordInCurrentUserScope).filter(isRecordPermittedByCategory)
      products = productRows.filter(isRecordInCurrentUserScope).filter((row) => isCategoryNamePermitted(row.category ?? row.categoryName))
    }
    catch (error) { ElMessage.error(error instanceof Error ? `无法核实分类引用，已阻止删除：${error.message}` : '无法核实分类引用，已阻止删除'); return }
    const child = categories.find((row) => names.has(String(row.parent ?? '')) && !ids.includes(String(row.id)))
    const product = products.find((row) => names.has(String(row.category ?? row.categoryName ?? '')))
    if (child || product) { ElMessage.warning(`无法删除：${child ? `分类“${String(child.parent)}”仍有子分类` : `分类“${String(product?.category ?? product?.categoryName)}”仍被产品引用`}`); return }
  }
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条记录？删除后无法恢复。`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await moduleApi.remove(currentModule.value.key, ids)
  ElMessage.success(`已删除 ${ids.length} 条记录`)
  selectedRows.value = []
  await load()
}
async function setStatus(row: CrmRecord, status: string, message: string): Promise<void> {
  if (isReport.value) { ElMessage.error('报表为只读资源，不能修改状态'); return }
  if (!canEdit.value) { ElMessage.error('当前账号没有编辑权限'); return }
  await moduleApi.setStatus(currentModule.value.key, [row.id], status)
  if (currentModule.value.key === 'orders' && status === '已完成') {
    const orderNo = String(row.orderNo ?? row.id)
    const customerName = String(row.customer ?? '')
    const orders = await moduleApi.all('orders').catch(() => [] as CrmRecord[])
    const customerOrders = orders.filter((item) => String(item.customer ?? '') === customerName && ['已完成', '已确认'].includes(String(item.status ?? '')))
    if (auth.can('crm:customer:edit') && customerName) {
      const customers = await moduleApi.all('customers').catch(() => [] as CrmRecord[])
      const customer = customers.find((item) => String(item.name ?? '') === customerName)
      if (customer) await moduleApi.update('customers', customer.id, { annualValue: customerOrders.reduce((sum, item) => sum + Number(item.amount ?? 0), 0), purchaseCount: customerOrders.length, lastOrderDate: dayjs().format('YYYY-MM-DD'), lastContact: dayjs().format('YYYY-MM-DD') })
    }
    if (auth.can('work:todo:add')) {
      const todos = await moduleApi.all('todos').catch(() => [] as CrmRecord[])
      const related = todos.filter((item) => String(item.relatedId ?? '') === String(row.id) || String(item.orderNo ?? '') === orderNo)
      const taskBase = { owner: String(row.owner ?? auth.user?.name ?? ''), customer: customerName, orderNo, status: '待处理', priority: '中' }
      if (!related.some((item) => String(item.taskType ?? '') === '客户回访')) await moduleApi.create('todos', { ...taskBase, title: `订单回访：${customerName || '客户'}`, related: `订单 ${orderNo} 已完成，安排满意度回访`, dueDate: dayjs().add(7, 'day').format('YYYY-MM-DD'), taskType: '客户回访', relatedType: '订单', relatedId: String(row.id) })
      if (!related.some((item) => String(item.taskType ?? '') === '补货提醒')) await moduleApi.create('todos', { ...taskBase, title: `补货跟进：${customerName || '客户'}`, related: `根据订单 ${orderNo} 确认下一次采购周期`, dueDate: dayjs().add(30, 'day').format('YYYY-MM-DD'), taskType: '补货提醒', relatedType: '订单', relatedId: String(row.id) })
    }
  }
  ElMessage.success(message)
  await load()
}
async function claimCustomer(row: CrmRecord): Promise<void> {
  if (!canClaimCustomer(row)) { ElMessage.warning('仅可认领未认领的公海客户'); return }
  if (!canEdit.value) { ElMessage.error('当前账号没有认领/分配权限'); return }
  try {
    await moduleApi.claimPool(row.id)
    ElMessage.success(`已领取${String(row.name ?? '客户')}`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '领取失败，请刷新后重试')
  }
}
function canClaimCustomer(row: CrmRecord): boolean {
  const owner = [row.owner, row.ownerId, row.assignee, row.assigneeId].some((value) => value !== undefined && value !== null && String(value).trim() !== '')
  return currentModule.value.key === 'pool' && !owner && ['待认领', '待分配', '公海', ''].includes(String(row.status ?? ''))
}
function canClaimLead(row: CrmRecord): boolean {
  if (currentModule.value.key !== 'leads') return false
  return [row.owner, row.ownerId].every((value) => value === undefined || value === null || String(value).trim() === '')
}
async function claimLeadRow(row: CrmRecord): Promise<void> {
  if (!auth.can('crm:lead:edit')) { ElMessage.error('当前账号没有领取线索权限'); return }
  try {
    await moduleApi.claimLead(row.id)
    ElMessage.success(`已领取线索「${String(row.name ?? row.company ?? row.id)}」`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '领取失败，请刷新后重试')
  }
}
async function recycleLeadRow(row: CrmRecord): Promise<void> {
  if (!auth.can('crm:pool:assign')) { ElMessage.error('当前账号没有回收线索权限'); return }
  try {
    await ElMessageBox.confirm(`将「${String(row.name ?? row.company ?? row.id)}」回收到线索公海？回收后负责人将被清空。`, '回收线索', { type: 'warning', confirmButtonText: '确认回收', cancelButtonText: '取消' })
  } catch { return }
  try {
    await moduleApi.recycleLead(row.id, '长期未跟进，回收到线索公海')
    ElMessage.success('线索已回收到公海')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回收失败')
  }
}
async function convertLead(row: CrmRecord): Promise<void> {
  if (!canEdit.value || !auth.can('crm:customer:add')) { ElMessage.error('当前账号没有线索转化所需权限'); return }
  await ElMessageBox.confirm(`将「${String(row.company ?? row.name)}」转为正式客户档案？`, '线索转化', { type: 'info', confirmButtonText: '转为客户', cancelButtonText: '稍后处理' })
  const customerName = String(row.company ?? row.name ?? '')
  const existing = (await moduleApi.all('customers')).find((item) => String(item.name ?? '').trim().toLowerCase() === customerName.trim().toLowerCase())
  let customerId = existing?.id
  if (existing) {
    await moduleApi.update('leads', row.id, { customer: customerName, customerId: existing.id, stage: '转化成功', status: '已完成' })
    ElMessage.warning('同名客户档案已存在，已关联现有客户并更新线索状态')
  } else {
    const customer = { name: customerName, customerType: row.customerType ?? '汽修厂', industry: row.industry ?? '汽车后市场服务', purchaseCategories: row.purchaseCategories, storeName: row.storeName, level: '普通客户', healthStatus: '正常', owner: row.owner ?? auth.user?.name, source: row.source, phone: row.phone, status: '进行中', lastContact: dayjs().format('YYYY-MM-DD'), annualValue: 0 }
    const created = await moduleApi.create('customers', customer)
    customerId = created.id
    await moduleApi.update('leads', row.id, { customer: customerName, customerId: created.id, stage: '转化成功', status: '已完成' })
    ElMessage.success('已创建客户档案并更新线索状态')
  }
  if (customerId && auth.can('crm:contact:add') && String(row.contact ?? '').trim()) {
    const contacts = await moduleApi.all('contacts').catch(() => [] as CrmRecord[])
    const existsContact = contacts.some((contact) => String(contact.customer ?? '') === customerName && String(contact.name ?? '') === String(row.contact ?? ''))
    if (!existsContact) await moduleApi.create('contacts', { customer: customerName, customerId, name: String(row.contact), phone: String(row.phone ?? ''), storeName: String(row.storeName ?? ''), role: '采购负责人', isPrimary: '是', departmentId: row.departmentId })
  }
  if (auth.can('work:todo:add') && String(row.nextDate ?? '').trim()) await moduleApi.create('todos', { title: `首次跟进：${customerName}`, related: `线索转化后承接原始来源：${String(row.source ?? '未知来源')}`, relatedId: String(row.id), relatedType: '线索', customer: customerName, owner: String(row.owner ?? auth.user?.name ?? ''), dueDate: String(row.nextDate), priority: '中', status: '待处理', departmentId: row.departmentId })
  await load()
}
async function advanceOpportunity(row: CrmRecord): Promise<void> {
  if (!canEdit.value) { ElMessage.error('当前账号没有编辑商机的权限'); return }
  const stages = ['需求发现', '产品推荐', '试用/样品', '报价', '议价', '待确认', '赢单']
  const index = stages.indexOf(String(row.stage))
  const stage = stages[Math.min(stages.length - 1, Math.max(0, index + 1))]
  await moduleApi.update('opportunities', row.id, { stage, probability: Math.min(100, Number(row.probability ?? 30) + 18), status: stage === '赢单' ? '已完成' : '进行中' })
  ElMessage.success(`商机已推进至${stage}`)
  await load()
}
function quoteStatus(row: CrmRecord): string { return quoteApprovalDrafts.value[row.id] ?? String(row.status ?? '—') }
async function decideQuote(row: CrmRecord, approved: boolean): Promise<void> {
  if (!auth.can('sales:quote:edit')) { ElMessage.error('当前账号没有报价审批权限'); return }
  const decision = approved ? '已通过' : '已拒绝'
  let rejectReason = ''
  if (!approved) {
    try {
      const { value } = await ElMessageBox.prompt('请输入驳回原因：', '报价驳回', {
        confirmButtonText: '确定驳回',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '驳回原因不能为空',
      })
      rejectReason = value
    } catch {
      return
    }
  } else {
    await ElMessageBox.confirm(`将报价「${String(row.quoteNo ?? row.name ?? row.id)}」标记为审核通过？`, '报价审核', { type: 'success', confirmButtonText: '确认通过', cancelButtonText: '取消' })
  }

  try {
    if (approved) {
      await crmBusinessApi.approveQuote(row.id)
    } else {
      await crmBusinessApi.rejectQuote(row.id, rejectReason)
    }
    quoteApprovalDrafts.value = { ...quoteApprovalDrafts.value, [row.id]: decision }
    ElMessage.success(`报价已${decision}`)
    await load()
  } catch (err: any) {
    ElMessage.error(err.message || '报价审核处理失败')
  }
}
async function convertQuoteToOrder(row: CrmRecord): Promise<void> {
  if (!auth.can('sales:order:add')) { ElMessage.error('当前账号没有新增销售订单权限'); return }
  const status = quoteStatus(row)
  if (!['已通过', '已完成', '已审核', '已接受'].includes(status)) { ElMessage.warning('请先完成报价审核或确认报价状态为已通过'); return }
  const quoteNo = String(row.quoteNo ?? row.quoteCode ?? row.id)
  await ElMessageBox.confirm(`将已审核报价「${quoteNo}」转为销售订单？该操作将调用服务端单原子事务生成订单及履约回款任务。`, '报价转订单', { type: 'info', confirmButtonText: '确认转单', cancelButtonText: '取消' })
  try {
    const res = await crmBusinessApi.convertQuoteToOrder(row.id)
    ElMessage.success(res.message || `销售订单 ${res.orderNo || ''} 创建成功，已级联生成履约回访与补货待办！`)
    await router.push('/orders')
  } catch (err: any) {
    ElMessage.error(err.message || '转单失败')
  }
}
async function rowAction(action: string, row: CrmRecord): Promise<void> {
  try {
    if (isReport.value && action !== 'detail') { ElMessage.error('报表为只读资源'); return }
    if (action === 'remove' && !canRemove.value) { ElMessage.error('当前账号没有删除权限'); return }
    if (['edit', 'finish', 'claim', 'convert', 'advance', 'fulfill', 'service', 'approve-quote', 'reject-quote'].includes(action) && !canEdit.value) { ElMessage.error('当前账号没有编辑权限'); return }
    if (action === 'detail') openDetail(row)
    if (action === 'edit') { detailRecord.value = row; beginEdit(row) }
    if (action === 'remove') await removeRows([row.id])
    if (action === 'finish') await setStatus(row, '已完成', '待办已完成')
    if (action === 'claim') await claimCustomer(row)
    if (action === 'claim-lead') await claimLeadRow(row)
    if (action === 'recycle-lead') await recycleLeadRow(row)
    if (action === 'convert') await convertLead(row)
    if (action === 'advance') await advanceOpportunity(row)
    if (action === 'approve-quote') await decideQuote(row, true)
    if (action === 'reject-quote') await decideQuote(row, false)
    if (action === 'convert-quote') await convertQuoteToOrder(row)
    if (action === 'fulfill') {
      await moduleApi.update(currentModule.value.key, row.id, { deliveryStatus: '已完成' })
      ElMessage.success('履约状态已完成；订单状态保持不变')
      await load()
    }
    if (action === 'service') await setStatus(row, '进行中', '工单已开始处理')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : String(error))
  }
}
async function batchChange(status: string): Promise<void> {
  if (isReport.value) { ElMessage.error('报表为只读资源，不能批量修改'); return }
  if (!canBatchChangeStatus.value || !['已完成', '进行中', '已暂停'].includes(status)) { ElMessage.error('当前模块未配置可安全使用的通用批量状态动作'); return }
  if (!canEdit.value) { ElMessage.error('当前账号没有编辑权限'); return }
  if (!selectedRows.value.length) { ElMessage.warning('请先选择记录'); return }
  try {
    await moduleApi.setStatus(currentModule.value.key, selectedRows.value.map((row) => row.id), status)
    ElMessage.success(`已更新 ${selectedRows.value.length} 条记录`)
    selectedRows.value = []
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '批量更新失败') }
}
async function exportCsv(): Promise<void> {
  if (!canExport.value) { ElMessage.error('当前账号没有此数据的导出权限'); return }
  try {
    const params: Record<string, string> = {}
    if (search.value.trim()) params.keyword = search.value.trim()
    for (const [fieldKey, value] of Object.entries(filters.value)) if (value) params[fieldKey] = String(value)
    if (canSelectDepartment.value && departmentFilter.value) params.departmentId = String(departmentFilter.value)
    if (currentModule.value.key === 'leads' && leadView.value !== 'all') params.view = leadView.value
    if (reportRange.value && reportRange.value.length === 2) { params.startDate = reportRange.value[0]; params.endDate = reportRange.value[1] }
    // 服务端导出：后端按数据范围/品类过滤、字段脱敏并写导出审计
    const blob = await crmBusinessApi.exportModule(currentModule.value.key, params)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${currentModule.value.title}-${dayjs().format('YYYYMMDD')}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
    ElMessage.success(isReport.value ? '报表已导出为 CSV' : '数据已导出为 CSV')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
}
function displayValue(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—'
  if (Array.isArray(value)) return value.map(displayValue).filter((item) => item !== '—').join('、') || '—'
  if (typeof value === 'object') return JSON.stringify(value) || '—'
  return String(value)
}
function maskPublicContact(value: unknown, key: string): unknown {
  if (currentModule.value.key !== 'pool' || !['phone', 'mobile', 'wechat', 'email'].includes(key)) return value
  const text = String(value ?? '')
  if (!text) return value
  if (key === 'email') return text.replace(/^(.{1,2}).*(@.*)$/, '$1***$2')
  return text.length >= 7 ? `${text.slice(0, 3)}****${text.slice(-4)}` : '***'
}
function formatDetailField(field: FormField, value: CrmValue): string {
  if (field.kind === 'lines') return formatLineItems(value)
  value = maskPublicContact(value, field.key) as CrmValue
  const sensitive = field.kind === 'money' || isSensitiveMoneyFieldKey(field.key)
  if (!canViewSensitivePrices.value && sensitive) return value === undefined || value === null || value === '' ? '—' : '••••'
  const safeValue = canViewSensitivePrices.value ? value : redactSensitiveMoneyFields(value)
  if (field.kind === 'money') return formatMoney(safeValue)
  if (field.kind === 'date') return formatDate(safeValue)
  return displayValue(safeValue)
}
function formatCell(row: CrmRecord, column: TableColumn): string {
  const value = maskPublicContact(row[column.key], column.key)
  const sensitive = column.type === 'money' || isSensitiveMoneyFieldKey(column.key)
  if (!canViewSensitivePrices.value && sensitive) return value === undefined || value === null || value === '' ? '—' : '••••'
  const safeValue = canViewSensitivePrices.value ? value : redactSensitiveMoneyFields(value)
  if (column.type === 'money') return formatMoney(safeValue)
  if (column.type === 'date') return formatDate(safeValue)
  return displayValue(safeValue)
}
function statusType(value: unknown): 'success' | 'warning' | 'danger' | 'info' {
  const text = String(value ?? '')
  if (['已完成', '已结清', '赢单', '启用', '在职', '已签收', '非常满意', '已解决'].includes(text)) return 'success'
  if (['待处理', '待认领', '待分配', '部分回款', '一般', '进行中', '已联系', '方案评估'].includes(text)) return 'warning'
  if (['逾期', '紧急', '已暂停', '已停用', '输单', '待改善'].includes(text)) return 'danger'
  return 'info'
}
async function handleSort(sort: { prop?: string; order?: 'ascending' | 'descending' | null }): Promise<void> {
  if (!sort.prop || !sort.order) {
    currentSort.value = {}
    void load()
    return
  }
  currentSort.value = { prop: sort.prop, order: sort.order }
  selectedRows.value = []
  if (isReport.value || localPagination.value) {
    allRows.value = sortRecords(allRows.value)
    pageRows.value = allRows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value)
    return
  }
  await load()
}
function reportOption(): EChartsOption {
  const labels = allRows.value.map((row) => String(row.month ?? row.customer ?? row.product ?? row.name ?? ''))
  const values = allRows.value.map((row) => Number(row.amount ?? 0))
  return {
    color: ['#528a70', '#c3a065'],
    grid: { left: 12, right: 18, top: 20, bottom: 12, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => formatMoney(value) },
    xAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: '#e5ece5' } }, axisTick: { show: false }, axisLabel: { color: '#849187', rotate: labels.length > 5 ? 16 : 0 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf2ed', type: 'dashed' } }, axisLabel: { color: '#9aa59b' } },
    series: [{ type: 'bar', data: values, barMaxWidth: 38, itemStyle: { borderRadius: [6, 6, 0, 0], color: '#77a38a' } }],
  } as EChartsOption
}

watch(() => route.meta.moduleKey, () => {
  dialogOpen.value = false
  drawerOpen.value = false
  detailRecord.value = null
  formModel.value = {}
  lineItems.value = []
  selectedRows.value = []
  currentPage.value = 1
  currentSort.value = {}
  search.value = ''
  reportRange.value = []
  selectedCategoryName.value = ''
  leadView.value = 'all'
  Object.keys(filters).forEach((key) => delete filters[key])
  void loadLinkedOptions()
  void load()
}, { immediate: true })
watch(() => auth.can('system:data:all'), (canViewAll) => {
  if (!canViewAll) departmentFilter.value = ''
  currentPage.value = 1
  void loadDepartments().then(() => { void loadLinkedOptions(); void load() })
})
watch(() => route.query.create, (value) => {
  if (value === '1' && canAdd.value) {
    beginCreate()
    void router.replace({ path: route.path })
  }
})
onMounted(() => {
  unsubscribeFrontendPolicy = subscribeFrontendPolicyChange(() => {
    frontendPolicyRevision.value += 1
    currentPage.value = 1
    selectedRows.value = []
    void loadDepartments().then(() => { void loadLinkedOptions(); void load() })
  })
  if (auth.can('system:data:all') || (frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本部门及下级' && auth.can('system:dept:list'))) {
    void loadDepartments().then(() => { void loadLinkedOptions(); void load() })
  }
  if (route.query.create === '1' && canAdd.value) beginCreate()
})
onBeforeUnmount(() => { unsubscribeFrontendPolicy?.() })
</script>

<template>
  <div class="module-page">
    <PageHeading :title="currentModule.title" :eyebrow="currentModule.eyebrow" :description="currentModule.description">
      <template #actions>
        <el-button v-if="canExport" class="button-secondary" :icon="Download" @click="exportCsv">{{ isReport ? currentModule.primaryAction : '导出数据' }}</el-button>
        <el-button v-if="canAdd" type="primary" :icon="Plus" @click="beginCreate">{{ titleAction }}</el-button>
      </template>
    </PageHeading>

    <el-alert v-if="productScopeNotice || ownerScopeNotice" class="scope-preview-alert" :title="[productScopeNotice, ownerScopeNotice].filter(Boolean).join('；')" type="warning" :closable="false" show-icon />
    <el-alert v-if="loadFailed" class="scope-preview-alert" :title="`${currentModule.title}加载失败`" :description="loadErrorMessage" type="error" :closable="false" show-icon><template #default><el-button text type="primary" @click="load">重试</el-button></template></el-alert>

    <section v-if="canConfigurePool" class="panel pool-config-bar">
      <div class="pool-config-title"><div class="panel-kicker">POOL SETTINGS</div><strong>公海设置</strong></div>
      <label class="pool-config-item">完整联系方式<el-switch v-model="poolContactFull" /></label>
      <label class="pool-config-item">自动回收<el-switch v-model="poolAutoEnabled" /></label>
      <label class="pool-config-item">超过<el-input-number v-model="poolAutoDays" :min="1" :max="365" :controls="false" class="pool-days-input" />天未跟进</label>
      <div class="pool-config-actions"><el-button class="button-secondary" :loading="poolConfigSaving" @click="savePoolConfig">保存设置</el-button><el-button type="primary" :loading="poolRunning" @click="runAutoRecycle">立即回收</el-button></div>
    </section>

    <section v-if="currentModule.key === 'product-categories'" class="panel category-tree-panel">
      <div class="category-tree-heading"><div><div class="panel-kicker">CATEGORY NAVIGATION</div><h2>产品分类树</h2><p>点击分类查看其子级分类记录；数据来自当前可见的分类资源。</p></div><div class="category-tree-heading-actions"><el-button v-if="selectedCategoryName" text type="primary" @click="selectCategoryNode({ id: '', name: selectedCategoryName })">清除分类筛选</el-button><el-button text type="primary" @click="categoryTreeOpen = !categoryTreeOpen">{{ categoryTreeOpen ? '收起分类' : '展开分类' }}</el-button></div></div>
      <template v-if="categoryTreeOpen">
        <div v-if="categoryTreeData.length" class="category-tree-wrap"><el-tree :data="categoryTreeData" node-key="id" :props="{ label: 'name', children: 'children' }" default-expand-all highlight-current :current-node-key="categoryTreeData.find((node) => node.name === selectedCategoryName)?.id" @node-click="selectCategoryNode"><template #default="{ data }"><span class="category-tree-node"><span>{{ data.name }}</span><small>{{ allRows.filter((row) => String(row.parent ?? '') === data.name).length }} 个子类</small></span></template></el-tree></div>
        <el-empty v-else :description="loadFailed ? '分类加载失败，请重试' : '暂无可展示的产品分类'" :image-size="52"><el-button v-if="loadFailed" type="primary" @click="load">重试</el-button></el-empty>
      </template>
    </section>

    <section v-if="currentModule.key === 'leads'" class="panel lead-views-panel">
      <div class="lead-views-heading"><div class="panel-kicker">LEAD VIEWS</div><h2>线索视图</h2><p>视图仅筛选当前接口已授权的线索结果；“线索公海”根据现有负责人/状态字段判断。</p></div>
      <div class="lead-views-controls">
        <el-radio-group :model-value="leadView" class="lead-view-switch" @update:model-value="onLeadViewChange">
          <el-radio-button value="all">全部线索</el-radio-button>
          <el-radio-button value="mine">我的线索</el-radio-button>
          <el-radio-button value="unassigned">线索公海 / 未分配</el-radio-button>
        </el-radio-group>
        <span class="lead-view-count">{{ loading ? '读取中' : `${formatNumber(total)} 条` }}</span>
      </div>
    </section>

    <section class="stats-grid module-stats">
      <StatCard v-for="(stat, index) in currentModule.stats" :key="stat.key + stat.label" :label="stat.label" :value="displayStat(stat)" :tone="stat.tone ?? (index === 1 ? 'gold' : index === 3 ? 'rose' : 'mint')" :note="stat.key.includes('率') ? '按当前数据计算' : '基于当前筛选范围'" />
    </section>

    <section v-if="isReport" class="report-range-bar"><span>报表周期筛选</span><el-date-picker v-model="reportRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" clearable @change="onReportRangeChange" /></section>
    <el-alert v-if="isReport && !canViewSensitivePrices" class="scope-preview-alert" title="金额趋势图已隐藏" description="当前角色没有敏感金额查看权限；图表可能通过柱形高度或悬浮提示暴露金额，因此已整体隐藏。日期筛选仍可用于当前授权的报表记录。" type="warning" :closable="false" show-icon />
    <section v-if="isReport && canViewSensitivePrices" class="panel report-chart-panel">
      <div class="panel-heading"><div><div class="panel-kicker">PERFORMANCE OVERVIEW</div><h2>{{ currentModule.title }}趋势</h2><p>选择周期观察关键指标的变化</p></div></div>
      <EChart :option="reportOption()" height="250px" />
    </section>

    <section class="panel data-panel">
      <div class="data-toolbar">
        <div class="data-toolbar__top">
          <el-input v-model="search" class="search-input" clearable :prefix-icon="Search" :placeholder="currentModule.searchPlaceholder ?? '输入关键词搜索'" @keyup.enter="onSearch" @clear="onSearch"><template #append><el-button :icon="Search" @click="onSearch">搜索</el-button></template></el-input>
          <div class="data-toolbar__actions">
            <el-radio-group v-if="currentModule.key === 'opportunities'" v-model="opportunityViewMode" size="small" style="margin-right: 8px;">
              <el-radio-button value="table">表格视图</el-radio-button>
              <el-radio-button value="kanban">看板视图</el-radio-button>
            </el-radio-group>
            <el-button v-if="selectedRows.length && canEdit && canBatchChangeStatus" class="button-secondary" @click="batchChange('已完成')"><Check />批量完成（{{ selectedRows.length }}）</el-button>
            <el-dropdown v-if="selectedRows.length && (canRemove || canEdit && canBatchChangeStatus)" trigger="click"><el-button class="button-secondary">批量操作<ArrowDown /></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="canEdit && canBatchChangeStatus" @click="batchChange('进行中')">批量标记进行中</el-dropdown-item><el-dropdown-item v-if="canEdit && canBatchChangeStatus" @click="batchChange('已暂停')">批量暂停</el-dropdown-item><el-dropdown-item v-if="canRemove && !isReport" divided @click="removeRows(selectedRows.map((row) => row.id))">批量删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
            <el-button v-if="canExport" class="button-secondary" :icon="Download" @click="exportCsv">导出</el-button>
          </div>
        </div>
        <div v-if="hasFilters || canSelectDepartment" class="data-toolbar__filters">
          <template v-if="hasFilters"><div v-for="filter in currentModule.filters" :key="filter.key" class="filter-item"><span class="filter-label">{{ filter.label }}</span><el-select v-model="filters[filter.key]" class="filter-select" clearable placeholder="请选择" @change="onSearch"><el-option v-for="option in fieldOptions(filter)" :key="option.value" :label="option.label" :value="option.value" /><el-option v-if="linkedOptionsErrors[filter.key]" disabled value="__load_error__" label="选项加载失败" /></el-select><el-button v-if="linkedOptionsErrors[filter.key]" text type="primary" @click="retryLinkedOptions">重试</el-button></div></template>
          <div v-if="canSelectDepartment" class="filter-item"><span class="filter-label">所属部门</span><el-select v-model="departmentFilter" class="filter-select department-filter" clearable placeholder="全部部门" @change="onDepartmentFilterChange"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id" /></el-select></div>
          <el-button v-if="hasActiveFilters" text class="reset-button" @click="resetFilters">重置</el-button>
        </div>
      </div>
      <!-- 商机看板视图 -->
      <div v-if="currentModule.key === 'opportunities' && opportunityViewMode === 'kanban'" class="kanban-board">
        <div
          v-for="col in kanbanColumns"
          :key="col.stage"
          class="kanban-col"
          @dragover.prevent
          @drop="onKanbanDrop(col.stage)"
        >
          <div class="kanban-col__header">
            <div class="kanban-col__title">
              <span class="kanban-col__name">{{ col.stage }}</span>
              <el-tag size="small" round effect="plain">{{ col.count }}</el-tag>
            </div>
            <span v-if="col.amount" class="kanban-col__amount">¥{{ formatNumber(col.amount) }}</span>
          </div>
          <div class="kanban-col__body">
            <div
              v-for="item in col.items"
              :key="item.id"
              class="kanban-card"
              draggable="true"
              @dragstart="onKanbanDragStart($event, item)"
              @click="openDetail(item)"
            >
              <div class="kanban-card__header">
                <strong class="kanban-card__name">{{ item.name }}</strong>
                <span v-if="item.amount" class="kanban-card__amount">¥{{ formatNumber(Number(item.amount)) }}</span>
              </div>
              <div v-if="item.customer" class="kanban-card__customer">{{ item.customer }}</div>
              <div class="kanban-card__footer">
                <span class="kanban-card__owner">{{ item.owner || '未分配' }}</span>
                <el-progress
                  v-if="item.probability !== undefined"
                  :percentage="Number(item.probability)"
                  :stroke-width="4"
                  :show-text="false"
                  style="width: 50px"
                />
                <el-button
                  v-if="col.stage !== '赢单' && col.stage !== '输单' && canEdit"
                  size="small"
                  link
                  type="primary"
                  @click.stop="rowAction('advance', item)"
                >推进</el-button>
              </div>
            </div>
            <div v-if="!col.items.length" class="kanban-col__empty">可拖拽商机至此</div>
          </div>
        </div>
      </div>
      <div v-else class="table-wrap">
        <el-table ref="tableRef" v-loading="loading" :data="pageRows" row-key="id" class="crm-table" @selection-change="onSelectionChange" @sort-change="handleSort">
          <el-table-column v-if="!isReport && (canRemove || canEdit && canBatchChangeStatus)" type="selection" width="44" />
          <el-table-column v-if="canSelectDepartment" label="所属部门" min-width="125"><template #default="{ row }">{{ departmentName(row.departmentId) }}</template></el-table-column>
          <el-table-column v-for="column in currentModule.columns" :key="column.key" :prop="column.key" :label="column.label" :width="column.width" min-width="125" show-overflow-tooltip sortable="custom">
            <template #default="{ row }">
              <template v-if="column.type === 'status'"><el-tag :type="statusType(currentModule.key === 'quotes' && column.key === 'status' ? quoteStatus(row) : row[column.key])" effect="light" round>{{ currentModule.key === 'quotes' && column.key === 'status' ? quoteStatus(row) : row[column.key] ?? '—' }}</el-tag></template>
              <template v-else-if="column.type === 'progress'"><div class="progress-cell"><el-progress :percentage="Number(row[column.key] ?? 0)" :stroke-width="5" :show-text="false" /><span>{{ row[column.key] ?? 0 }}%</span></div></template>
              <span v-else :class="{ 'cell-strong': ['name', 'title', 'customer', 'product', 'orderNo', 'quoteNo', 'ticketNo'].includes(column.key), 'cell-muted': column.type === 'muted' }">{{ formatCell(row, column) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!isReport" label="操作" fixed="right" width="205" class-name="actions-cell">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" @click="rowAction('detail', row)">详情</el-button>
                <el-button v-if="auth.can(actionPermission) && !isReport" v-permission="actionPermission" link type="primary" @click="rowAction('edit', row)">编辑</el-button>
                <el-dropdown v-if="!isReport && (canEdit || canRemove)" trigger="click">
                  <el-button link class="more-action"><MoreFilled /></el-button>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item v-if="canEdit && currentModule.key === 'todos' && row.status !== '已完成'" @click="rowAction('finish', row)"><Check />标记完成</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit && canClaimCustomer(row)" @click="rowAction('claim', row)"><Plus />认领客户</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit && currentModule.key === 'leads' && canClaimLead(row)" @click="rowAction('claim-lead', row)"><Plus />领取线索</el-dropdown-item>
                    <el-dropdown-item v-if="auth.can('crm:pool:assign') && currentModule.key === 'leads' && !canClaimLead(row)" @click="rowAction('recycle-lead', row)"><Refresh />回收到公海</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit && auth.can('crm:customer:add') && currentModule.key === 'leads' && row.stage !== '转化成功'" @click="rowAction('convert', row)"><ArrowRight />转为正式客户</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit && currentModule.key === 'opportunities' && row.stage !== '赢单' && row.stage !== '输单'" @click="rowAction('advance', row)"><ArrowRight />推进销售阶段</el-dropdown-item>
                    <el-dropdown-item v-if="auth.can('sales:quote:edit') && currentModule.key === 'quotes' && quoteStatus(row) !== '已通过'" @click="rowAction('approve-quote', row)"><Check />审核通过（页面预览）</el-dropdown-item>
                    <el-dropdown-item v-if="auth.can('sales:quote:edit') && currentModule.key === 'quotes' && quoteStatus(row) !== '已拒绝'" @click="rowAction('reject-quote', row)"><Delete />审核拒绝（页面预览）</el-dropdown-item>
                    <el-dropdown-item v-if="auth.can('sales:order:add') && currentModule.key === 'quotes' && ['已通过', '已完成', '已审核', '已接受'].includes(quoteStatus(row))" @click="rowAction('convert-quote', row)"><ArrowRight />转为销售订单</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit && currentModule.key === 'orders' && row.status !== '已完成'" @click="rowAction('fulfill', row)"><Check />完成订单履约</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit && currentModule.key === 'service-tickets' && row.status === '待处理'" @click="rowAction('service', row)"><Check />开始处理</el-dropdown-item>
                    <el-dropdown-item v-if="canRemove && !isReport" divided class="danger-menu-item" @click="rowAction('remove', row)"><Delete />删除</el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty" :class="{ 'table-empty--todo': currentModule.key === 'todos' }"><div class="empty-mark">{{ loadFailed ? '!' : hasActiveFilters ? '⌕' : '✓' }}</div><strong>{{ loadFailed ? '数据读取失败' : hasActiveFilters ? '没有找到符合条件的记录' : currentModule.key === 'todos' ? '暂无待办事项' : '暂无记录' }}</strong><span v-if="loadFailed">已清除旧结果以免误认为是当前筛选数据。</span><span v-else-if="hasActiveFilters">尝试调整或重置筛选条件。</span><span v-else-if="canAdd">{{ currentModule.key === 'todos' ? '当前还没有待办，可以创建一条开始安排工作。' : '当前还没有记录，可以创建一条新记录。' }}</span><span v-else>当前没有可显示的记录。</span><el-button v-if="loadFailed" text type="primary" @click="load">重试</el-button><el-button v-else-if="hasActiveFilters" text type="primary" @click="resetFilters">重置筛选</el-button><el-button v-else-if="canAdd" type="primary" :icon="Plus" @click="beginCreate">{{ currentModule.key === 'todos' ? '新建待办' : titleAction }}</el-button></div></template>
        </el-table>
      </div>
      <div v-if="!(currentModule.key === 'opportunities' && opportunityViewMode === 'kanban')" class="table-footer" :class="{ 'table-footer--empty': total === 0 }"><span>共 {{ formatNumber(total) }} 条记录 <span v-if="selectedRows.length">· 已选择 {{ selectedRows.length }} 条</span></span><el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="sizes, prev, pager, next, jumper" @size-change="load" @current-change="load" /></div>
    </section>

    <el-dialog v-model="dialogOpen" :title="formMode === 'create' ? titleAction : `编辑${currentModule.singular}`" width="min(700px, 92vw)" destroy-on-close class="crm-dialog">
      <div class="dialog-intro">{{ formMode === 'create' ? `填写${currentModule.singular}信息后即可加入工作流程。` : '修改信息后保存，关联业务会同步更新。' }}</div>
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-position="top" class="record-form" @submit.prevent="saveForm">
        <el-form-item v-if="canSelectDepartment" label="所属部门" prop="departmentId" required class="form-item--wide"><el-select v-model="formModel.departmentId as string" class="w-full" placeholder="选择记录所属部门" filterable @change="onFormDepartmentChange"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id" /></el-select></el-form-item>
        <el-form-item v-for="field in visibleFormFields" :key="field.key" :label="field.label" :prop="field.key" :required="field.required" :class="{ 'form-item--wide': field.kind === 'textarea' || field.kind === 'lines' || field.span === 2 }">
          <el-select v-if="field.kind === 'select'" v-model="formModel[field.key] as string" class="w-full" :placeholder="linkedOptionsErrors[field.key] ? `${field.label}选项加载失败` : linkedOptionsRequested[field.key] && !linkedOptionsLoaded[field.key] ? `正在加载${field.label}选项` : field.placeholder ?? `请选择${field.label}`" :filterable="Boolean(linkedResources[field.key])" clearable><el-option v-for="option in fieldOptions(field)" :key="option.value" :label="option.label" :value="option.value" /><el-option v-if="linkedOptionsErrors[field.key]" disabled value="__load_error__" label="选项加载失败，请重试" /><el-option v-else-if="linkedOptionsRequested[field.key] && !linkedOptionsLoaded[field.key]" disabled value="__loading__" label="选项加载中…" /><el-option v-else-if="linkedOptionsLoaded[field.key] && !fieldOptions(field).length" disabled value="__no_options__" label="没有可用选项" /></el-select>
          <el-select v-else-if="field.kind === 'multiselect'" v-model="formModel[field.key] as string[]" class="w-full" multiple collapse-tags collapse-tags-tooltip :placeholder="`请选择${field.label}`" filterable clearable><el-option v-for="option in fieldOptions(field)" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-button v-if="field.kind === 'select' && linkedOptionsErrors[field.key]" text type="primary" @click="retryLinkedOptions">重试加载{{ field.label }}</el-button>
          <div v-else-if="field.kind === 'lines'" class="line-items-editor">
            <div v-for="(line, index) in lineItems" :key="index" class="line-items-row">
              <el-select :model-value="lineSkuValue(line)" class="line-items-product" placeholder="选择产品规格" filterable @change="setLineProduct(line, String($event))"><el-option v-for="option in fieldOptions(field)" :key="option.value" :label="option.label" :value="option.value" /></el-select>
              <el-input-number v-model="line.quantity" :min="1" :max="99999" :precision="0" controls-position="right" aria-label="数量" />
              <el-input-number v-if="canViewSensitivePrices" v-model="line.unitPrice" :min="0" :precision="2" controls-position="right" aria-label="单价" />
              <el-input-number v-model="line.discount" :min="0" :max="100" :precision="2" controls-position="right" aria-label="折扣百分比" />
                <span v-if="!canViewSensitivePrices" class="line-items-masked">单价已隐藏</span>
              <span class="line-items-total">{{ canViewSensitivePrices ? formatMoney(line.quantity * line.unitPrice * Number(line.discount ?? 100) / 100) : '••••' }}</span>
              <el-button text type="danger" aria-label="移除产品明细" @click="lineItems.splice(index, 1)">移除</el-button>
            </div>
            <div class="line-items-footer"><el-button class="button-secondary" @click="addProductLine"><Plus />添加产品</el-button><span>合计 <strong>{{ canViewSensitivePrices ? formatMoney(lineItemsTotal) : '••••' }}</strong></span></div>
          </div>
          <el-date-picker v-else-if="field.kind === 'date'" :model-value="dateFieldValue(field.key)" @update:model-value="setDateFieldValue(field.key, $event)" class="w-full" type="date" value-format="YYYY-MM-DD" :placeholder="`请选择${field.label}`" />
          <el-input-number v-else-if="field.kind === 'number' || field.kind === 'money'" v-model="formModel[field.key] as number" class="w-full" :min="numberInputMin(field)" :max="numberInputMax(field)" :precision="field.kind === 'money' ? 2 : 0" :controls="false" :placeholder="`请输入${field.label}`" />
          <el-input v-else-if="field.kind === 'textarea'" v-model="formModel[field.key] as string" type="textarea" :rows="3" :placeholder="field.placeholder ?? `请输入${field.label}`" maxlength="500" show-word-limit />
          <el-input v-else v-model="formModel[field.key] as string" :placeholder="field.placeholder ?? `请输入${field.label}`" />
        </el-form-item>
      </el-form>
      <template #footer><el-button class="button-secondary" @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveForm">保存记录</el-button></template>
    </el-dialog>

    <el-drawer v-model="drawerOpen" :title="`${currentModule.singular}详情`" :size="currentModule.key === 'customers' ? 'min(88vw, 880px, 100vw)' : currentModule.key === 'products' ? 'min(78vw, 760px, 100vw)' : 'min(520px, 100vw)'" class="detail-drawer">
      <template v-if="detailRecord">
        <div class="detail-hero"><div class="detail-avatar">{{ String(detailRecord.name ?? detailRecord.title ?? detailRecord.customer ?? 'CRM').slice(0, 1) }}</div><div><div class="panel-kicker">RECORD PROFILE</div><h2>{{ detailRecord.name ?? detailRecord.title ?? detailRecord.customer ?? detailRecord.product ?? detailRecord.orderNo ?? currentModule.singular }}</h2><span class="detail-id">记录编号 {{ detailRecord.id }}</span></div></div>
        <el-divider />
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item v-if="hasDepartmentScope" label="所属部门">{{ departmentName(detailRecord.departmentId) }}</el-descriptions-item>
          <el-descriptions-item v-for="field in currentModule.fields" :key="field.key" :label="field.label">{{ currentModule.key === 'quotes' && field.key === 'status' ? quoteStatus(detailRecord) : formatDetailField(field, detailRecord[field.key]) }}</el-descriptions-item>
        </el-descriptions>
        <section v-if="currentModule.key === 'customers'" class="detail-module-extension"><CustomerWorkspace :customer="detailRecord" :show-sensitive-amounts="canViewSensitivePrices" /></section>
        <LeadJourneyPanel v-if="currentModule.key === 'leads'" :lead="detailRecord" />
        <ProductSkuPanel v-if="currentModule.key === 'products'" class="detail-module-extension" :record="detailRecord" :show-sensitive-price="canViewSensitivePrices" />
        <BusinessRelationsPanel v-if="['orders', 'shipments', 'payments', 'receivables', 'returns', 'service-tickets'].includes(currentModule.key)" :resource="currentModule.key" :record="detailRecord" :show-sensitive-amounts="canViewSensitivePrices" />
        <section v-if="currentModule.key === 'quotes'" class="quote-review-preview">
          <el-alert title="报价审核前端预览" description="审核结果仅保留在当前页面会话，不修改报价记录；服务端审批/审计未接入。转订单会通过现有销售订单资源新增一条记录。" type="warning" :closable="false" show-icon />
          <div class="quote-review-actions">
            <el-tag :type="statusType(quoteStatus(detailRecord))" effect="light" round>当前状态：{{ quoteStatus(detailRecord) }}</el-tag>
            <el-button v-if="auth.can('sales:quote:edit')" class="button-secondary" :disabled="quoteStatus(detailRecord) === '已通过'" @click="rowAction('approve-quote', detailRecord)">审核通过</el-button>
            <el-button v-if="auth.can('sales:quote:edit')" class="button-secondary" :disabled="quoteStatus(detailRecord) === '已拒绝'" @click="rowAction('reject-quote', detailRecord)">拒绝报价</el-button>
            <el-button v-if="auth.can('sales:order:add')" type="primary" :disabled="!['已通过', '已完成', '已审核', '已接受'].includes(quoteStatus(detailRecord))" @click="rowAction('convert-quote', detailRecord)">转为销售订单</el-button>
          </div>
        </section>
        <div v-if="!isReport" class="detail-actions"><el-button v-if="canEdit" v-permission="actionPermission" type="primary" :icon="Edit" @click="drawerOpen = false; beginEdit(detailRecord)">编辑信息</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.scope-preview-alert { margin-bottom: 13px; }
.pool-config-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; margin-bottom: 13px; padding: 12px 16px; }
.pool-config-title { display: flex; min-width: 96px; flex-direction: column; gap: 3px; }
.pool-config-title strong { color: #3d5547; font-size: 14px; }
.pool-config-item { display: inline-flex; align-items: center; gap: 8px; color: #5b6f61; font-size: 12px; white-space: nowrap; }
.pool-days-input { width: 72px; }
.pool-config-actions { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }
.report-range-bar { display: flex; align-items: center; justify-content: flex-end; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; color: #718176; font-size: 12px; }
.report-range-bar > span { flex: 0 0 auto; white-space: nowrap; }
.report-range-bar :deep(.el-date-editor) { flex: 0 1 236px; min-width: 236px; }
.category-tree-panel { margin-bottom: 14px; padding: 15px 18px; }
.category-tree-heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.category-tree-heading h2 { margin: 4px 0; color: #355142; font-size: 16px; }
.category-tree-heading p { margin: 0; color: #8b988e; font-size: 12px; }
.category-tree-heading-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 4px; }
.category-tree-heading-actions :deep(.el-button) { font-size: 12px; }
.category-tree-wrap { max-height: 230px; overflow: auto; margin-top: 13px; padding: 7px 10px; border: 1px solid #edf1ec; border-radius: 9px; background: #fcfdfb; }
.category-tree-wrap :deep(.el-tree-node__content) { height: 34px; border-radius: 7px; }
.category-tree-wrap :deep(.el-tree-node__content:hover) { background: #f1f6f1; }
.category-tree-node { display: flex; min-width: 0; width: 100%; align-items: center; gap: 9px; color: #52675a; font-size: 12px; }
.category-tree-node small { margin-left: auto; color: #a2ada3; font-size: 10px; }
.detail-module-extension { margin-top: 18px; }
.lead-views-panel { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 13px; margin-bottom: 14px; padding: 14px 17px; }
.lead-views-panel h2 { margin: 4px 0; color: #355142; font-size: 15px; }
.lead-views-panel p { margin: 0; color: #8b988e; font-size: 11px; }
.lead-views-heading { min-width: 0; }
.lead-views-controls { display: flex; align-items: center; gap: 14px; flex: 0 0 auto; }
.lead-view-switch { flex: none; }
.lead-view-count { color: #7f8e82; font-size: 11px; white-space: nowrap; }
.quote-review-preview { display: grid; gap: 11px; margin-top: 18px; }
.quote-review-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.line-items-masked { min-width: 112px; color: #a17f49; font-size: 11px; text-align: center; }

/* 商机看板样式 */
.kanban-board {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 12px 4px 16px;
  scrollbar-width: thin;
}
.kanban-col {
  flex: 0 0 260px;
  min-width: 260px;
  background: #f7faf7;
  border: 1px solid #e2ebe2;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  max-height: 720px;
}
.kanban-col__header {
  padding: 10px 12px;
  background: #eef5ee;
  border-bottom: 1px solid #e0ebe0;
  border-radius: 9px 9px 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kanban-col__title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kanban-col__name {
  font-size: 13px;
  font-weight: 700;
  color: #2b4535;
}
.kanban-col__amount {
  font-size: 11px;
  font-weight: 600;
  color: #4b785d;
}
.kanban-col__body {
  padding: 10px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  min-height: 120px;
}
.kanban-card {
  background: #ffffff;
  border: 1px solid #e1ebe1;
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: 0 1px 4px rgba(35, 60, 45, 0.04);
  cursor: grab;
  transition: transform 0.15s, box-shadow 0.15s;
}
.kanban-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 10px rgba(35, 60, 45, 0.08);
}
.kanban-card:active {
  cursor: grabbing;
}
.kanban-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 4px;
}
.kanban-card__name {
  font-size: 13px;
  color: #253d2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kanban-card__amount {
  font-size: 12px;
  font-weight: 700;
  color: #3b7450;
  flex-shrink: 0;
}
.kanban-card__customer {
  font-size: 11px;
  color: #798d7f;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kanban-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding-top: 6px;
  border-top: 1px dashed #eef2ee;
}
.kanban-card__owner {
  font-size: 11px;
  color: #8c9c90;
}
.kanban-col__empty {
  text-align: center;
  padding: 30px 0;
  color: #a4b3a7;
  font-size: 11px;
  border: 1px dashed #d5e3d5;
  border-radius: 6px;
}

@media (max-width: 640px) {
  .category-tree-panel { padding: 13px; }
  .category-tree-heading { align-items: flex-start; }
  .lead-views-panel { align-items: flex-start; flex-direction: column; padding: 13px; }
  .lead-views-controls { width: 100%; flex-wrap: wrap; justify-content: space-between; }
  .lead-view-switch { display: flex; width: 100%; flex-wrap: wrap; }
  .lead-view-switch :deep(.el-radio-button) { flex: 1; }
  .report-range-bar { align-items: stretch; flex-direction: column; gap: 7px; }
  .report-range-bar :deep(.el-date-editor) { width: 100%; min-width: 0; }
  .quote-review-actions { align-items: stretch; flex-direction: column; }
}
</style>
