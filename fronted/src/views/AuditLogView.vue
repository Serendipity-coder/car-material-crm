<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeading from '../components/PageHeading.vue'
import { crmBusinessApi, type AuditLogRow } from '../api/system'

const rows = ref<AuditLogRow[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 20, account: '', resource: '', action: '', start: '', end: '' })

const RESOURCE_LABELS: Record<string, string> = {
  leads: '销售线索', pool: '客户公海', customers: '客户档案', contacts: '联系人', followups: '跟进记录',
  opportunities: '销售商机', quotes: '报价单', orders: '销售订单', returns: '退换货单', shipments: '发货记录',
  payments: '收款记录', receivables: '应收账款', 'service-tickets': '售后工单', visits: '客户回访', todos: '我的待办',
  stores: '客户门店', 'reports-sales': '销售统计', 'reports-customer-purchase': '客户采购分析', 'reports-product-sales': '产品销售分析',
  'system-user': '系统用户', 'system-role': '系统角色', 'system-dept': '系统部门',
}
const ACTION_LABELS: Record<string, string> = {
  CREATE: '新增', UPDATE: '编辑', DELETE: '删除', STATUS: '状态变更', EXPORT: '导出',
  CLAIM: '领取', ASSIGN: '分配', RECYCLE: '回收', AUTO_RECYCLE: '自动回收', APPROVE: '审批', REJECT: '驳回', CONVERT: '转单',
  ASSIGN_MENU: '分配权限', ASSIGN_CATEGORY: '分配品类', ASSIGN_SCOPE: '分配数据范围',
}

function resourceLabel(key: string): string {
  return RESOURCE_LABELS[key] ?? key ?? '—'
}
function actionLabel(key: string): string {
  return ACTION_LABELS[key] ?? key ?? '—'
}
function formatTime(value?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
function recordLabel(row: AuditLogRow): string {
  return row.recordLabel || row.recordId || '—'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await crmBusinessApi.auditLogs({
      pageNum: query.pageNum, pageSize: query.pageSize,
      account: query.account || undefined, resource: query.resource || undefined,
      action: query.action || undefined, start: query.start || undefined, end: query.end || undefined,
    })
    rows.value = data.rows
    total.value = data.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作日志读取失败')
  } finally {
    loading.value = false
  }
}
function onSearch(): void { query.pageNum = 1; void load() }
function onReset(): void { query.account = ''; query.resource = ''; query.action = ''; query.start = ''; query.end = ''; onSearch() }
onMounted(load)
</script>

<template>
  <div class="audit-page">
    <PageHeading title="操作日志" eyebrow="AUDIT TRAIL" description="记录角色分配、品类授权、数据增删改、公海领取、导出等关键操作，可追溯、可审计。" />

    <section class="panel audit-filter-panel">
      <div class="audit-filters">
        <el-input v-model="query.account" clearable placeholder="操作人账号" class="audit-filter-input" @keyup.enter="onSearch" />
        <el-select v-model="query.resource" clearable placeholder="资源类型" class="audit-filter-input">
          <el-option v-for="(label, key) in RESOURCE_LABELS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="query.action" clearable placeholder="动作类型" class="audit-filter-input">
          <el-option v-for="(label, key) in ACTION_LABELS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-date-picker v-model="query.start" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" class="audit-filter-input" />
        <el-date-picker v-model="query.end" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" class="audit-filter-input" />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>
    </section>

    <section class="panel audit-table-panel">
      <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
        <el-table-column label="时间" min-width="160"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="operatorAccount" label="操作人" min-width="110" />
        <el-table-column label="资源" min-width="120"><template #default="{ row }">{{ resourceLabel(row.resourceKey) }}</template></el-table-column>
        <el-table-column label="记录" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ recordLabel(row) }}</template></el-table-column>
        <el-table-column label="动作" min-width="100"><template #default="{ row }">{{ actionLabel(row.actionType) }}</template></el-table-column>
        <el-table-column prop="result" label="结果" min-width="80" />
        <el-table-column prop="clientIp" label="IP" min-width="120" />
      </el-table>
      <div class="audit-pagination">
        <span>共 {{ total }} 条记录</span>
        <el-pagination layout="sizes, prev, pager, next, jumper" :total="total" :current-page="query.pageNum" :page-size="query.pageSize" :page-sizes="[10, 20, 50]" @current-change="(p: number) => { query.pageNum = p; void load() }" @size-change="(s: number) => { query.pageSize = s; query.pageNum = 1; void load() }" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.audit-page { display: flex; flex-direction: column; gap: 14px; }
.audit-filter-panel { padding: 14px 16px; }
.audit-filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.audit-filter-input { width: 150px; }
.audit-table-panel { padding: 4px 8px 12px; }
.audit-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 8px 0; color: #98a39a; font-size: 12px; }
.audit-pagination :deep(.el-pagination) { --el-pagination-font-size: 12px; --el-pagination-button-color: #7d8c81; }
</style>
