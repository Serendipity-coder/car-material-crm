<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import { Aim, ArrowDown, ArrowRight, Avatar, Bell, ChatDotRound, Checked, Close, Connection, DataAnalysis, DataBoard, Document, Expand, Fold, Goods, Grid, HelpFilled, Histogram, Key, Lock, Menu, Money, OfficeBuilding, Phone, PieChart, Plus, PriceTag, RefreshLeft, Search, Service, ShoppingCart, SwitchButton, Tickets, TrendCharts, User, Van, Wallet } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { navGroups, allNavEntries } from '../data/navigation'
import { moduleDefinitions } from '../data/modules'
import { RECENT_TABS_KEY } from '../data/keys'
import { useMockData } from '../api/client'

interface RouteTab { path: string; title: string }
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(localStorage.getItem('crm_sidebar_collapsed') === '1')
const mobileOpen = ref(false)
const isMobile = ref(window.innerWidth <= 640)
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const logoutConfirmOpen = ref(false)
const logoutLoading = ref(false)
const tabs = ref<RouteTab[]>([])
const expandedGroups = ref<string[]>([])
const icons = { Aim, ArrowDown, ArrowRight, Avatar, Bell, ChatDotRound, Checked, Close, Connection, DataAnalysis, DataBoard, Document, Expand, Fold, Goods, Grid, HelpFilled, Histogram, Key, Lock, Menu, Money, OfficeBuilding, Phone, PieChart, Plus, PriceTag, RefreshLeft, Search, Service, ShoppingCart, SwitchButton, Tickets, TrendCharts, User, Van, Wallet } as unknown as Record<string, Component>
const visibleGroups = computed(() => navGroups.map((group) => ({ ...group, items: group.items.filter((item) => auth.can(item.permission)) })).filter((group) => group.items.length > 0))
const visibleModules = computed(() => Object.values(moduleDefinitions).filter((module) => auth.can(module.permission) && auth.can(module.permission.replace(':list', ':add')) && !module.key.startsWith('reports-')))
const searchResults = computed(() => searchQuery.value.trim() ? allNavEntries.filter((item) => auth.can(item.permission) && `${item.title} ${item.path}`.toLowerCase().includes(searchQuery.value.toLowerCase())).slice(0, 6) : [])
const currentEntry = computed(() => allNavEntries.find((item) => item.path === route.path))
const currentTitle = computed(() => String(route.meta.title ?? currentEntry.value?.title ?? 'CRM 工作台'))
const currentGroup = computed(() => navGroups.find((group) => group.items.some((item) => item.path === route.path))?.title ?? '个人与系统')
const modeLabel = computed(() => useMockData ? '演示数据' : '真实数据')
function isGroupExpanded(title: string): boolean { return expandedGroups.value.includes(title) }
function toggleGroup(title: string): void {
  expandedGroups.value = isGroupExpanded(title) ? expandedGroups.value.filter((item) => item !== title) : [...expandedGroups.value, title]
}

function loadTabs(): RouteTab[] {
  try {
    const stored = JSON.parse(localStorage.getItem(RECENT_TABS_KEY) ?? '[]') as RouteTab[]
    return Array.isArray(stored) ? stored.filter((tab) => Boolean(tab && typeof tab.path === 'string' && router.resolve(tab.path).name !== 'not-found')) : []
  } catch { return [] }
}
function persistTabs(): void { localStorage.setItem(RECENT_TABS_KEY, JSON.stringify(tabs.value)) }
function addCurrentTab(): void {
  if (route.meta.public || route.name === 'not-found' || ['/403'].includes(route.path) || route.path.startsWith('/login') || route.path.startsWith('/register')) return
  const tab = { path: route.path, title: currentTitle.value }
  const rest = tabs.value.filter((item) => item.path !== tab.path)
  tabs.value = [...rest, tab].slice(-8)
  persistTabs()
}
function go(path: string): void { searchOpen.value = false; searchQuery.value = ''; mobileOpen.value = false; void router.push(path) }
function prefetchRoute(path: string): void {
  for (const record of router.resolve(path).matched) {
    const component = record.components?.default
    if (typeof component === 'function') void (component as () => Promise<unknown>)().catch(() => undefined)
  }
}
function closeTab(tab: RouteTab): void {
  const index = tabs.value.findIndex((item) => item.path === tab.path)
  tabs.value = tabs.value.filter((item) => item.path !== tab.path)
  persistTabs()
  if (route.path === tab.path) {
    const next = tabs.value[Math.max(0, index - 1)] ?? tabs.value[0]
    go(next?.path ?? '/')
  }
}
function toggleSidebar(): void {
  if (isMobile.value) mobileOpen.value = !mobileOpen.value
  else { collapsed.value = !collapsed.value; localStorage.setItem('crm_sidebar_collapsed', collapsed.value ? '1' : '0') }
}
function handleResize(): void { isMobile.value = window.innerWidth <= 640; if (!isMobile.value) mobileOpen.value = false }
function goSearch(): void { const first = searchResults.value[0]; if (first) go(first.path) }
function handleGlobalKeydown(event: KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    searchOpen.value = true
    requestAnimationFrame(() => searchInput.value?.focus())
  }
  if (event.key === 'Escape') searchOpen.value = false
}
function openQuickCreate(moduleKey: string): void { const module = moduleDefinitions[moduleKey]; if (module) go(`${module.path}?create=1`) }
function logout(): void { logoutConfirmOpen.value = true }
function cancelLogout(): void { if (!logoutLoading.value) logoutConfirmOpen.value = false }
async function confirmLogout(): Promise<void> {
  logoutLoading.value = true
  try {
    await auth.logout()
    tabs.value = []
    persistTabs()
    logoutConfirmOpen.value = false
    await router.replace('/login')
  } catch { ElMessage.error('退出失败，请稍后重试') }
  finally { logoutLoading.value = false }
}
function handleUserCommand(command: string): void { if (command === 'logout') void logout(); else if (command === 'profile') go('/profile') }
function iconFor(name: string): Component { return icons[name] ?? icons.Menu }
watch(currentGroup, (group) => { if (group && !isGroupExpanded(group)) expandedGroups.value = [...expandedGroups.value, group] }, { immediate: true })
watch(() => route.fullPath, addCurrentTab, { immediate: true })
onMounted(() => {
  tabs.value = loadTabs()
  persistTabs()
  addCurrentTab()
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', handleGlobalKeydown)
  void auth.refreshProfile().catch(() => ElMessage.error('当前用户信息同步失败，请检查服务连接。'))
})
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); window.removeEventListener('keydown', handleGlobalKeydown) })
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--collapsed': collapsed && !isMobile, 'app-shell--mobile': isMobile }">
    <aside v-if="!isMobile" class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
      <div class="sidebar-brand" @click="go('/')"><span class="brand-mark"><i></i><i></i><i></i></span><span v-if="!collapsed" class="brand-wordmark">evergreen <small>CRM</small></span></div>
      <div v-if="!collapsed" class="workspace-switcher"><span class="workspace-avatar">E</span><span><strong>Evergreen Studio</strong><small>企业工作空间</small></span><el-icon><component :is="iconFor('ArrowDown')" /></el-icon></div>
      <nav class="sidebar-nav" :aria-label="'CRM 主导航'">
          <section v-for="group in visibleGroups" :key="group.title" class="nav-group">
          <button v-if="!collapsed" type="button" class="nav-group-title" :class="{ 'nav-group-title--open': isGroupExpanded(group.title) }" @click="toggleGroup(group.title)"><span>{{ group.title }}</span><el-icon><component :is="iconFor('ArrowDown')" /></el-icon></button>
            <el-tooltip v-if="collapsed || isGroupExpanded(group.title)" v-for="item in group.items" :key="item.path" :content="collapsed ? item.title : ''" placement="right" :disabled="!collapsed">
              <router-link :to="item.path" class="nav-link" :class="{ 'nav-link--active': route.path === item.path }" @mouseenter="prefetchRoute(item.path)" @focus="prefetchRoute(item.path)"><el-icon class="nav-link-icon"><component :is="iconFor(item.icon)" /></el-icon><span v-if="!collapsed" class="nav-link-title">{{ item.title }}</span><span v-if="!collapsed && route.path === item.path" class="nav-link-indicator"></span></router-link>
          </el-tooltip>
        </section>
      </nav>
      <div class="sidebar-bottom"><div v-if="!collapsed" class="sidebar-help"><span class="help-icon"><el-icon><component :is="iconFor('HelpFilled')" /></el-icon></span><div><strong>需要帮助？</strong><small>查看工作台操作指南</small></div><el-icon><component :is="iconFor('ArrowRight')" /></el-icon></div><button class="collapse-control" @click="toggleSidebar"><el-icon><component :is="iconFor(collapsed ? 'Expand' : 'Fold')" /></el-icon><span v-if="!collapsed">收起侧边栏</span></button></div>
    </aside>

    <el-drawer v-if="isMobile" v-model="mobileOpen" direction="ltr" size="min(84vw, 320px)" :with-header="false" class="mobile-nav-drawer">
      <div class="sidebar sidebar--mobile"><div class="sidebar-brand" @click="go('/')"><span class="brand-mark"><i></i><i></i><i></i></span><span class="brand-wordmark">evergreen <small>CRM</small></span></div><div class="workspace-switcher"><span class="workspace-avatar">E</span><span><strong>Evergreen Studio</strong><small>企业工作空间</small></span></div><nav class="sidebar-nav"><section v-for="group in visibleGroups" :key="group.title" class="nav-group"><button type="button" class="nav-group-title" :class="{ 'nav-group-title--open': isGroupExpanded(group.title) }" @click="toggleGroup(group.title)"><span>{{ group.title }}</span><el-icon><component :is="iconFor('ArrowDown')" /></el-icon></button><router-link v-if="isGroupExpanded(group.title)" v-for="item in group.items" :key="item.path" :to="item.path" class="nav-link" :class="{ 'nav-link--active': route.path === item.path }" @mouseenter="prefetchRoute(item.path)" @focus="prefetchRoute(item.path)" @click="mobileOpen = false"><el-icon class="nav-link-icon"><component :is="iconFor(item.icon)" /></el-icon><span class="nav-link-title">{{ item.title }}</span></router-link></section></nav></div>
    </el-drawer>

    <div class="app-main">
      <header class="topbar">
        <div class="topbar-left"><button class="mobile-menu-button" aria-label="打开导航" @click="toggleSidebar"><el-icon><component :is="iconFor('Menu')" /></el-icon></button><div class="breadcrumb"><span>{{ currentGroup }}</span><el-icon><component :is="iconFor('ArrowRight')" /></el-icon><strong>{{ currentTitle }}</strong></div><span class="mode-badge" :class="useMockData ? 'mode-badge--mock' : 'mode-badge--live'"><i></i>{{ modeLabel }}</span></div>
        <div class="topbar-right">
          <div class="global-search" :class="{ 'global-search--open': searchOpen }"><el-icon><component :is="iconFor('Search')" /></el-icon><input ref="searchInput" v-model="searchQuery" placeholder="搜索页面..." @focus="searchOpen = true" @keydown.enter.prevent="goSearch" @keydown.esc="searchOpen = false" /><kbd>⌘ K</kbd><button v-if="searchQuery" class="search-clear" @click="searchQuery = ''">×</button><div v-if="searchOpen && searchQuery && searchResults.length" class="search-results"><button v-for="item in searchResults" :key="item.path" @click="go(item.path)"><el-icon><component :is="iconFor(item.icon)" /></el-icon><span>{{ item.title }}</span><small>{{ item.path }}</small></button></div><div v-if="searchOpen && searchQuery && !searchResults.length" class="search-no-results">没有找到匹配的页面</div></div>
          <el-dropdown trigger="click" @command="openQuickCreate"><el-button class="quick-create-button" type="primary"><el-icon><component :is="iconFor('Plus')" /></el-icon><span>快捷新建</span><el-icon class="quick-create-caret"><component :is="iconFor('ArrowDown')" /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="module in visibleModules.slice(0, 8)" :key="module.key" :command="module.key"><el-icon><component :is="iconFor(module.icon)" /></el-icon>新建{{ module.singular }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          <el-tooltip content="暂无新的系统通知" placement="bottom"><button class="topbar-icon-button" aria-label="通知"><el-icon><component :is="iconFor('Bell')" /></el-icon><i></i></button></el-tooltip>
          <el-dropdown trigger="click" @command="handleUserCommand"><button class="user-menu"><span class="user-avatar">{{ auth.user?.name?.slice(0, 1) ?? 'U' }}</span><span class="user-menu-copy"><strong>{{ auth.user?.name ?? 'CRM 用户' }}</strong><small>{{ auth.user?.role ?? '团队成员' }}</small></span><el-icon><component :is="iconFor('ArrowDown')" /></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile"><el-icon><component :is="iconFor('User')" /></el-icon>个人资料</el-dropdown-item><el-dropdown-item command="logout" divided><el-icon><component :is="iconFor('SwitchButton')" /></el-icon>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </header>
      <div v-if="searchOpen" class="search-backdrop" @click="searchOpen = false"></div>
      <div class="route-tabs"><button v-for="tab in tabs" :key="tab.path" class="route-tab" :class="{ 'route-tab--active': route.path === tab.path }" @click="go(tab.path)"><span>{{ tab.title }}</span><el-icon class="route-tab-close" @click.stop="closeTab(tab)"><component :is="iconFor('Close')" /></el-icon></button></div>
      <main class="page-content" :class="{ 'page-content--state': route.name === 'not-found' || route.name === 'forbidden' }"><transition name="page-fade" mode="out-in"><slot /></transition></main>
      <footer class="app-footer"><span>Evergreen CRM</span><span>客户经营 · 销售协作 · 团队成长</span><span>前端演示版</span></footer>
    </div>
    <div v-if="logoutConfirmOpen" class="logout-modal" role="dialog" aria-modal="true" aria-labelledby="logout-modal-title" @click.self="cancelLogout">
      <section class="logout-modal__card">
        <button class="logout-modal__close" type="button" aria-label="关闭退出确认" :disabled="logoutLoading" @click="cancelLogout">×</button>
        <div class="logout-modal__icon"><el-icon><component :is="iconFor('SwitchButton')" /></el-icon></div>
        <h2 id="logout-modal-title">确认退出登录</h2>
        <p>退出后需要重新输入账号、密码、手机号和验证码才能进入工作台。</p>
        <div class="logout-modal__actions"><button type="button" class="logout-modal__cancel" :disabled="logoutLoading" @click="cancelLogout">暂不退出</button><button type="button" class="logout-modal__confirm" :disabled="logoutLoading" @click="confirmLogout">{{ logoutLoading ? '退出中…' : '退出登录' }}</button></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-content--state { display: flex; flex-direction: column; }
</style>
