<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowRight, MoreFilled, OfficeBuilding, Plus, Search } from '@element-plus/icons-vue'
import PageHeading from '../components/PageHeading.vue'
import StatCard from '../components/StatCard.vue'
import { organizationApi, departmentDescendantIds, departmentPath, departmentTree } from '../api/organization'
import type { Department, Employee, Role } from '../data/organization'
import { useAuthStore } from '../stores/auth'
import { useDelayedLoading } from '../utils/useDelayedLoading'

const auth = useAuthStore()
const router = useRouter()
const departments = ref<Department[]>([])
const employees = ref<Employee[]>([])
const roles = ref<Role[]>([])
const loadIndicator = useDelayedLoading()
const loading = loadIndicator.visible
const loadError = ref('')
const hasLoaded = ref(false)
const treeRef = ref<{ filter: (value: string) => void }>()
const search = ref('')
const selected = ref<Department | null>(null)
const memberPage = ref(1)
const memberPageSize = ref(5)
const dialogOpen = ref(false)
const saving = ref(false)
const mode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const form = reactive({ id: '', name: '', parentId: '', code: '', leaderId: '', sort: 1, phone: '', email: '', status: '启用' as '启用' | '停用' })
const rules: FormRules = { name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }], code: [{ required: true, message: '请输入部门编号', trigger: 'blur' }], sort: [{ required: true, message: '请设置排序值', trigger: 'blur' }] }
const treeData = computed(() => departmentTree(departments.value, employees.value))
const directMembers = computed(() => employees.value.filter((employee) => employee.departmentId === selected.value?.id))
const memberPageRows = computed(() => directMembers.value.slice((memberPage.value - 1) * memberPageSize.value, memberPage.value * memberPageSize.value))
const enabledCount = computed(() => departments.value.filter((dept) => dept.status === '启用').length)
const depth = computed(() => Math.max(0, ...departments.value.map((dept) => departmentPath(dept.id, departments.value).split(' / ').length)))
const totalActiveEmployees = computed(() => employees.value.filter((employee) => employee.status === '在职').length)
const visibleParentOptions = computed(() => {
  if (mode.value !== 'edit' || !form.id) return departments.value
  const excluded = new Set(departmentDescendantIds(form.id, departments.value))
  return departments.value.filter((dept) => !excluded.has(dept.id))
})

async function load(): Promise<void> {
  const request = loadIndicator.start()
  try {
    const [depts, people, roleData] = await Promise.all([organizationApi.departments(), organizationApi.employees(), organizationApi.roles()])
    if (!loadIndicator.isCurrent(request)) return
    departments.value = depts
    employees.value = people
    roles.value = roleData
    hasLoaded.value = true
    loadError.value = ''
    if (!selected.value || !depts.some((item) => item.id === selected.value?.id)) selected.value = depts[0] ?? null
  } catch (error) {
    if (loadIndicator.isCurrent(request)) {
      loadError.value = error instanceof Error ? error.message : '部门数据载入失败'
      ElMessage.error(loadError.value)
    }
  }
  finally { loadIndicator.finish(request) }
}
function filterTree(): void { treeRef.value?.filter(search.value) }
function filterNode(value: string, data: unknown): boolean { const node = data as Department & { path?: string }; return !value || node.name.includes(value) || String(node.path ?? '').includes(value) }
function showCreate(parent?: Department): void {
  mode.value = 'create'
  Object.assign(form, { id: '', name: '', parentId: parent?.id ?? '', code: '', leaderId: '', sort: 1, phone: '', email: '', status: '启用' })
  dialogOpen.value = true
}
function showEdit(dept: Department): void {
  mode.value = 'edit'
  Object.assign(form, { ...dept, parentId: dept.parentId ?? '' })
  dialogOpen.value = true
}
async function save(): Promise<void> {
  await formRef.value?.validate()
  saving.value = true
  try {
    const input = { name: form.name, parentId: form.parentId || null, code: form.code, leaderId: form.leaderId, sort: Number(form.sort), phone: form.phone, email: form.email, status: form.status }
    if (mode.value === 'create') await organizationApi.createDepartment(input)
    else await organizationApi.updateDepartment(form.id, input)
    ElMessage.success(mode.value === 'create' ? '部门已创建' : '部门信息已更新')
    dialogOpen.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存部门失败') }
  finally { saving.value = false }
}
async function toggleStatus(dept: Department): Promise<void> {
  const status = dept.status === '启用' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`${status === '停用' ? '停用部门后其成员可能无法继续访问。' : '启用部门后成员可恢复使用。'}确认${status}「${dept.name}」？`, `${status}部门`, { type: 'warning' })
    await organizationApi.updateDepartment(dept.id, { status })
    ElMessage.success(`已${status}部门`)
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : String(error)) }
}
async function removeDept(dept: Department): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${dept.name}」？有下级部门或直属员工时无法删除。`, '删除部门', { type: 'warning', confirmButtonText: '确认删除' })
    await organizationApi.deleteDepartment(dept.id)
    ElMessage.success('部门已删除')
    selected.value = null
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : String(error)) }
}
function leaderName(dept: Department): string { return employees.value.find((person) => person.id === dept.leaderId)?.name ?? '暂未设置' }
function roleNames(person: Employee): string { return person.roleIds.map((id) => roles.value.find((role) => role.id === id)?.name).filter(Boolean).join('、') || '—' }
function nodeLabel(data: unknown): string { return String((data as Department).name ?? '') }
function nodeContent(_: unknown, { data }: { data: Department & { employeeCount?: number; path?: string } }): string { return `${data.employeeCount ?? 0} 人` }

watch(() => selected.value?.id, () => { memberPage.value = 1 })
onMounted(load)
</script>

<template>
  <div class="org-page">
    <PageHeading title="部门管理" eyebrow="ORGANIZATION DESIGN" description="构建清楚的汇报关系，让每位成员都有合适的协作位置。"><template #actions><el-button v-permission="'system:dept:add'" type="primary" :icon="Plus" @click="showCreate()">新建部门</el-button></template></PageHeading>
    <section class="stats-grid org-stats"><StatCard label="组织部门" :value="departments.length" note="完整组织层级" /><StatCard label="启用部门" :value="enabledCount" note="当前正常运作" tone="mint" /><StatCard label="组织层级" :value="depth" note="从总部向下延伸" tone="gold" /><StatCard label="在册成员" :value="totalActiveEmployees" note="在职员工总数" tone="rose" /></section>
    <section class="department-layout">
      <article class="panel department-tree-panel">
        <div class="department-panel-head"><div><div class="panel-kicker">ORG STRUCTURE</div><h2>组织架构</h2><p>点击部门查看直属成员与部门详情</p></div><el-button v-permission="'system:dept:add'" class="icon-button" :icon="Plus" circle aria-label="新增部门" @click="showCreate(selected ?? undefined)" /></div>
        <el-input v-model="search" class="tree-search" :prefix-icon="Search" clearable placeholder="搜索部门名称" @input="filterTree" />
        <div v-loading="loading" class="tree-area"><el-tree v-if="departments.length" ref="treeRef" :data="treeData" node-key="id" :props="{ label: nodeLabel, children: 'children' }" default-expand-all highlight-current :filter-node-method="filterNode" @node-click="(data: Department) => selected = data"><template #default="{ data }"><div class="dept-tree-node"><span class="dept-tree-icon"><el-icon><OfficeBuilding /></el-icon></span><span class="dept-tree-name">{{ data.name }}</span><span class="dept-tree-count">{{ data.employeeCount }} 人</span></div></template></el-tree><div v-if="loadError" class="department-load-error" role="alert"><span>{{ loadError }}</span><el-button link type="primary" @click="load">重试</el-button></div><el-empty v-else-if="hasLoaded && !departments.length" description="暂无部门" :image-size="60" /></div>
        <div class="tree-footnote"><span class="status-pulse"></span>组织树自动展开至当前所有层级</div>
      </article>
      <article v-if="selected" class="panel department-detail-panel">
        <div class="department-detail-head"><div class="department-detail-copy"><div class="panel-kicker">DEPARTMENT PROFILE</div><div class="department-title-line"><h2>{{ selected.name }}</h2><el-tag :type="selected.status === '启用' ? 'success' : 'info'" round effect="light">{{ selected.status }}</el-tag></div><div class="dept-breadcrumb">{{ departmentPath(selected.id, departments) }}</div></div><div class="department-detail-summary"><div><span>部门编号</span><strong>{{ selected.code || '—' }}</strong></div><div><span>负责人</span><strong>{{ leaderName(selected) }}</strong></div><div><span>部门邮箱</span><strong>{{ selected.email || '—' }}</strong></div><div><span>联系电话</span><strong>{{ selected.phone || '—' }}</strong></div></div><el-dropdown v-if="auth.can('system:dept:add') || auth.can('system:dept:edit') || auth.can('system:dept:remove')" trigger="click"><el-button class="icon-button" circle aria-label="部门操作"><el-icon><MoreFilled /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-permission="'system:dept:add'" @click="showCreate(selected!)">新增下级部门</el-dropdown-item><el-dropdown-item v-permission="'system:dept:edit'" @click="showEdit(selected!)">编辑部门</el-dropdown-item><el-dropdown-item v-permission="'system:dept:edit'" @click="toggleStatus(selected!)">{{ selected.status === '启用' ? '停用部门' : '启用部门' }}</el-dropdown-item><el-dropdown-item v-permission="'system:dept:remove'" divided class="danger-menu-item" @click="removeDept(selected!)">删除部门</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
        <div class="section-divider"><div><div class="panel-kicker">DEPARTMENT MEMBERS</div><h3>部门员工</h3></div><div class="member-heading-actions"><el-button v-if="auth.can('system:user:list')" link type="primary" @click="router.push({ path: '/employees', query: { deptId: selected?.id } })">员工管理<el-icon><ArrowRight /></el-icon></el-button></div></div>
        <div class="member-table-scroll"><el-table :data="memberPageRows" class="crm-table member-table" size="small">
          <el-table-column label="员工" min-width="150"><template #default="{ row }"><div class="employee-cell"><span class="employee-avatar employee-avatar--small">{{ row.name.slice(0, 1) }}</span><span><strong>{{ row.name }}</strong><small>{{ row.account }}</small></span></div></template></el-table-column>
          <el-table-column prop="position" label="职位" min-width="130" />
          <el-table-column label="角色" min-width="140"><template #default="{ row }">{{ roleNames(row) }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === '在职' ? 'success' : 'info'" size="small" round>{{ row.status }}</el-tag></template></el-table-column>
        </el-table></div>
        <div v-if="directMembers.length" class="department-member-footer"><span>共 {{ directMembers.length }} 位员工</span><el-pagination v-model:current-page="memberPage" v-model:page-size="memberPageSize" :page-sizes="[5, 10, 20]" :total="directMembers.length" layout="sizes, prev, pager, next, jumper" /></div>
        <el-empty v-if="directMembers.length === 0" description="该部门暂无直属成员" :image-size="70" />
      </article>
    </section>
    <el-dialog v-model="dialogOpen" :title="mode === 'create' ? '新建部门' : '编辑部门'" width="min(620px, 94vw)" destroy-on-close>
      <div class="dialog-intro">可创建任意层级结构；移动部门时不能选择自身或下级部门作为上级。</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="record-form record-form--two">
        <el-form-item label="部门名称" prop="name"><el-input v-model="form.name" placeholder="例如：华东销售部" /></el-form-item>
        <el-form-item label="部门编号" prop="code"><el-input v-model="form.code" placeholder="例如：EAST" /></el-form-item>
        <el-form-item label="上级部门" prop="parentId"><el-select v-model="form.parentId" clearable filterable class="w-full" placeholder="无上级（顶级部门）"><el-option v-for="dept in visibleParentOptions" :key="dept.id" :label="departmentPath(dept.id, departments)" :value="dept.id" /></el-select></el-form-item>
        <el-form-item label="部门负责人" prop="leaderId"><el-select v-model="form.leaderId" clearable filterable class="w-full" placeholder="请选择负责人"><el-option v-for="person in employees.filter((item) => item.status === '在职')" :key="person.id" :label="`${person.name} · ${departmentPath(person.departmentId, departments)}`" :value="person.id" /></el-select></el-form-item>
        <el-form-item label="显示顺序" prop="sort"><el-input-number v-model="form.sort" :min="0" :max="9999" class="w-full" /></el-form-item>
        <el-form-item label="状态" prop="status"><el-select v-model="form.status" class="w-full"><el-option label="启用" value="启用" /><el-option label="停用" value="停用" /></el-select></el-form-item>
        <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="部门邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
      </el-form>
      <template #footer><el-button class="button-secondary" @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存部门</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.member-table-scroll { min-width: 0; max-width: 100%; overflow-x: auto; }
.member-table-scroll :deep(.el-table) { min-width: 590px; }
.department-member-footer { display: flex; min-height: 52px; align-items: center; justify-content: space-between; gap: 12px; margin-top: auto; padding-top: 10px; color: #98a39a; font-size: 12px; }
.department-member-footer > span { white-space: nowrap; }
.department-member-footer :deep(.el-pagination) { --el-pagination-font-size: 12px; }
.department-load-error { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 18px 8px; color: #b65b4f; font-size: 13px; }
@media (max-width: 640px) {
  .department-member-footer { align-items: flex-start; flex-direction: column; }
  .department-member-footer :deep(.el-pagination) { flex-wrap: wrap; }
}
</style>
