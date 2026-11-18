<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Download, MoreFilled, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import PageHeading from '../components/PageHeading.vue'
import StatCard from '../components/StatCard.vue'
import { organizationApi, departmentPath } from '../api/organization'
import type { Department, Employee, Role } from '../data/organization'
import { formatDate, escapeCsv } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { useDelayedLoading } from '../utils/useDelayedLoading'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const employees = ref<Employee[]>([])
const totalCount = ref(0)
const summary = ref({ total: 0, active: 0, departments: 0, joined: 0 })
const departments = ref<Department[]>([])
const roles = ref<Role[]>([])
const loadIndicator = useDelayedLoading()
const loading = loadIndicator.visible
const search = ref('')
const departmentFilter = ref(typeof route.query.deptId === 'string' ? route.query.deptId : '')
const roleFilter = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const selected = ref<Employee[]>([])
const formRef = ref<FormInstance>()
const dialogOpen = ref(false)
const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<Employee | null>(null)
const formMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const form = reactive({ id: '', name: '', account: '', password: '', departmentId: '', position: '', roleIds: [] as string[], phone: '', email: '', status: '在职' as '在职' | '停用', hireDate: '', gender: '未填写', remark: '' })
const visibleEmployees = computed(() => employees.value)
const pageRows = computed(() => employees.value)
let detailRequest = 0
const batchDeactivatableCount = computed(() => selected.value.filter((item) => item.status === '在职' && !isAdmin(item)).length)
const rules: FormRules = {
  name: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  account: [{ required: true, message: '请输入登录账号', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_-]{3,32}$/, message: '账号为 3-32 位字母、数字、下划线或连字符', trigger: 'blur' }],
  password: [{ validator: (_rule, value: string, callback) => formMode.value === 'create' && !value ? callback(new Error('新建员工需要设置初始密码')) : value && value.length < 6 ? callback(new Error('密码至少需要 6 位')) : callback(), trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  roleIds: [{ type: 'array', required: true, message: '请至少选择一个角色', trigger: 'change' }],
  phone: [{ required: true, message: '请输入手机号码', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }],
}

let searchTimer: ReturnType<typeof setTimeout> | undefined
async function load(): Promise<void> {
  const request = loadIndicator.start()
  try {
    const [pageResult, summaryResult, departmentResult, roleResult] = await Promise.all([
      organizationApi.employeePage({ pageNum: page.value, pageSize: pageSize.value, keyword: search.value, departmentId: departmentFilter.value, roleId: roleFilter.value, status: statusFilter.value ? statusFilter.value as Employee['status'] : undefined }),
      organizationApi.employeeSummary(), organizationApi.departments(), organizationApi.roles(),
    ])
    if (!loadIndicator.isCurrent(request)) return
    employees.value = pageResult.rows
    totalCount.value = pageResult.total
    summary.value = { total: summaryResult.total, active: summaryResult.active, departments: summaryResult.departments, joined: summaryResult.newThisMonth }
    departments.value = departmentResult
    roles.value = roleResult
    selected.value = []
  }
  catch (error) { if (loadIndicator.isCurrent(request)) ElMessage.error(error instanceof Error ? error.message : '员工数据载入失败') }
  finally { loadIndicator.finish(request) }
}
function scheduleLoad(): void {
  page.value = 1
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { void load() }, 250)
}
function resetFilters(): void {
  search.value = ''
  roleFilter.value = ''
  statusFilter.value = ''
  page.value = 1
  if (route.query.deptId !== undefined) void router.replace({ query: { ...route.query, deptId: undefined } })
  else { departmentFilter.value = ''; void load() }
}
function pageSizeChanged(): void { page.value = 1; void load() }
function roleNames(employee: Employee): string { return employee.roleIds.map((id) => roles.value.find((role) => role.id === id)?.name).filter(Boolean).join('、') || '未分配角色' }
function showCreate(): void {
  formMode.value = 'create'
  Object.assign(form, { id: '', name: '', account: '', password: '', departmentId: departments.value[0]?.id ?? '', position: '', roleIds: [], phone: '', email: '', status: '在职', hireDate: new Date().toISOString().slice(0, 10), gender: '未填写', remark: '' })
  dialogOpen.value = true
}
function showEdit(employee: Employee): void {
  formMode.value = 'edit'
  Object.assign(form, { ...employee, password: '', roleIds: [...employee.roleIds] })
  dialogOpen.value = true
}
async function showDetail(employee: Employee): Promise<void> {
  const request = ++detailRequest
  detail.value = employee
  drawerOpen.value = true
  detailLoading.value = true
  try {
    const result = await organizationApi.employeeDetail(employee.id)
    if (request === detailRequest && drawerOpen.value) detail.value = result
  } catch (error) {
    if (request === detailRequest && drawerOpen.value) ElMessage.error(error instanceof Error ? error.message : '员工档案读取失败')
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}
async function save(): Promise<void> {
  const requiredPermission = formMode.value === 'create' ? 'system:user:add' : 'system:user:edit'
  if (!auth.can(requiredPermission)) { ElMessage.error('当前账号没有员工维护权限'); return }
  await formRef.value?.validate()
  saving.value = true
  try {
    const input = { name: form.name, account: form.account, password: form.password, departmentId: form.departmentId, position: form.position, roleIds: [...form.roleIds], phone: form.phone, email: form.email, status: form.status, hireDate: form.hireDate, gender: form.gender, remark: form.remark, lastLoginIp: formMode.value === 'edit' ? detail.value?.lastLoginIp ?? '' : '—' }
    if (formMode.value === 'create') await organizationApi.createEmployee(input)
    else await organizationApi.updateEmployee(form.id, input)
    ElMessage.success(formMode.value === 'create' ? '员工已创建' : '员工档案已更新')
    dialogOpen.value = false
    if (formMode.value === 'edit' && auth.user?.id === form.id) await auth.refreshProfile()
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存员工失败') }
  finally { saving.value = false }
}
async function setStatus(rows: Employee[], status: '在职' | '停用'): Promise<void> {
  if (!auth.can('system:user:edit')) { ElMessage.error('当前账号没有编辑员工状态的权限'); return }
  const protectedAdmins = status === '停用' ? rows.filter((row) => row.status !== status && isAdmin(row)) : []
  const targets = rows.filter((row) => row.status !== status && !(status === '停用' && isAdmin(row)))
  if (protectedAdmins.length) ElMessage.warning(`已排除 ${protectedAdmins.length} 位超级管理员；超级管理员不能通过批量操作停用。`)
  if (!targets.length) { ElMessage.info('所选员工已是该状态'); return }
  try {
    await ElMessageBox.confirm(`${status === '停用' ? '停用后员工将无法登录。' : '启用后员工可以重新登录。'}确认${status}${targets.length}位员工？`, `${status}员工`, { type: status === '停用' ? 'warning' : 'info', confirmButtonText: `确认${status}`, cancelButtonText: '取消' })
    await organizationApi.setEmployeeStatus(targets.map((item) => item.id), status)
    ElMessage.success(`已${status}${targets.length}位员工`)
    selected.value = []
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '员工状态更新失败')
  }
}
function exportCsv(): void {
  if (!auth.can('system:user:export')) { ElMessage.error('当前账号没有员工信息导出权限'); return }
  const rows = visibleEmployees.value
  const columns = ['姓名', '登录账号', '所属部门', '职位', '角色', '联系电话', '邮箱', '状态', '入职日期']
  const data = rows.map((item) => [item.name, item.account, departmentPath(item.departmentId, departments.value), item.position, roleNames(item), item.phone, item.email, item.status, formatDate(item.hireDate)])
  const blob = new Blob(['\ufeff', [columns.map(escapeCsv).join(','), ...data.map((line) => line.map(escapeCsv).join(','))].join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `员工档案-${new Date().toISOString().slice(0, 10)}.csv`; a.click(); URL.revokeObjectURL(url)
}
function deptOptionLabel(dept: Department): string { return departmentPath(dept.id, departments.value) }
function isAdmin(employee: Employee): boolean { return employee.roleIds.some((roleId) => roles.value.find((role) => role.id === roleId)?.key === 'admin') }

watch(() => route.query.deptId, (value) => {
  const next = typeof value === 'string' ? value : ''
  if (next === departmentFilter.value) return
  departmentFilter.value = next
  page.value = 1
  void load()
})
watch(drawerOpen, (open) => { if (!open) { detailRequest += 1; detailLoading.value = false } })
onMounted(load)
onBeforeUnmount(() => { if (searchTimer) clearTimeout(searchTimer) })
</script>

<template>
  <div class="org-page">
    <PageHeading title="员工管理" eyebrow="PEOPLE OPERATIONS" description="管理团队成员、组织归属与系统访问角色。">
      <template #actions><el-button v-permission="'system:user:export'" class="button-secondary" :icon="Download" @click="exportCsv">导出当前页</el-button><el-button v-permission="'system:user:add'" type="primary" :icon="Plus" @click="showCreate">新建员工</el-button></template>
    </PageHeading>
    <section class="stats-grid org-stats">
      <StatCard label="员工总数" :value="summary.total" note="已录入的组织成员" />
      <StatCard label="在职员工" :value="summary.active" note="正常参与业务协作" tone="mint" />
      <StatCard label="组织部门" :value="summary.departments" note="当前启用的部门" tone="gold" />
      <StatCard label="本月新成员" :value="summary.joined" note="本月加入团队" tone="rose" />
    </section>
    <section class="panel data-panel">
      <div class="data-toolbar">
        <div class="data-toolbar__top">
          <el-input v-model="search" class="search-input" :prefix-icon="Search" clearable placeholder="搜索姓名、账号、邮箱或电话" @keyup.enter="scheduleLoad" @clear="scheduleLoad" />
          <div class="data-toolbar__actions">
            <el-button class="button-secondary" :icon="Search" @click="scheduleLoad">查询</el-button>
            <el-button v-if="selected.length && auth.can('system:user:edit')" class="button-secondary" :disabled="!batchDeactivatableCount" @click="setStatus(selected, '停用')">批量停用（{{ batchDeactivatableCount }}）</el-button>
          </div>
        </div>
        <div class="data-toolbar__filters">
          <div class="filter-item"><span class="filter-label">所属部门</span><el-select v-model="departmentFilter" class="filter-select" clearable placeholder="请选择" @change="scheduleLoad"><el-option v-for="dept in departments" :key="dept.id" :label="deptOptionLabel(dept)" :value="dept.id" /></el-select></div>
          <div class="filter-item"><span class="filter-label">员工角色</span><el-select v-model="roleFilter" class="filter-select" clearable placeholder="请选择" @change="scheduleLoad"><el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" /></el-select></div>
          <div class="filter-item"><span class="filter-label">状态</span><el-select v-model="statusFilter" class="filter-select filter-select--short" clearable placeholder="请选择" @change="scheduleLoad"><el-option label="在职" value="在职" /><el-option label="停用" value="停用" /></el-select></div>
          <el-button text class="reset-button" @click="resetFilters">重置</el-button>
        </div>
      </div>
      <div class="table-wrap">
        <el-table v-loading="loading" :data="pageRows" row-key="id" class="crm-table" @selection-change="selected = $event">
          <el-table-column type="selection" width="44" />
          <el-table-column label="员工" min-width="190"><template #default="{ row }"><div class="employee-cell"><span class="employee-avatar">{{ row.name.slice(0, 1) }}</span><span><strong>{{ row.name }}</strong><small>{{ row.account }}</small></span></div></template></el-table-column>
          <el-table-column label="所属部门" min-width="190"><template #default="{ row }"><span class="dept-path-cell">{{ departmentPath(row.departmentId, departments) }}</span></template></el-table-column>
          <el-table-column prop="position" label="职位" min-width="120" />
          <el-table-column label="角色" min-width="160"><template #default="{ row }"><div class="role-tags"><el-tag v-for="name in roleNames(row).split('、')" :key="name" size="small" effect="plain" round>{{ name }}</el-tag></div></template></el-table-column>
          <el-table-column label="联系方式" min-width="180"><template #default="{ row }"><div class="contact-cell"><span>{{ row.phone }}</span><small>{{ row.email || '—' }}</small></div></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === '在职' ? 'success' : 'info'" effect="light" round>{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="入职日期" width="120"><template #default="{ row }">{{ formatDate(row.hireDate) }}</template></el-table-column>
          <el-table-column label="操作" fixed="right" width="208"><template #default="{ row }"><div class="row-actions"><el-button link type="primary" @click="showDetail(row)">档案</el-button><el-button v-permission="'system:user:edit'" link type="primary" @click="detail = row; showEdit(row)">编辑</el-button><el-dropdown v-if="auth.can('system:user:edit')" trigger="click"><el-button link><el-icon><MoreFilled /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item :disabled="row.status === '在职'" @click="setStatus([row], '在职')">启用员工</el-dropdown-item><el-dropdown-item :disabled="row.status === '停用' || isAdmin(row)" class="danger-menu-item" @click="setStatus([row], '停用')">停用员工</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
          <template #empty><div class="table-empty"><strong>没有找到员工</strong><span>请调整筛选条件或创建新成员。</span></div></template>
        </el-table>
      </div>
      <div class="table-footer"><span>共 {{ totalCount }} 位员工</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="totalCount" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next, jumper" @current-change="load" @size-change="pageSizeChanged" /></div>
    </section>

    <el-dialog v-model="dialogOpen" :title="formMode === 'create' ? '新建员工' : '编辑员工档案'" width="min(760px, 94vw)" destroy-on-close>
      <div class="dialog-intro">为团队成员配置基本信息、组织归属及系统访问角色。</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="record-form record-form--three">
        <el-form-item label="真实姓名" prop="name"><el-input v-model="form.name" placeholder="例如：林予安" /></el-form-item>
        <el-form-item label="登录账号" prop="account"><el-input v-model="form.account" :disabled="formMode === 'edit'" placeholder="3-32 位字符" /></el-form-item>
        <el-form-item :label="formMode === 'create' ? '初始密码' : '修改密码（可留空）'" prop="password"><el-input v-model="form.password" type="password" show-password :placeholder="formMode === 'create' ? '至少 6 位' : '留空表示不修改'" /></el-form-item>
        <el-form-item label="所属部门" prop="departmentId"><el-select v-model="form.departmentId" filterable class="w-full" placeholder="请选择部门"><el-option v-for="dept in departments" :key="dept.id" :label="deptOptionLabel(dept)" :value="dept.id" /></el-select></el-form-item>
        <el-form-item label="岗位" prop="position"><el-input v-model="form.position" placeholder="例如：客户经理" /></el-form-item>
        <el-form-item label="角色（可多选）" prop="roleIds"><el-select v-model="form.roleIds" multiple collapse-tags collapse-tags-tooltip filterable class="w-full" placeholder="请选择启用角色"><el-option v-for="role in roles.filter((item) => item.status === '启用')" :key="role.id" :label="role.name" :value="role.id" /></el-select></el-form-item>
        <el-form-item label="性别" prop="gender"><el-select v-model="form.gender" class="w-full"><el-option label="未填写" value="未填写" /><el-option label="女" value="女" /><el-option label="男" value="男" /></el-select></el-form-item>
        <el-form-item label="入职日期" prop="hireDate"><el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
        <el-form-item label="电子邮箱" prop="email"><el-input v-model="form.email" placeholder="name@company.com" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-select v-model="form.status" class="w-full"><el-option label="在职" value="在职" /><el-option label="停用" value="停用" /></el-select></el-form-item>
        <el-form-item label="备注" prop="remark" class="form-item--wide"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button class="button-secondary" @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存员工</el-button></template>
    </el-dialog>

    <el-drawer v-model="drawerOpen" v-loading="detailLoading" title="员工档案" size="min(520px, 100vw)">
      <template v-if="detail"><div class="employee-profile-head"><span class="employee-avatar employee-avatar--large">{{ detail.name.slice(0, 1) }}</span><div><h2>{{ detail.name }}</h2><span>{{ detail.position }} · {{ departmentPath(detail.departmentId, departments) }}</span></div><el-tag :type="detail.status === '在职' ? 'success' : 'info'" round>{{ detail.status }}</el-tag></div>
        <div class="drawer-section"><div class="drawer-section-title">组织与访问</div><div class="profile-info-grid"><div><span>登录账号</span><strong>{{ detail.account }}</strong></div><div><span>角色</span><strong>{{ roleNames(detail) }}</strong></div><div><span>入职日期</span><strong>{{ formatDate(detail.hireDate) }}</strong></div><div><span>最近登录 IP</span><strong>{{ detail.lastLoginIp || '—' }}</strong></div></div></div>
        <div class="drawer-section"><div class="drawer-section-title">联系信息</div><div class="profile-info-grid"><div><span>手机号码</span><strong>{{ detail.phone }}</strong></div><div><span>电子邮箱</span><strong>{{ detail.email || '—' }}</strong></div><div><span>性别</span><strong>{{ detail.gender || '—' }}</strong></div></div></div>
        <div class="drawer-section"><div class="drawer-section-title">备注</div><p class="profile-remark">{{ detail.remark || '暂无备注信息' }}</p></div>
        <div class="detail-actions"><el-button v-permission="'system:user:edit'" type="primary" @click="drawerOpen = false; showEdit(detail)">编辑档案</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>
