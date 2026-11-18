<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { organizationApi } from '../api/organization'
import type { PermissionNode } from '../data/organization'
import { useMockData } from '../api/client'
import { readPermissionCatalogDraft, savePermissionCatalogDraft, type PermissionCatalogDraft } from '../utils/permission-catalog'

interface Props { modelValue: boolean; isAdministrator: boolean }
interface CatalogRow { id: string; name: string; type: PermissionNode['type']; permission: string; parentId: string | null; parentName: string; depth: number }
const props = defineProps<Props>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const visible = computed({ get: () => props.modelValue, set: (value: boolean) => emit('update:modelValue', value) })
const isMockMode = useMockData
const canEdit = computed(() => props.isAdministrator && isMockMode)
const tree = ref<PermissionNode[]>([])
const loading = ref(false)
const search = ref('')
const typeFilter = ref<'' | PermissionNode['type']>('')
const currentPage = ref(1)
const pageSize = ref(12)
const editDialogOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const form = reactive({ id: '', name: '', type: 'C' as PermissionNode['type'], permission: '', parentId: '' })
const formRules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入权限名称', trigger: 'blur' }, { max: 64, message: '权限名称不能超过 64 个字符', trigger: 'blur' }],
  permission: [{ required: true, whitespace: true, message: '请输入权限标识', trigger: 'blur' }, { pattern: /^[a-z][a-z0-9:_-]*$/, message: '权限标识以小写字母开头，只能包含小写字母、数字、冒号、下划线或连字符', trigger: 'blur' }],
}

function flatten(nodes: PermissionNode[], parentId: string | null = null, parentName = '顶级目录', depth = 0, rows: CatalogRow[] = []): CatalogRow[] {
  for (const node of nodes) {
    rows.push({ id: node.id, name: node.name, type: node.type, permission: node.permission, parentId, parentName, depth })
    if (node.children) flatten(node.children, node.id, node.name, depth + 1, rows)
  }
  return rows
}
const allRows = computed(() => flatten(tree.value))
const filteredRows = computed(() => {
  const term = search.value.trim().toLocaleLowerCase()
  return allRows.value.filter((row) => (!typeFilter.value || row.type === typeFilter.value) && (!term || `${row.name} ${row.permission} ${row.parentName}`.toLocaleLowerCase().includes(term)))
})
const pageRows = computed(() => filteredRows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
const typeCounts = computed(() => ({ M: allRows.value.filter((row) => row.type === 'M').length, C: allRows.value.filter((row) => row.type === 'C').length, F: allRows.value.filter((row) => row.type === 'F').length }))
const parentOptions = computed(() => {
  if (form.type === 'M') return []
  return allRows.value.filter((row) => form.type === 'C' ? row.type === 'M' : row.type === 'M' || row.type === 'C')
})
const editTitle = computed(() => formMode.value === 'create' ? '新增权限节点' : '编辑权限节点')
function typeLabel(type: PermissionNode['type']): string { return type === 'M' ? '目录' : type === 'C' ? '页面' : '操作' }
function isDraftNode(id: string): boolean { return id.startsWith('local-permission-') }

async function loadTree(): Promise<void> {
  if (!props.isAdministrator) {
    ElMessage.error('权限目录仅管理员可以查看')
    visible.value = false
    return
  }
  loading.value = true
  try { tree.value = await organizationApi.menuTree() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限目录读取失败') }
  finally { loading.value = false }
}
watch(() => props.modelValue, (open) => { if (open) void loadTree() })
watch([search, typeFilter], () => { currentPage.value = 1 })

function openCreate(): void {
  if (!props.isAdministrator) { ElMessage.error('权限目录仅管理员可以管理'); return }
  if (!canEdit.value) { ElMessage.warning('真实模式暂未提供权限目录写入接口；当前目录只读。'); return }
  formMode.value = 'create'
  Object.assign(form, { id: '', name: '', type: 'C', permission: '', parentId: '' })
  editDialogOpen.value = true
}
function openEdit(row: CatalogRow): void {
  if (!props.isAdministrator) { ElMessage.error('权限目录仅管理员可以管理'); return }
  if (!canEdit.value) { ElMessage.warning('真实模式暂未提供权限目录写入接口；当前目录只读。'); return }
  formMode.value = 'edit'
  Object.assign(form, { id: row.id, name: row.name, type: row.type, permission: row.permission, parentId: row.parentId ?? '' })
  editDialogOpen.value = true
}
function readDraft(): PermissionCatalogDraft { return readPermissionCatalogDraft() }
function uniquePermissionCode(code: string, exceptId = ''): boolean {
  return !allRows.value.some((row) => row.id !== exceptId && row.permission.toLocaleLowerCase() === code.toLocaleLowerCase())
}
function validParent(): boolean {
  if (form.type === 'M') return true
  const parent = allRows.value.find((row) => row.id === form.parentId)
  if (!parent) return false
  return form.type === 'C' ? parent.type === 'M' : parent.type === 'M' || parent.type === 'C'
}
async function saveNode(): Promise<void> {
  if (!props.isAdministrator || !canEdit.value) { ElMessage.error('当前账号或模式不允许编辑权限目录'); return }
  if (!(await formRef.value?.validate().catch(() => false))) return
  const name = form.name.trim()
  const permission = form.permission.trim()
  if (!uniquePermissionCode(permission, formMode.value === 'edit' ? form.id : '')) { ElMessage.error('权限标识已存在，请使用唯一标识'); return }
  if (formMode.value === 'create' && !validParent()) { ElMessage.error(form.type === 'M' ? '目录节点必须是顶级节点' : '请选择符合层级规则的上级节点'); return }
  saving.value = true
  try {
    const draft = readDraft()
    if (formMode.value === 'edit') {
      draft.overrides[form.id] = { name, permission }
    } else {
      const id = `local-permission-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`
      draft.additions.push({ parentId: form.type === 'M' ? null : form.parentId, node: { id, name, type: form.type, permission } })
    }
    savePermissionCatalogDraft(draft)
    tree.value = await organizationApi.menuTree()
    editDialogOpen.value = false
    currentPage.value = 1
    ElMessage.success(formMode.value === 'create' ? '权限节点已加入 Mock 本地草稿' : '权限节点变更已保存到 Mock 本地草稿')
    emit('saved')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限目录草稿保存失败') }
  finally { saving.value = false }
}
</script>

<template>
  <el-dialog v-model="visible" title="权限目录管理" width="min(920px, 96vw)" destroy-on-close class="permission-catalog-manager">
    <div class="catalog-intro-row">
      <div><div class="panel-kicker">权限目录</div><p>集中查看系统目录、页面和操作权限。角色勾选仍在“配置角色权限”中完成。</p></div>
      <el-tag type="warning" effect="plain" round>管理员专属</el-tag>
    </div>
    <el-alert v-if="isMockMode" class="catalog-mode-alert" title="Mock 权限目录草稿" description="新增和编辑内容保存在当前浏览器，并合并进 Mock 权限树。目录变化不自动创建侧栏、页面或后端权限控制。" type="info" :closable="false" show-icon />
    <el-alert v-else class="catalog-mode-alert" title="真实模式目录只读" description="当前后端仅提供权限目录读取接口，没有新增/编辑接口；如需持久化并在其他用户生效，需由后端补充权限目录写入契约。" type="warning" :closable="false" show-icon />
    <div class="catalog-counts"><span><b>{{ typeCounts.M }}</b> 目录 M</span><span><b>{{ typeCounts.C }}</b> 页面 C</span><span><b>{{ typeCounts.F }}</b> 操作 F</span><small>{{ isMockMode ? '当前浏览器草稿与 Mock 权限树' : '真实接口权限树' }}</small></div>
    <div class="data-toolbar catalog-toolbar"><div class="data-toolbar__top"><el-input v-model="search" class="search-input" :prefix-icon="Search" clearable placeholder="搜索名称、权限标识或上级目录" /><div class="data-toolbar__actions"><el-button type="primary" :icon="Plus" :disabled="!canEdit" @click="openCreate">新增权限</el-button></div></div><div class="data-toolbar__filters"><div class="filter-item"><el-select v-model="typeFilter" class="filter-select filter-select--short" clearable placeholder="全部类型"><el-option label="目录 M" value="M" /><el-option label="页面 C" value="C" /><el-option label="操作 F" value="F" /></el-select></div></div></div>
    <el-table v-loading="loading" :data="pageRows" row-key="id" class="crm-table catalog-table">
      <el-table-column label="权限名称" min-width="210"><template #default="{ row }"><div class="catalog-name" :style="{ paddingLeft: `${row.depth * 16}px` }"><i :class="`catalog-dot catalog-dot--${row.type.toLowerCase()}`"></i><strong>{{ row.name }}</strong><el-tag v-if="isDraftNode(row.id)" size="small" type="warning" effect="plain">草稿</el-tag></div></template></el-table-column>
      <el-table-column label="类型" width="105"><template #default="{ row }"><el-tag :type="row.type === 'M' ? 'success' : row.type === 'C' ? 'warning' : 'info'" effect="plain" round>{{ typeLabel(row.type) }} {{ row.type }}</el-tag></template></el-table-column>
      <el-table-column label="权限说明" min-width="150"><template #default="{ row }">{{ typeLabel(row.type) }}权限</template></el-table-column>
      <el-table-column prop="parentName" label="上级节点" min-width="150" />
      <el-table-column label="操作" width="85" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="!canEdit" @click="openEdit(row)">编辑</el-button></template></el-table-column>
      <template #empty><div class="table-empty"><strong>没有匹配的权限节点</strong><span>调整搜索词或类型筛选。</span></div></template>
    </el-table>
    <div class="catalog-pagination"><span>共 {{ filteredRows.length }} 个节点</span><el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[12, 24, 48]" :total="filteredRows.length" layout="sizes, prev, pager, next, jumper" small /></div>
    <template #footer><el-button class="button-secondary" @click="visible = false">关闭</el-button></template>
  </el-dialog>

  <el-dialog v-model="editDialogOpen" :title="editTitle" width="min(520px, 94vw)" append-to-body destroy-on-close>
    <el-alert v-if="formMode === 'edit'" title="编辑仅更改显示名称和权限标识，节点 ID、类型及所属层级保持不变，以免破坏已有角色勾选关系。" type="info" :closable="false" show-icon class="edit-node-alert" />
    <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="record-form">
      <el-form-item label="权限名称" prop="name"><el-input v-model="form.name" maxlength="64" placeholder="例如：客户档案" /></el-form-item>
      <el-form-item v-if="formMode === 'create'" label="节点类型" prop="type"><el-radio-group v-model="form.type"><el-radio value="M">目录 M</el-radio><el-radio value="C">页面 C</el-radio><el-radio value="F">操作 F</el-radio></el-radio-group></el-form-item>
      <el-form-item v-else label="节点类型"><el-input :model-value="`${typeLabel(form.type)} ${form.type}`" disabled /></el-form-item>
      <el-form-item v-if="formMode === 'create' && form.type !== 'M'" label="上级节点" prop="parentId"><el-select v-model="form.parentId" class="w-full" placeholder="选择上级节点"><el-option v-for="parent in parentOptions" :key="parent.id" :label="`${parent.name} · ${parent.permission}`" :value="parent.id" /></el-select></el-form-item>
      <el-form-item label="权限标识" prop="permission"><el-input v-model="form.permission" maxlength="100" placeholder="例如：crm:customer:export" /></el-form-item>
      <el-alert v-if="formMode === 'create'" title="目录 M 只能建在顶层；页面 C 需归属目录 M；操作 F 可归属目录 M 或页面 C。" type="info" :closable="false" show-icon />
    </el-form>
    <template #footer><el-button class="button-secondary" @click="editDialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveNode">保存到 Mock 草稿</el-button></template>
  </el-dialog>
</template>

<style scoped>
.catalog-intro-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 12px; }
.catalog-intro-row p { margin: 5px 0 0; color: #829086; font-size: 12px; line-height: 1.55; }
.catalog-mode-alert { margin-bottom: 12px; }
.catalog-counts { display: flex; align-items: center; gap: 15px; margin: 0 0 12px; padding: 10px 12px; border: 1px solid #e8eee7; border-radius: 10px; background: #fafcf9; color: #76847a; font-size: 12px; }
.catalog-counts b { color: #42634e; font-family: 'DM Sans', sans-serif; font-size: 14px; }
.catalog-counts small { margin-left: auto; color: #a0aaa1; font-size: 10px; }
.catalog-toolbar { margin-bottom: 10px; }
.catalog-name { display: flex; align-items: center; gap: 7px; min-width: 0; }
.catalog-name strong { overflow: hidden; color: #40594a; text-overflow: ellipsis; white-space: nowrap; }
.catalog-dot { width: 7px; height: 7px; flex: none; border-radius: 50%; background: #8baa8e; }
.catalog-dot--c { background: #d1b26d; }
.catalog-dot--f { background: #8297ac; }
.catalog-pagination { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-top: 12px; color: #8d9a90; font-size: 12px; }
.edit-node-alert { margin-bottom: 14px; }
@media (max-width: 760px) { .catalog-counts { flex-wrap: wrap; gap: 8px 13px; } .catalog-counts small { width: 100%; margin-left: 0; } .catalog-toolbar { align-items: flex-start; flex-direction: column; } .catalog-pagination { align-items: flex-start; flex-direction: column; } }
</style>
