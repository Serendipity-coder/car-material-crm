<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CrmRecord } from '../api/types'
import { moduleApi } from '../api/modules'
import { useAuthStore } from '../stores/auth'
import { formatDate, formatMoney } from '../utils/format'

type RelationKey = 'orders' | 'shipments' | 'payments' | 'receivables' | 'returns' | 'service-tickets' | 'visits'
interface RelationGroup { resource: RelationKey; label: string; permission: string; rows: CrmRecord[] }

const props = withDefaults(defineProps<{ resource: string; record: CrmRecord; showSensitiveAmounts?: boolean }>(), { showSensitiveAmounts: false })
const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const groups = ref<RelationGroup[]>([])
const canDisplayAmounts = computed(() => props.showSensitiveAmounts === true)
let sequence = 0

const orderRelated: RelationGroup[] = [
  { resource: 'shipments', label: '发货记录', permission: 'delivery:shipment:list', rows: [] },
  { resource: 'payments', label: '收款记录', permission: 'delivery:payment:list', rows: [] },
  { resource: 'receivables', label: '应收账款', permission: 'delivery:receivable:list', rows: [] },
  { resource: 'returns', label: '退换货单', permission: 'sales:return:list', rows: [] },
  { resource: 'service-tickets', label: '售后工单', permission: 'service:ticket:list', rows: [] },
]
const routeByResource: Record<RelationKey, string> = {
  orders: '/orders', shipments: '/shipments', payments: '/payments', receivables: '/receivables',
  returns: '/returns', 'service-tickets': '/service-tickets', visits: '/visits',
}
const orderNumber = computed(() => String(props.record.orderNo ?? props.record.orderNumber ?? ''))
const primaryOrderId = computed(() => props.resource === 'orders' ? String(props.record.id ?? '') : String(props.record.orderId ?? ''))
const orderIsPrimary = computed(() => props.resource === 'orders')
const sourceOrderResource = computed(() => ['shipments', 'payments', 'receivables', 'returns', 'service-tickets'].includes(props.resource))

function exactOrderMatch(row: CrmRecord): boolean {
  const references = [orderNumber.value, primaryOrderId.value].filter(Boolean)
  const candidates = [row.orderNo, row.orderNumber, row.orderId, row.id].filter((value) => value !== undefined && value !== null && String(value) !== '').map(String)
  return references.some((reference) => candidates.includes(reference))
}
function recordTitle(row: CrmRecord, resource: RelationKey): string {
  const fields: Record<RelationKey, string[]> = {
    orders: ['orderNo', 'orderNumber', 'id'], shipments: ['shipmentNo', 'trackingNo', 'orderNo', 'id'],
    payments: ['receiptNo', 'orderNo', 'id'], receivables: ['invoiceNo', 'orderNo', 'id'],
    returns: ['returnNo', 'orderNo', 'id'], 'service-tickets': ['ticketNo', 'name', 'type', 'id'], visits: ['date', 'method', 'id'],
  }
  const key = fields[resource].find((candidate) => row[candidate] !== undefined && row[candidate] !== null && String(row[candidate]) !== '')
  return key ? String(row[key]) : '—'
}
function rowFacts(row: CrmRecord): string[] {
  const facts = [row.status, row.deliveryStatus, row.paymentStatus, row.date, row.createdAt, row.shipmentDate, row.dueDate]
    .filter((value) => value !== undefined && value !== null && String(value).trim() !== '')
    .map((value) => String(value))
  const amount = row.amount
  if (canDisplayAmounts.value && amount !== undefined && amount !== null && amount !== '') facts.push(formatMoney(amount))
  return [...new Set(facts)].slice(0, 4)
}
async function load(): Promise<void> {
  const current = ++sequence
  loading.value = true
  error.value = ''
  groups.value = []
  try {
    if (orderIsPrimary.value) {
      const next = await Promise.all(orderRelated.map(async (group) => {
        if (!auth.can(group.permission)) return group
        const filters: Record<string, string> = orderNumber.value ? { orderNo: orderNumber.value } : primaryOrderId.value ? { orderId: primaryOrderId.value } : {}
        const rows = await moduleApi.all(group.resource, { filters })
        group.rows = rows.filter(exactOrderMatch)
        return group
      }))
      if (current === sequence) groups.value = next
    } else if (sourceOrderResource.value && auth.can('sales:order:list') && (orderNumber.value || primaryOrderId.value)) {
      const filters: Record<string, string> = orderNumber.value ? { orderNo: orderNumber.value } : { id: primaryOrderId.value }
      const rows = await moduleApi.all('orders', { filters })
      if (current === sequence) groups.value = [{ resource: 'orders', label: '来源销售订单', permission: 'sales:order:list', rows: rows.filter(exactOrderMatch) }]
    }
  } catch (cause) {
    if (current === sequence) error.value = cause instanceof Error ? cause.message : '关联业务记录暂时无法读取'
  } finally {
    if (current === sequence) loading.value = false
  }
}
function openModule(resource: RelationKey): void { void router.push(routeByResource[resource]) }
function displayDate(value: unknown): string { return value ? formatDate(value) : '' }

onMounted(() => {
  void load()
})
onBeforeUnmount(() => { sequence += 1 })
watch(() => [props.resource, props.record.id, props.record.orderNo, props.record.customer], () => { void load() })
</script>

<template>
  <section v-if="orderIsPrimary || sourceOrderResource" class="business-relations" aria-label="关联业务记录">
    <header class="business-relations__header">
      <div><div class="panel-kicker">RELATED BUSINESS</div><h3>订单与履约关联</h3><p>仅从已有模块读取关联记录；没有可关联字段或访问权限时不推断业务关系。</p></div>
      <el-tag type="info" effect="plain" round>只读</el-tag>
    </header>
    <el-alert v-if="error" title="关联记录读取失败" :description="error" type="warning" :closable="false" show-icon />
    <el-skeleton v-else-if="loading" :rows="2" animated />
    <div v-else-if="groups.length" class="business-relations__groups">
      <article v-for="group in groups" :key="group.resource" class="business-relations__group">
        <div class="business-relations__title"><h4>{{ group.label }}</h4><span>{{ auth.can(group.permission) ? `${group.rows.length} 条` : '无访问权限' }}</span></div>
        <el-alert v-if="!auth.can(group.permission)" :title="`当前账号没有${group.label}查看权限`" type="info" :closable="false" />
        <el-empty v-else-if="!group.rows.length" description="暂无可由现有订单编号/客户字段匹配的记录" :image-size="46" />
        <button v-else v-for="row in group.rows" :key="row.id" class="business-relations__row" type="button" @click="openModule(group.resource)">
          <span class="business-relations__row-title">{{ recordTitle(row, group.resource) }}</span>
          <span class="business-relations__row-facts"><span v-for="fact in rowFacts(row)" :key="fact">{{ fact }}</span></span>
          <span v-if="displayDate(row.orderDate)" class="business-relations__date">{{ displayDate(row.orderDate) }}</span>
        </button>
      </article>
    </div>
    <el-empty v-else-if="!auth.can('sales:order:list')" description="当前账号没有读取关联销售订单的权限" :image-size="50" />
    <el-empty v-else description="当前记录未提供订单编号，暂不能读取来源订单" :image-size="50" />
  </section>
</template>

<style scoped>
.business-relations { display: grid; gap: 12px; margin-top: 18px; padding: 16px; border: 1px solid #e6ece5; border-radius: 12px; background: #fff; }
.business-relations__header, .business-relations__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.business-relations__header h3 { margin: 4px 0; color: #354d3e; font-size: 15px; }
.business-relations__header p { margin: 0; color: #8f9b91; font-size: 11px; }
.business-relations__groups { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.business-relations__group { min-width: 0; padding: 12px; border: 1px solid #edf1ec; border-radius: 10px; background: #fbfcfa; }
.business-relations__title { margin-bottom: 8px; }
.business-relations__title h4 { margin: 0; color: #526659; font-size: 12px; }
.business-relations__title span { color: #9ba69d; font-size: 10px; }
.business-relations__row { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 0; border: 0; border-top: 1px solid #eef2ed; color: #566a5c; background: transparent; text-align: left; cursor: pointer; }
.business-relations__row:hover .business-relations__row-title { color: #528a70; }
.business-relations__row-title { overflow: hidden; flex: 1; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.business-relations__row-facts { display: flex; flex-wrap: wrap; gap: 5px; color: #829086; font-size: 10px; }
.business-relations__date { flex: 0 0 auto; color: #9aa59b; font-size: 10px; }
.business-relations :deep(.el-empty) { padding: 6px 0; }
@media (max-width: 640px) { .business-relations { padding: 13px; } .business-relations__groups { grid-template-columns: 1fr; } .business-relations__row { align-items: flex-start; flex-direction: column; } }
</style>
