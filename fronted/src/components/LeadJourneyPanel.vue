<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CrmRecord } from '../api/types'
import { moduleApi } from '../api/modules'
import { useAuthStore } from '../stores/auth'
import { resolveFrontendPolicy, subscribeFrontendPolicyChange } from '../utils/frontend-policy'
import { formatDate } from '../utils/format'

const props = defineProps<{ lead: CrmRecord }>()
const auth = useAuthStore()
const router = useRouter()
const stages = ['新线索', '已联系', '已验证', '转化成功']
const rows = ref<CrmRecord[]>([])
const loading = ref(false)
const error = ref('')
const policyRevision = ref(0)
const frontendPolicy = computed(() => { policyRevision.value; return resolveFrontendPolicy(auth.user?.roleIds ?? []) })
let sequence = 0
const currentStage = () => stages.indexOf(String(props.lead.stage ?? ''))
const linkedCustomer = () => String(props.lead.customer ?? props.lead.company ?? '')
function isVisibleWithinRoleDraft(row: CrmRecord): boolean {
  if (frontendPolicy.value.configured && frontendPolicy.value.dataScope === '本人' && !auth.can('system:data:all')) {
    const identity = [auth.user?.name, auth.user?.id, auth.user?.account, auth.user?.phone].map((value) => String(value ?? '').trim().toLocaleLowerCase()).filter(Boolean)
    const owners = [row.owner, row.ownerId, row.assignee, row.assigneeId, row.createdBy, row.createdById].filter((value) => value !== undefined && value !== null && String(value).trim() !== '').map((value) => String(value).trim().toLocaleLowerCase())
    if (!identity.length || !owners.some((owner) => identity.includes(owner))) return false
  }
  if (frontendPolicy.value.constrainedCategories && !auth.can('system:data:all')) {
    const raw = row.categories ?? row.categoryNames ?? row.interestCategories ?? row.category ?? row.categoryName
    const names = Array.isArray(raw) ? raw.map(String) : typeof raw === 'string' ? raw.split(/[,，、]/).map((name) => name.trim()) : []
    return names.some((name) => frontendPolicy.value.categoryNames.includes(name))
  }
  return true
}

async function load(): Promise<void> {
  const current = ++sequence
  rows.value = []
  error.value = ''
  if (!auth.can('crm:followup:list')) return
  loading.value = true
  try {
    const leadName = String(props.lead.name ?? props.lead.title ?? '')
    const result = await moduleApi.all('followups', { filters: { lead: leadName } })
    if (current !== sequence) return
    rows.value = result.filter((row) => (String(row.leadId ?? '') === String(props.lead.id) || String(row.lead ?? '') === leadName) && isVisibleWithinRoleDraft(row))
      .sort((a, b) => String(b.date ?? b.createdAt ?? '').localeCompare(String(a.date ?? a.createdAt ?? '')))
  } catch (cause) {
    if (current === sequence) error.value = cause instanceof Error ? cause.message : '跟进记录暂时无法读取'
  } finally {
    if (current === sequence) loading.value = false
  }
}
function openFollowups(): void { void router.push('/followups') }
function displayValue(record: CrmRecord): string { return String(record.outcome ?? record.result ?? record.note ?? record.type ?? '跟进记录') }

let unsubscribeFrontendPolicy: (() => void) | undefined
onMounted(() => {
  unsubscribeFrontendPolicy = subscribeFrontendPolicyChange(() => { policyRevision.value += 1; void load() })
  void load()
})
watch(() => [props.lead.id, props.lead.name, props.lead.stage], () => { void load() })
onBeforeUnmount(() => { sequence += 1; unsubscribeFrontendPolicy?.() })
</script>

<template>
  <section class="lead-journey" aria-label="线索跟进轨迹">
    <header class="lead-journey__header"><div><div class="panel-kicker">LEAD JOURNEY</div><h3>线索推进与跟进</h3><p>进度来自现有阶段字段；时间线只列出接口中明确关联到此线索的跟进记录。</p></div><el-button v-if="auth.can('crm:followup:list')" text type="primary" @click="openFollowups">跟进模块</el-button></header>
    <div class="lead-stage-strip">
      <div v-for="(stage, index) in stages" :key="stage" class="lead-stage" :class="{ 'is-current': index === currentStage(), 'is-passed': currentStage() >= 0 && index < currentStage() }">
        <span class="lead-stage__dot">{{ index + 1 }}</span><strong>{{ stage }}</strong><i v-if="index < stages.length - 1"></i>
      </div>
    </div>
    <div class="lead-context-grid">
      <div><span>线索公司</span><strong>{{ lead.company ?? lead.name ?? '—' }}</strong></div>
      <div><span>当前负责人</span><strong>{{ lead.owner ?? '未分配' }}</strong></div>
      <div><span>下次跟进</span><strong>{{ lead.nextDate ? formatDate(lead.nextDate) : '未设置' }}</strong></div>
      <div><span>线索评分</span><strong>{{ lead.score ?? '未评分' }}</strong></div>
    </div>
    <el-alert v-if="lead.invalidReason || lead.lostReason" title="线索状态说明" :description="String(lead.invalidReason ?? lead.lostReason)" type="info" :closable="false" show-icon />
    <div class="lead-followups-heading"><strong>已关联跟进记录</strong><span>{{ auth.can('crm:followup:list') ? `${rows.length} 条` : '无查看权限' }}</span></div>
    <el-skeleton v-if="loading" :rows="2" animated />
    <el-alert v-else-if="error" title="跟进记录读取失败" :description="error" type="warning" :closable="false" show-icon />
    <el-empty v-else-if="!auth.can('crm:followup:list')" description="当前账号没有查看跟进记录权限" :image-size="46" />
    <el-empty v-else-if="!rows.length" description="现有跟进资源中没有明确关联到此线索的记录" :image-size="46" />
    <ol v-else class="lead-followup-list">
      <li v-for="row in rows" :key="row.id"><time>{{ formatDate(row.date ?? row.createdAt) }}</time><div><strong>{{ displayValue(row) }}</strong><small>{{ row.owner ?? '未提供负责人' }} · {{ row.type ?? '未提供方式' }}</small></div><el-tag v-if="row.status" size="small" effect="plain" round>{{ row.status }}</el-tag></li>
    </ol>
    <el-alert v-if="linkedCustomer()" class="lead-customer-link" title="转换关联信息" :description="`当前接口返回的关联客户：${linkedCustomer()}。线索转客户动作仍使用现有客户创建/线索更新接口。`" type="success" :closable="false" show-icon />
  </section>
</template>

<style scoped>
.lead-journey { display: grid; gap: 13px; margin-top: 18px; padding: 16px; border: 1px solid #e6ece5; border-radius: 12px; background: #fff; }
.lead-journey__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.lead-journey__header h3 { margin: 4px 0; color: #354d3e; font-size: 15px; }
.lead-journey__header p { margin: 0; color: #8f9b91; font-size: 11px; line-height: 1.5; }
.lead-stage-strip { display: flex; align-items: flex-start; justify-content: space-between; gap: 0; padding: 4px 4px 11px; }
.lead-stage { position: relative; display: flex; min-width: 0; flex: 1; align-items: center; flex-direction: column; gap: 6px; color: #a0aaa2; text-align: center; }
.lead-stage__dot { z-index: 1; display: grid; width: 25px; height: 25px; place-items: center; border: 1px solid #dfe7df; border-radius: 50%; background: #fff; font-size: 10px; }
.lead-stage strong { font-size: 10px; font-weight: 500; white-space: nowrap; }
.lead-stage i { position: absolute; top: 12px; left: calc(50% + 14px); width: calc(100% - 28px); height: 1px; background: #e7ede7; }
.lead-stage.is-passed, .lead-stage.is-current { color: #548267; }
.lead-stage.is-passed .lead-stage__dot, .lead-stage.is-current .lead-stage__dot { border-color: #83af8e; background: #edf5ee; }
.lead-stage.is-passed i { background: #9dbda4; }
.lead-context-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 7px; }
.lead-context-grid > div { display: flex; min-width: 0; flex-direction: column; gap: 5px; padding: 9px; border-radius: 8px; background: #f7f9f6; }
.lead-context-grid span, .lead-followups-heading span { color: #94a096; font-size: 10px; }
.lead-context-grid strong { overflow: hidden; color: #4a6051; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.lead-followups-heading { display: flex; align-items: center; justify-content: space-between; padding-top: 2px; }
.lead-followups-heading strong { color: #4f6555; font-size: 12px; }
.lead-followup-list { margin: 0; padding: 0; list-style: none; }
.lead-followup-list li { display: grid; grid-template-columns: 85px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 10px 0; border-top: 1px solid #edf1ed; }
.lead-followup-list time { color: #87968a; font-size: 10px; }
.lead-followup-list li > div { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.lead-followup-list li strong { overflow: hidden; color: #53665a; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.lead-followup-list li small { color: #9aa59b; font-size: 10px; }
.lead-customer-link { margin-top: 3px; }
@media (max-width: 620px) { .lead-journey { padding: 13px; } .lead-context-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .lead-followup-list li { grid-template-columns: 68px minmax(0, 1fr); } .lead-followup-list li > :last-child { grid-column: 2; justify-self: start; } }
</style>
