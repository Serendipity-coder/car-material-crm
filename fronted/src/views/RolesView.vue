<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules } from 'element-plus'
import { ArrowRight, Check, Key, Plus, Refresh, Search, Setting } from '@element-plus/icons-vue'
import PageHeading from '../components/PageHeading.vue'
import StatCard from '../components/StatCard.vue'
import { organizationApi, type CategoryNode, type RoleCategoryBinding, type RoleDataScopeItem } from '../api/organization'
import type { PermissionNode, Role } from '../data/organization'
import PermissionCatalogManager from '../components/PermissionCatalogManager.vue'
import { useAuthStore } from '../stores/auth'
import { useDelayedLoading } from '../utils/useDelayedLoading'

const auth = useAuthStore()
const roles = ref<Role[]>([])
const permissionTree = ref<PermissionNode[]>([])
const loadIndicator = useDelayedLoading()
const loading = loadIndicator.visible
const permissionLoading = ref(false)
const rolesLoaded = ref(false)
const roleLoadError = ref('')
const permissionTreeLoaded = ref(false)
const permissionLoadError = ref('')
const keyword = ref('')
const selectedId = ref('')
const createDialogOpen = ref(false)
const assignDialogOpen = ref(false)
const permissionCatalogOpen = ref(false)
const saving = ref(false)
const savingPermissions = ref(false)
const formRef = ref<FormInstance>()
const treeRef = ref<InstanceType<typeof ElTree>>()
const formMode = ref<'create' | 'edit'>('create')
const assigningRole = ref<Role | null>(null)
const form = reactive({ id: '', name: '', key: '', sort: 0, status: '启用' as '启用' | '停用', remark: '' })
const formRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  key: [{ required: true, message: '请输入角色标识', trigger: 'blur' }, { pattern: /^[a-z][a-z0-9_-]*$/, message: '以小写字母开头，可包含字母、数字、下划线和连字符', trigger: 'blur' }],
  sort: [{ required: true, message: '请设置排序值', trigger: 'change' }],
}

const filteredRoles = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  return roles.value
    .filter((role) => !query || [role.name, role.key, role.remark].some((value) => value.toLocaleLowerCase().includes(query)))
    .sort((a, b) => a.sort - b.sort || a.name.localeCompare(b.name, 'zh-CN'))
})
const selectedRole = computed(() => filteredRoles.value.find((role) => role.id === selectedId.value) ?? filteredRoles.value[0] ?? null)
interface PermissionPreviewEntry { node: PermissionNode; depth: number; directlyGranted: boolean; descendantGranted: boolean }
function permissionPreviewEntries(role: Role | null, nodes: PermissionNode[], depth = 0, output: PermissionPreviewEntry[] = []): PermissionPreviewEntry[] {
  if (!role) return output
  for (const node of nodes) {
    const directlyGranted = role.key === 'admin' || role.menuIds.includes(node.id)
    const descendantGranted = (node.children ?? []).some((child) => role.key === 'admin' || role.menuIds.includes(child.id) || permissionPreviewEntries(role, [child], depth + 1, []).some((entry) => entry.directlyGranted || entry.descendantGranted))
    output.push({ node, depth, directlyGranted, descendantGranted })
    if (node.children?.length) permissionPreviewEntries(role, node.children, depth + 1, output)
  }
  return output
}
const permissionPreview = computed(() => permissionPreviewEntries(selectedRole.value, permissionTree.value))
const selectedVisibleRights = computed(() => permissionPreview.value
  .filter((entry) => entry.directlyGranted || entry.descendantGranted)
  .filter((entry) => entry.node.type !== 'F')
  .slice(0, 6))
const activeRoles = computed(() => roles.value.filter((role) => role.status === '启用').length)
const permissionCount = computed(() => flatten(permissionTree.value).length)
const associationCount = computed(() => roles.value.reduce((total, role) => total + role.memberCount, 0))
const allPermissionIds = computed(() => flatten(permissionTree.value).map((node) => node.id))
const isAssigningAdmin = computed(() => assigningRole.value?.key === 'admin')
const isAdministrator = computed(() => auth.permissions.includes('*') || Boolean(auth.user?.roleIds?.some((roleId) => roles.value.some((role) => role.id === roleId && role.key === 'admin'))))

function openPermissionCatalog(): void {
  if (!isAdministrator.value) { ElMessage.error('只有系统管理员可以查看权限目录'); return }
  permissionCatalogOpen.value = true
}
async function onPermissionCatalogSaved(): Promise<void> {
  await auth.refreshProfile()
  await load()
}


function flatten(nodes: PermissionNode[], output: PermissionNode[] = []): PermissionNode[] {
  for (const node of nodes) {
    output.push(node)
    if (node.children) flatten(node.children, output)
  }
  return output
}
function rolePermissionCount(role: Role): number { return role.key === 'admin' ? permissionCount.value : role.menuIds.length }
function permissionTypeLabel(type: string): string { return type === 'M' ? '目录' : type === 'C' ? '页面' : '操作' }
 function permissionTypeClass(type: string): string { return `permission-type--${type.toLowerCase()}` }
 function roleKeyLabel(key: string): string { return ({ admin: '系统管理员', sales: '销售专员', sales_manager: '销售经理', customer_success: '客户成功', auditor: '只读审计', service: '客服售后' } as Record<string, string>)[key] ?? '自定义角色' }

function openCreate(): void {
  if (!auth.can('system:role:add')) { ElMessage.error('当前账号没有新建角色权限'); return }
  formMode.value = 'create'
  Object.assign(form, { id: '', name: '', key: '', sort: roles.value.reduce((max, role) => Math.max(max, role.sort), 0) + 1, status: '启用', remark: '' })
  createDialogOpen.value = true
}
function openEdit(role: Role): void {
  if (!auth.can('system:role:edit')) { ElMessage.error('当前账号没有编辑角色权限'); return }
  formMode.value = 'edit'
  Object.assign(form, { id: role.id, name: role.name, key: role.key, sort: role.sort, status: role.status, remark: role.remark })
  createDialogOpen.value = true
}
async function saveRole(): Promise<void> {
  const permission = formMode.value === 'create' ? 'system:role:add' : 'system:role:edit'
  if (!auth.can(permission)) { ElMessage.error('当前账号没有角色维护权限'); return }
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { name: form.name.trim(), key: form.key.trim(), sort: Number(form.sort), status: form.status, remark: form.remark.trim() }
    const result = formMode.value === 'create' ? await organizationApi.createRole(data) : await organizationApi.updateRole(form.id, data)
    ElMessage.success(formMode.value === 'create' ? '角色已创建' : '角色信息已更新')
    createDialogOpen.value = false
    selectedId.value = result.id
    await auth.refreshProfile()
    await load()
    selectedId.value = result.id
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存角色失败') }
  finally { saving.value = false }
}

async function openAssign(role: Role | null = selectedRole.value): Promise<void> {
  if (!role) return
  if (!auth.can('system:role:assign')) { ElMessage.error('当前账号没有角色授权权限'); return }
  try {
    const complete = await organizationApi.role(role.id)
    roles.value = roles.value.map((item) => item.id === complete.id ? complete : item)
    assigningRole.value = complete
    selectedId.value = complete.id
    if (!permissionTreeLoaded.value || permissionLoadError.value) await loadPermissionTree()
    if (permissionLoadError.value) return
    assignDialogOpen.value = true
    await nextTick()
    treeRef.value?.setCheckedKeys(complete.key === 'admin' ? allPermissionIds.value : complete.menuIds, false)
  } catch (error) {
    permissionLoading.value = false
    ElMessage.error(error instanceof Error ? error.message : '角色授权详情读取失败')
  }
}
function setAllPermissions(value: boolean): void {
  if (!auth.can('system:role:assign')) return
  if (isAssigningAdmin.value && !value) { ElMessage.warning('超级管理员角色必须保留全部权限'); return }
  treeRef.value?.setCheckedKeys(value ? allPermissionIds.value : [], false)
}
async function saveAssignments(): Promise<void> {
  const target = assigningRole.value
  if (!target || !auth.can('system:role:assign')) { ElMessage.error('当前账号没有角色授权权限'); return }
  const checked = treeRef.value?.getCheckedKeys(false).map(String) ?? []
  const halfChecked = treeRef.value?.getHalfCheckedKeys().map(String) ?? []
  const ids = target.key === 'admin' ? allPermissionIds.value : [...new Set([...checked, ...halfChecked])]
  savingPermissions.value = true
  try {
    await organizationApi.assignMenus(target.id, ids)
    ElMessage.success(target.key === 'admin' ? '超级管理员完整权限已保留' : `已保存 ${ids.length} 个权限节点`)
    assignDialogOpen.value = false
    assigningRole.value = null
    await auth.refreshProfile()
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限保存失败') }
  finally { savingPermissions.value = false }
}

// ---------------- 产品品类授权 ----------------
interface RoleCategoryState { all: boolean; bindings: RoleCategoryBinding[] }
const categoryStates = ref<Record<string, RoleCategoryState>>({})
const categoryDialogOpen = ref(false)
const categoryDialogRole = ref<Role | null>(null)
const categoryTree = ref<CategoryNode[]>([])
const categoryTreeRef = ref<InstanceType<typeof ElTree>>()
const categoryTreeLoading = ref(false)
const categoryTreeError = ref('')
const categoryAll = ref(false)
const categoryIncludeChildren = ref(true)
const categoryCheckedCount = ref(0)
const categorySaving = ref(false)
const selectedCategoryState = computed(() => selectedRole.value ? categoryStates.value[selectedRole.value.id] ?? null : null)
const categorySummary = computed(() => {
  if (!selectedRole.value) return '—'
  const state = selectedCategoryState.value
  if (!state) return '读取中…'
  if (state.all) return '全部品类'
  return state.bindings.length ? `${state.bindings.length} 个品类` : '未授权'
})
watch(selectedRole, (role) => { if (role) { void loadCategoryState(role.id); void loadDataScopeState(role.id) } }, { immediate: true })
async function loadCategoryState(roleId: string): Promise<void> {
  if (categoryStates.value[roleId]) return
  try {
    const state = await organizationApi.roleCategories(roleId)
    categoryStates.value = { ...categoryStates.value, [roleId]: state }
  } catch { /* 概览读取失败不影响角色其他信息 */ }
}
async function openCategories(role: Role | null = selectedRole.value): Promise<void> {
  if (!role) return
  if (!auth.can('system:role:assign')) { ElMessage.error('当前账号没有角色授权权限'); return }
  categoryDialogRole.value = role
  categoryTreeError.value = ''
  categoryTreeLoading.value = true
  categoryDialogOpen.value = true
  try {
    if (!categoryTree.value.length) categoryTree.value = await organizationApi.roleCategoryTree()
    const state = await organizationApi.roleCategories(role.id)
    categoryStates.value = { ...categoryStates.value, [role.id]: state }
    categoryAll.value = state.all || role.key === 'admin'
    categoryIncludeChildren.value = state.bindings[0]?.includeChildren ?? true
    await nextTick()
    categoryTreeRef.value?.setCheckedKeys(state.bindings.map((binding) => binding.categoryId), false)
    categoryCheckedCount.value = categoryTreeRef.value?.getCheckedKeys(false).length ?? 0
  } catch (error) {
    categoryTreeError.value = error instanceof Error ? error.message : '品类授权读取失败'
  } finally { categoryTreeLoading.value = false }
}
function onCategoryCheck(): void { categoryCheckedCount.value = categoryTreeRef.value?.getCheckedKeys(false).length ?? 0 }
async function saveCategories(): Promise<void> {
  const role = categoryDialogRole.value
  if (!role) return
  if (!auth.can('system:role:assign')) { ElMessage.error('当前账号没有角色授权权限'); return }
  categorySaving.value = true
  try {
    const ids = categoryAll.value ? [] : (categoryTreeRef.value?.getCheckedKeys(false).map(String) ?? [])
    const state = await organizationApi.saveRoleCategories(role.id, { all: categoryAll.value || role.key === 'admin', categoryIds: ids, includeChildren: categoryIncludeChildren.value })
    categoryStates.value = { ...categoryStates.value, [role.id]: state }
    ElMessage.success(state.all ? '已授权全部产品品类' : `已授权 ${state.bindings.length} 个产品品类`)
    categoryDialogOpen.value = false
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '品类授权保存失败') }
  finally { categorySaving.value = false }
}

// ---------------- 数据范围 ----------------
const DATA_SCOPE_MODULES: Array<{ key: string; label: string }> = [
  { key: 'leads', label: '销售线索' },
  { key: 'customers', label: '客户档案' },
  { key: 'opportunities', label: '销售商机' },
  { key: 'quotes', label: '报价单' },
  { key: 'orders', label: '销售订单' },
  { key: 'reports', label: '数据统计' },
]
const DATA_SCOPE_OPTIONS = [
  { value: 'SELF', label: '仅本人负责' },
  { value: 'DEPT', label: '本部门' },
  { value: 'DEPT_AND_CHILD', label: '本部门及下级' },
  { value: 'ALL', label: '全企业' },
]
const dataScopeStates = ref<Record<string, RoleDataScopeItem[]>>({})
const dataScopeDialogOpen = ref(false)
const dataScopeDialogRole = ref<Role | null>(null)
const dataScopeDraft = ref<Record<string, string>>({})
const dataScopeLoading = ref(false)
const dataScopeSaving = ref(false)
const selectedDataScopeState = computed(() => selectedRole.value ? dataScopeStates.value[selectedRole.value.id] ?? null : null)
const dataScopeSummary = computed(() => {
  if (!selectedRole.value) return '—'
  const items = selectedDataScopeState.value
  if (!items) return '读取中…'
  if (!items.length) return '未配置'
  const types = new Set(items.map((item) => item.scopeType))
  if (types.size === 1) {
    const type = [...types][0]
    return DATA_SCOPE_OPTIONS.find((option) => option.value === type)?.label ?? type
  }
  return '按模块配置'
})
async function loadDataScopeState(roleId: string): Promise<void> {
  if (dataScopeStates.value[roleId]) return
  try {
    const items = await organizationApi.roleDataScopes(roleId)
    dataScopeStates.value = { ...dataScopeStates.value, [roleId]: items }
  } catch { /* 概览读取失败不影响其他信息 */ }
}
async function openDataScopes(role: Role | null = selectedRole.value): Promise<void> {
  if (!role) return
  if (!auth.can('system:role:assign')) { ElMessage.error('当前账号没有角色授权权限'); return }
  dataScopeDialogRole.value = role
  dataScopeDialogOpen.value = true
  dataScopeLoading.value = true
  try {
    const items = await organizationApi.roleDataScopes(role.id)
    dataScopeStates.value = { ...dataScopeStates.value, [role.id]: items }
    const draft: Record<string, string> = {}
    for (const module of DATA_SCOPE_MODULES) draft[module.key] = items.find((item) => item.moduleKey === module.key)?.scopeType ?? ''
    dataScopeDraft.value = draft
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据范围读取失败')
  } finally { dataScopeLoading.value = false }
}
async function saveDataScopes(): Promise<void> {
  const role = dataScopeDialogRole.value
  if (!role) return
  if (!auth.can('system:role:assign')) { ElMessage.error('当前账号没有角色授权权限'); return }
  dataScopeSaving.value = true
  try {
    const items = DATA_SCOPE_MODULES
      .filter((module) => dataScopeDraft.value[module.key])
      .map((module) => ({ moduleKey: module.key, scopeType: dataScopeDraft.value[module.key], scopeValue: null }))
    const saved = await organizationApi.saveRoleDataScopes(role.id, items)
    dataScopeStates.value = { ...dataScopeStates.value, [role.id]: saved }
    ElMessage.success('数据范围已保存')
    dataScopeDialogOpen.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据范围保存失败')
  } finally { dataScopeSaving.value = false }
}

async function roleAction(command: string, role: Role): Promise<void> {
  if (command === 'toggle') {
    if (!auth.can('system:role:edit')) { ElMessage.error('当前账号没有编辑角色权限'); return }
    if (role.key === 'admin') { ElMessage.warning('内置超级管理员不能停用'); return }
    const next = role.status === '启用' ? '停用' : '启用'
    try {
      if (next === '停用') {
        await ElMessageBox.confirm(`停用「${role.name}」角色？该角色关联 ${role.memberCount} 名员工，停用后他们可能失去由此角色提供的系统访问权限。`, '停用角色及成员影响', { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '取消' })
      }
      await organizationApi.setRoleStatus(role.id, next)
      ElMessage.success(`已${next}角色`)
      await auth.refreshProfile()
      await load()
    } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '角色状态更新失败') }
  } else if (command === 'delete') {
    if (!auth.can('system:role:remove')) { ElMessage.error('当前账号没有删除角色权限'); return }
    if (role.key === 'admin' || role.memberCount > 0) return
    try {
      await ElMessageBox.confirm(`删除「${role.name}」角色？已分配给员工的角色不能删除。`, '删除角色', { type: 'warning', confirmButtonText: '确认删除' })
      await organizationApi.deleteRole(role.id)
      ElMessage.success('角色已删除')
      if (selectedId.value === role.id) selectedId.value = ''
      await auth.refreshProfile()
      await load()
    } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : String(error)) }
  }
}
function applySearch(): void {
  if (!filteredRoles.value.some((role) => role.id === selectedId.value)) selectedId.value = filteredRoles.value[0]?.id ?? ''
}
function resetSearch(): void { keyword.value = ''; selectedId.value = roles.value[0]?.id ?? '' }
function selectRole(role: Role): void { selectedId.value = role.id }

let permissionTreeRequest = 0
async function loadPermissionTree(): Promise<void> {
  const request = ++permissionTreeRequest
  permissionLoading.value = true
  permissionLoadError.value = ''
  try {
    const tree = await organizationApi.menuTree()
    if (request !== permissionTreeRequest) return
    permissionTree.value = tree
    permissionTreeLoaded.value = true
  } catch (error) {
    if (request === permissionTreeRequest) permissionLoadError.value = error instanceof Error ? error.message : '权限树读取失败'
  } finally {
    if (request === permissionTreeRequest) permissionLoading.value = false
  }
}
async function load(): Promise<void> {
  const request = loadIndicator.start()
  roleLoadError.value = ''
  const roleLoad = organizationApi.roles().then((roleData) => {
    if (!loadIndicator.isCurrent(request)) return
    roles.value = roleData
    rolesLoaded.value = true
    if (!selectedId.value || !roleData.some((role) => role.id === selectedId.value)) selectedId.value = roleData[0]?.id ?? ''
  }).catch((error: unknown) => {
    if (loadIndicator.isCurrent(request)) {
      roleLoadError.value = error instanceof Error ? error.message : '角色列表读取失败'
      rolesLoaded.value = true
    }
  })
  const treeLoad = loadPermissionTree()
  await Promise.all([roleLoad, treeLoad])
  loadIndicator.finish(request)
}
function selectRoleFromKeyboard(role: Role, event: KeyboardEvent): void {
  if (event.key === 'Enter' || event.key === ' ') selectRole(role)
}
onMounted(load)
</script>

<template>
  <div class="org-page">
      <PageHeading title="角色权限" eyebrow="权限管理" description="将岗位职责转换为清晰的访问边界，按目录、页面与操作管理权限。">
      <template #actions><el-button v-if="isAdministrator" class="button-secondary" :icon="Key" @click="openPermissionCatalog">管理权限</el-button><el-button v-permission="'system:role:add'" type="primary" :icon="Plus" @click="openCreate">新建角色</el-button></template>
    </PageHeading>
    <section class="stats-grid org-stats">
      <StatCard label="角色总数" :value="roles.length" note="按职责划分访问范围" />
      <StatCard label="启用角色" :value="activeRoles" note="当前可分配角色" tone="mint" />
      <StatCard label="权限节点" :value="permissionCount" note="目录、页面与操作" tone="gold" />
      <StatCard label="角色关联记录" :value="associationCount" note="员工与角色的关联" tone="rose" />
    </section>

    <section class="roles-layout">
      <article class="panel role-list-panel role-picker-panel">
        <div class="role-picker-heading"><div><div class="panel-kicker">角色目录</div><h3>角色列表</h3><p>选择角色查看右侧权限详情</p></div></div>
        <div v-loading="loading" class="role-picker-body role-picker-body--list">
          <div class="roles-toolbar">
            <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索角色名称、标识或说明" @keyup.enter="applySearch" @clear="applySearch" />
            <el-button class="button-secondary" :icon="Search" @click="applySearch">查询</el-button>
            <el-button class="button-secondary" :icon="Refresh" @click="resetSearch">重置</el-button>
          </div>
          <div v-if="roleLoadError" class="role-load-error" role="alert"><span>{{ roleLoadError }}</span><el-button link type="primary" @click="load">重试角色列表</el-button></div>
          <el-table v-else :data="filteredRoles" row-key="id" highlight-current-row :current-row-key="selectedRole?.id" class="crm-table role-reference-table" @row-click="selectRole">
            <el-table-column label="角色" min-width="170"><template #default="{ row }"><button type="button" class="role-select-button" :aria-pressed="selectedRole?.id === row.id" @click.stop="selectRole(row)"><span class="role-person"><span class="role-symbol" :class="{ 'role-symbol--admin': row.key === 'admin' }"><el-icon><Key /></el-icon></span><span><b>{{ row.name }}<i v-if="row.key === 'admin'" class="builtin-pill">内置</i></b><small>{{ roleKeyLabel(row.key) }}</small></span></span></button></template></el-table-column>
            <el-table-column label="成员" width="76" align="center"><template #default="{ row }"><span class="member-count">{{ row.memberCount }} 人</span></template></el-table-column>
            <el-table-column label="状态" width="82" align="center"><template #default="{ row }"><el-tag size="small" :type="row.status === '启用' ? 'success' : 'info'" effect="light" round>{{ row.status === '启用' ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <template #empty><div class="table-empty"><strong>{{ roleLoadError ? '角色列表读取失败' : rolesLoaded ? '没有找到符合条件的角色' : '正在读取角色' }}</strong><span>{{ roleLoadError ? '请重试加载角色列表。' : '请调整关键词或新建角色。' }}</span></div></template>
          </el-table>
        </div>
        <div class="role-list-foot"><strong>安全提示</strong><p>内置超级管理员角色受保护，必须保留完整权限且不可停用或删除。</p></div>
      </article>

      <article v-if="selectedRole" class="panel role-profile-panel">
        <div class="role-profile-head">
          <span class="role-profile-icon"><el-icon><Key /></el-icon></span>
          <span class="role-profile-title"><div class="panel-kicker">角色详情</div><h2>{{ selectedRole.name }}</h2><span class="role-key-pill">{{ roleKeyLabel(selectedRole.key) }}</span></span>
          <el-tag :type="selectedRole.status === '启用' ? 'success' : 'info'" round>{{ selectedRole.status === '启用' ? '启用中' : '已停用' }}</el-tag>
        </div>
        <p class="role-description">{{ selectedRole.remark || '尚未填写角色说明。' }}</p>
        <div class="role-summary-grid">
          <div><span>授权节点</span><strong>{{ rolePermissionCount(selectedRole) }} <small>项</small></strong></div>
          <div><span>关联成员</span><strong>{{ selectedRole.memberCount }} <small>人</small></strong></div>
          <div><span>排序值</span><strong>{{ selectedRole.sort }}</strong></div>
        </div>
        <div class="role-category-summary">
          <div><span>产品品类</span><strong>{{ categorySummary }}</strong></div>
          <el-button v-permission="'system:role:assign'" link type="primary" @click="openCategories(selectedRole)">配置品类<el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <div class="role-category-summary">
          <div><span>数据范围</span><strong>{{ dataScopeSummary }}</strong></div>
          <el-button v-permission="'system:role:assign'" link type="primary" @click="openDataScopes(selectedRole)">配置数据范围<el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <div class="section-divider"><div><div class="panel-kicker">权限概览</div><h3>授权概览</h3></div><el-button v-permission="'system:role:assign'" link type="primary" @click="openAssign(selectedRole)">配置角色授权<el-icon><ArrowRight /></el-icon></el-button></div>
        <div v-if="permissionLoadError" class="role-load-error" role="alert"><span>{{ permissionLoadError }}</span><el-button link type="primary" @click="loadPermissionTree">重试权限树</el-button></div>
        <div v-else-if="permissionTreeLoaded && selectedVisibleRights.length" class="assigned-rights">
          <div class="rights-preview">
            <div v-for="entry in selectedVisibleRights" :key="entry.node.id" class="rights-preview-row"><span class="rights-dot"></span><span>{{ entry.node.name }}</span><small>{{ permissionTypeLabel(entry.node.type) }}</small></div>
            <div v-if="rolePermissionCount(selectedRole) > selectedVisibleRights.length" class="rights-overflow">还有 {{ rolePermissionCount(selectedRole) - selectedVisibleRights.length }} 项权限</div>
          </div>
        </div>
        <el-empty v-else-if="permissionTreeLoaded" description="尚未配置权限" :image-size="48" />
        <div class="role-actions">
          <el-button v-permission="'system:role:edit'" class="button-secondary" @click="openEdit(selectedRole)"><el-icon><Setting /></el-icon>编辑角色</el-button>
          <el-button v-permission="'system:role:edit'" class="button-secondary" :disabled="selectedRole.key === 'admin'" @click="roleAction('toggle', selectedRole)">{{ selectedRole.status === '启用' ? '停用角色' : '启用角色' }}</el-button>
          <el-button v-permission="'system:role:assign'" type="primary" @click="openAssign(selectedRole)"><el-icon><Check /></el-icon>配置权限</el-button>
          <el-button v-permission="'system:role:remove'" text type="danger" :disabled="selectedRole.key === 'admin' || selectedRole.memberCount > 0" @click="roleAction('delete', selectedRole)">删除角色</el-button>
        </div>
      </article>
      <article v-else class="panel role-profile-panel role-empty-panel"><el-empty description="没有符合条件的角色" /></article>
    </section>

    <el-dialog v-model="createDialogOpen" :title="formMode === 'create' ? '新建角色' : '编辑角色'" width="min(560px, 94vw)" destroy-on-close>
      <div class="dialog-intro">角色标识用于权限识别；编辑时不可更改标识，创建后可在员工档案中分配。</div>
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="record-form record-form--two">
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" placeholder="例如：销售经理" /></el-form-item>
        <el-form-item label="角色标识" prop="key"><el-input v-model="form.key" :disabled="formMode === 'edit'" placeholder="例如：sales_manager" /></el-form-item>
        <el-form-item label="排序值" prop="sort"><el-input-number v-model="form.sort" :min="0" :max="9999" class="w-full" controls-position="right" /></el-form-item>
        <el-form-item label="角色状态" prop="status"><el-select v-model="form.status" class="w-full" :disabled="form.key === 'admin'"><el-option label="启用" value="启用" /><el-option label="停用" value="停用" /></el-select></el-form-item>
        <el-form-item label="角色说明" prop="remark" class="form-item--wide"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="描述该角色适用的岗位和数据范围" /></el-form-item>
      </el-form>
      <template #footer><el-button class="button-secondary" @click="createDialogOpen = false">取消</el-button><el-button v-permission="formMode === 'create' ? 'system:role:add' : 'system:role:edit'" type="primary" :loading="saving" @click="saveRole">保存角色</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignDialogOpen" :title="`配置权限 · ${assigningRole?.name || ''}`" width="min(760px, 94vw)" destroy-on-close class="permission-dialog">
      <div class="permission-intro">
        <div class="permission-legend"><span><i class="legend-directory">M</i>目录</span><span><i class="legend-page">C</i>页面</span><span><i class="legend-action">F</i>操作</span></div>
        <el-text type="info" size="small">页面访问、按钮操作与跨部门数据范围分别授权</el-text>
        <el-button v-if="auth.can('system:role:assign')" text type="primary" :disabled="isAssigningAdmin" @click="setAllPermissions(true)">全选</el-button>
        <el-button v-if="auth.can('system:role:assign')" text :disabled="isAssigningAdmin" @click="setAllPermissions(false)">清空</el-button>
      </div>
      <el-alert v-if="isAssigningAdmin" title="超级管理员角色必须保留全部权限，包含跨部门全局数据范围；保存时已自动锁定完整权限集。" type="warning" :closable="false" show-icon />
      <div v-if="permissionLoadError" class="role-load-error" role="alert"><span>{{ permissionLoadError }}</span><el-button link type="primary" @click="loadPermissionTree">重试权限树</el-button></div><div v-else class="permission-tree-wrap"><el-tree ref="treeRef" v-loading="permissionLoading" :data="permissionTree" node-key="id" show-checkbox check-strictly default-expand-all :props="{ label: 'name', children: 'children' }" class="permission-tree" :expand-on-click-node="false"><template #default="{ data }"><div class="permission-node-label"><span class="permission-type" :class="permissionTypeClass(data.type)">{{ data.type === 'M' ? '目录' : data.type === 'C' ? '页面' : '操作' }}</span><span>{{ data.name }}</span></div></template></el-tree><el-empty v-if="!permissionLoading && permissionTreeLoaded && !permissionTree.length" description="权限树为空" :image-size="48" /></div>
      <template #footer><el-button class="button-secondary" @click="assignDialogOpen = false">取消</el-button><el-button v-permission="'system:role:assign'" type="primary" :disabled="Boolean(permissionLoadError) || !permissionTreeLoaded" :loading="savingPermissions" @click="saveAssignments">保存授权</el-button></template>
    </el-dialog>

    <el-dialog v-model="categoryDialogOpen" :title="`配置产品品类 · ${categoryDialogRole?.name || ''}`" width="min(720px, 94vw)" destroy-on-close class="permission-dialog">
      <div class="category-intro">
        <el-switch v-model="categoryAll" :disabled="categoryDialogRole?.key === 'admin'" active-text="全部品类" />
        <el-checkbox v-model="categoryIncludeChildren" :disabled="categoryAll">勾选父级时包含下级品类</el-checkbox>
        <el-text type="info" size="small">{{ categoryAll ? '该角色可查看与销售全部产品品类' : `已勾选 ${categoryCheckedCount} 个品类，未勾选时无法查看或销售任何产品` }}</el-text>
      </div>
      <el-alert type="info" :closable="false" show-icon title="产品列表、分类树、客户价格与报价/订单选品都按这里的授权范围过滤；管理员账号不受品类限制。" />
      <div v-if="categoryTreeError" class="role-load-error" role="alert"><span>{{ categoryTreeError }}</span><el-button link type="primary" @click="openCategories(categoryDialogRole)">重试</el-button></div>
      <div v-else v-loading="categoryTreeLoading" class="category-tree-wrap">
        <el-tree ref="categoryTreeRef" :data="categoryTree" node-key="id" show-checkbox check-strictly default-expand-all :props="{ label: 'name', children: 'children', disabled: () => categoryAll }" :expand-on-click-node="false" class="permission-tree" @check="onCategoryCheck" />
        <el-empty v-if="!categoryTreeLoading && !categoryTree.length" description="暂无产品分类" :image-size="48" />
      </div>
      <template #footer><el-button class="button-secondary" @click="categoryDialogOpen = false">取消</el-button><el-button v-permission="'system:role:assign'" type="primary" :disabled="Boolean(categoryTreeError)" :loading="categorySaving" @click="saveCategories">保存品类授权</el-button></template>
    </el-dialog>

    <el-dialog v-model="dataScopeDialogOpen" :title="`配置数据范围 · ${dataScopeDialogRole?.name || ''}`" width="min(640px, 94vw)" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon title="数据范围决定该角色能查看、编辑哪些业务记录；管理员账号不受限制，未配置的模块默认按「本部门及下级」处理。" />
      <div v-loading="dataScopeLoading" class="data-scope-list">
        <div v-for="module in DATA_SCOPE_MODULES" :key="module.key" class="data-scope-row">
          <span>{{ module.label }}</span>
          <el-select v-model="dataScopeDraft[module.key]" placeholder="未配置（默认本部门及下级）" clearable class="data-scope-select" :disabled="dataScopeDialogRole?.key === 'admin'">
            <el-option v-for="option in DATA_SCOPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>
      </div>
      <template #footer><el-button class="button-secondary" @click="dataScopeDialogOpen = false">取消</el-button><el-button v-permission="'system:role:assign'" type="primary" :disabled="dataScopeDialogRole?.key === 'admin'" :loading="dataScopeSaving" @click="saveDataScopes">保存数据范围</el-button></template>
    </el-dialog>

    <PermissionCatalogManager v-model="permissionCatalogOpen" :is-administrator="isAdministrator" @saved="onPermissionCatalogSaved" />
  </div>
</template>

<style scoped>
.roles-layout { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(300px, .85fr); gap: 14px; align-items: stretch; }
.role-list-panel, .role-profile-panel { min-width: 0; min-height: 490px; padding: 17px; }
.role-picker-panel { display: flex; flex-direction: column; }
.role-picker-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 15px; border-bottom: 1px solid var(--line); }
.role-picker-heading h3 { margin: 5px 0 3px; color: #334b3c; font-size: 16px; }
.role-picker-heading p { margin: 0; color: #98a39a; font-size: 12px; }
.role-picker-heading__icon { margin-top: 3px; color: #6e9a7b; font-size: 20px; }
.role-picker-body { flex: 1 1 auto; min-height: 0; padding-top: 18px; }
.role-picker-body--list { display: flex; flex-direction: column; overflow: hidden; }
.role-picker-body--list .roles-toolbar { flex: 0 0 auto; }
.role-picker-body--list .el-table { flex: 1 1 auto; min-height: 0; }
.role-picker-select { width: 100%; }
.role-picker-option { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; }
.role-picker-option > span { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.role-picker-option strong { overflow: hidden; color: #40584a; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.role-picker-option small { color: #96a198; font-family: 'DM Sans', sans-serif; font-size: 11px; }
.role-picker-selected { display: flex; align-items: center; gap: 10px; margin-top: 18px; padding: 14px; border: 1px solid #e5eee5; border-radius: 11px; background: #f8fbf7; }
.role-picker-selected > div { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.role-picker-selected strong { overflow: hidden; color: #3d5547; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.role-picker-selected small { color: #95a096; font-family: 'DM Sans', sans-serif; font-size: 11px; }
.role-picker-hint { margin-top: 12px; color: #9aa59b; font-size: 11px; line-height: 1.6; }
.role-picker-empty { display: flex; min-height: 130px; align-items: center; justify-content: center; gap: 7px; color: #98a39a; font-size: 12px; }
.role-picker-empty strong { color: #6c7e70; }
.roles-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.roles-toolbar .el-input { min-width: 220px; flex: 1 1 280px; max-width: none; }
.roles-toolbar .el-button { flex: 0 0 auto; }
.role-table-wrap { min-width: 0; }
.role-reference-table { width: 100%; cursor: pointer; }
.role-reference-table :deep(th.el-table__cell) { font-size: 12px; }
.role-reference-table :deep(td.el-table__cell) { font-size: 13px; }
.role-reference-table :deep(.el-table__row.current-row > td) { background: #f2f6f1; }
.role-person { display: flex; min-width: 0; align-items: center; gap: 9px; }
.role-select-button { display: block; max-width: 100%; padding: 0; border: 0; color: inherit; background: transparent; font: inherit; text-align: left; cursor: pointer; }
.role-select-button:focus-visible { outline: 2px solid #5b8d6c; outline-offset: 3px; border-radius: 6px; }
.role-person > span:last-child { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.role-person b, .role-person small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.role-person b { color: #3d5547; font-size: 14px; }
.role-person small { color: #95a096; font-size: 12px; }
.role-symbol { display: grid; flex: none; width: 32px; height: 32px; place-items: center; border-radius: 10px; background: #e6f1e9; color: var(--accent); font-size: 16px; }
.role-symbol--admin { color: #ae8950; background: #f7f1e4; }
.builtin-pill { margin-left: 5px; padding: 2px 5px; border-radius: 5px; color: #ab8751; background: #f8f1e5; font-size: 10px; font-weight: 500; font-style: normal; }
.rights-count { color: #5f8068; font-size: 13px; white-space: nowrap; }
.member-count { color: #738277; font-size: 13px; white-space: nowrap; }
.role-table-actions { display: flex; align-items: center; gap: 2px; white-space: nowrap; }
.role-table-actions :deep(.el-button) { padding: 0 4px; font-size: 12px; }
.role-list-foot { margin-top: 12px; padding-top: 11px; border-top: 1px solid var(--line); color: #8e9a90; font-size: 12px; }
.role-list-foot strong { color: #6f806f; }
.role-list-foot p { margin: 4px 0 0; line-height: 1.55; }
.role-profile-panel { display: flex; flex-direction: column; }
.role-profile-head { display: flex; min-width: 0; align-items: center; gap: 11px; padding: 3px 0 12px; }
.role-profile-icon { display: grid; flex: none; width: 46px; height: 46px; place-items: center; border: 1px solid #efe6d4; border-radius: 15px; color: #ac8750; background: #f8f3e9; font-size: 20px; }
.role-profile-title { min-width: 0; flex: 1; }
.role-profile-title h2 { overflow: hidden; margin: 3px 0; color: #304639; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.role-key-pill { color: #849187; font-family: 'DM Sans', sans-serif; font-size: 12px; white-space: nowrap; }
.role-profile-head :deep(.el-tag) { flex: none; }
.role-description { min-height: 22px; margin: 0 0 14px; color: #8d9a90; font-size: 12px; line-height: 1.6; }
.role-summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 12px; border: 1px solid #e9efe8; border-radius: 10px; background: #fafcf9; }
.role-summary-grid > div { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.role-summary-grid > div + div { padding-left: 9px; border-left: 1px solid #e6ece6; }
.role-summary-grid span { color: #98a39a; font-size: 11px; white-space: nowrap; }
.role-summary-grid strong { overflow: hidden; color: #425a4a; font-family: 'Manrope', sans-serif; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.role-summary-grid strong small { color: #9ba69c; font-family: inherit; font-size: 10px; font-weight: 400; }
.assigned-rights { padding-top: 4px; }
.rights-preview { margin-top: 8px; }
.rights-preview-row { display: grid; grid-template-columns: 7px minmax(70px, auto) minmax(0, 1fr); align-items: center; gap: 8px; min-height: 34px; padding: 5px 0; border-bottom: 1px solid #f0f3ef; color: #566b5c; font-size: 12px; }
.rights-preview-row > span:nth-child(2) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rights-preview-row small { overflow: hidden; color: #98a297; font-family: 'DM Sans', sans-serif; font-size: 10px; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.rights-dot { width: 7px; height: 7px; border-radius: 50%; background: #78a584; }
.rights-overflow { margin-top: 9px; color: #8b998d; font-size: 11px; white-space: nowrap; }
.permission-preview { max-height: 252px; overflow: auto; padding: 2px 0; }
.permission-preview-item { display: flex; min-height: 26px; align-items: center; gap: 7px; padding: 2px 4px; border-bottom: 1px solid #f1f3f0; color: #a1aaa2; font-size: 12px; }
.permission-preview-item strong { color: inherit; font-weight: 500; }
.permission-preview-item small { margin-left: auto; color: #9aa59b; font-size: 10px; white-space: nowrap; }
.permission-preview-item.is-granted { color: #5d8267; }
.permission-preview-item.is-granted small { color: #6f8d75; }
.permission-type { display: inline-grid; width: 19px; height: 19px; flex: 0 0 19px; place-items: center; border-radius: 6px; font-size: 10px; font-weight: 700; }
.permission-type--m { color: #528a70; background: #e9f3eb; }
.permission-type--c { color: #b18c51; background: #f8f1e4; }
.permission-type--f { color: #718399; background: #edf1f6; }
.role-load-error { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 10px; color: #b65b4f; font-size: 12px; }
.role-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-top: auto; padding-top: 13px; border-top: 1px solid #edf1ed; }
.role-actions :deep(.el-button) { min-height: 32px; font-size: 12px; white-space: nowrap; }
.role-empty-panel { display: grid; place-items: center; }
.permission-dialog :deep(.el-dialog__body) { padding-top: 0; }
.permission-intro { display: flex; align-items: center; gap: 6px; padding-bottom: 12px; border-bottom: 1px solid var(--line); }
.permission-legend { display: flex; align-items: center; gap: 12px; margin-right: auto; }
.permission-legend > span { display: inline-flex; align-items: center; gap: 5px; color: #758378; font-size: 12px; white-space: nowrap; }
.permission-legend i { display: grid; width: 18px; height: 18px; place-items: center; border-radius: 5px; font-size: 10px; font-style: normal; font-weight: 700; }
.legend-directory { color: #52775c; background: #e5efe6; }
.legend-page { color: #46756a; background: #e8f0ed; }
.legend-action { color: #a18448; background: #f3efe4; }
.permission-tree-wrap { max-height: 58vh; overflow: auto; padding: 12px 4px; }
.permission-tree :deep(.el-tree-node__content) { min-height: 34px; border-radius: 7px; }
.permission-tree :deep(.el-tree-node__content:hover) { background: #f2f6f1; }
.permission-tree { background: transparent; }
.permission-node-label { display: flex; min-width: 0; width: 100%; align-items: center; gap: 8px; }
.permission-node-label > span:nth-child(2) { overflow: hidden; color: #52665a; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.permission-node-label code { overflow: hidden; max-width: min(270px, 35vw); margin-left: auto; color: #9aa59b; font-family: 'DM Sans', sans-serif; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.category-intro { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; padding-bottom: 12px; border-bottom: 1px solid var(--line); }
.category-intro .el-text { flex: 1 1 220px; }
.category-tree-wrap { max-height: 52vh; min-height: 120px; overflow: auto; padding: 10px 4px; }
.role-category-summary { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 10px; padding: 9px 12px; border: 1px solid #e9efe8; border-radius: 10px; background: #fbfdf9; }
.role-category-summary > div { display: flex; flex-direction: column; gap: 4px; }
.role-category-summary span { color: #98a39a; font-size: 11px; white-space: nowrap; }
.role-category-summary strong { color: #425a4a; font-size: 13px; font-weight: 600; }
.data-scope-list { margin-top: 12px; }
.data-scope-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 2px; border-bottom: 1px solid #f0f3ef; color: #52665a; font-size: 13px; }
.data-scope-row:last-child { border-bottom: 0; }
.data-scope-select { width: 220px; flex: 0 0 auto; }
@media (max-width: 1100px) { .roles-layout { grid-template-columns: 1fr; } .role-profile-panel { min-height: auto; } }
@media (max-width: 760px) { .roles-toolbar { flex-wrap: wrap; } .roles-toolbar .el-input { width: 100%; min-width: 0; flex-basis: 100%; } .roles-toolbar .el-button { flex: 1 1 0; } .role-list-panel, .role-profile-panel { padding: 13px; } .permission-intro { flex-wrap: wrap; } .permission-legend { width: 100%; margin-bottom: 3px; } .permission-node-label code { max-width: 120px; } }
</style>
