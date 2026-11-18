import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { moduleDefinitions } from '../data/modules'
import { pinia } from '../stores/pinia'
import { useAuthStore } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录', public: true } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册', public: true } },
  { path: '/', name: 'overview', component: () => import('../views/DashboardView.vue'), meta: { title: '经营概况', permission: 'work:overview:list' } },
  ...Object.values(moduleDefinitions).map((module) => ({
    path: module.path,
    name: module.key,
    component: () => import('../views/ModuleView.vue'),
    meta: { title: module.title, eyebrow: module.eyebrow, description: module.description, permission: module.permission, moduleKey: module.key },
  } as RouteRecordRaw)),
  { path: '/employees', name: 'employees', component: () => import('../views/EmployeesView.vue'), meta: { title: '员工管理', permission: 'system:user:list' } },
  { path: '/departments', name: 'departments', component: () => import('../views/DepartmentsView.vue'), meta: { title: '部门管理', permission: 'system:dept:list' } },
  { path: '/roles', name: 'roles', component: () => import('../views/RolesView.vue'), meta: { title: '角色管理', permission: 'system:role:list' } },
  { path: '/audit-logs', name: 'audit-logs', component: () => import('../views/AuditLogView.vue'), meta: { title: '操作日志', permission: 'system:audit:list' } },
  { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { title: '个人资料' } },
  { path: '/403', name: 'forbidden', component: () => import('../views/ForbiddenView.vue'), meta: { title: '无权访问' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面未找到' } },
]

export const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0, behavior: 'smooth' }) })

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.public) {
    if (auth.isAuthenticated && (to.path === '/login' || to.path === '/register')) return '/'
    return true
  }
  if (!auth.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
  const required = typeof to.meta.permission === 'string' ? to.meta.permission : ''
  if (required && !auth.can(required)) return { path: '/403', query: { from: to.fullPath } }
  return true
})
