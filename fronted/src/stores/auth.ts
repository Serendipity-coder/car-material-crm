import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type AuthUser, type LoginInput } from '../api/auth'
import { AUTH_TOKEN_KEY, PERMISSION_KEY, USER_KEY } from '../data/keys'
import { setPermissionSnapshot } from '../utils/permissions'

function readUser(): AuthUser | null {
  try { return JSON.parse(localStorage.getItem(USER_KEY) ?? 'null') as AuthUser | null } catch { return null }
}
function readPermissions(): string[] {
  try { return JSON.parse(localStorage.getItem(PERMISSION_KEY) ?? '[]') as string[] } catch { return [] }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(AUTH_TOKEN_KEY) ?? '')
  const user = ref<AuthUser | null>(readUser())
  const permissions = ref<string[]>(readPermissions())
  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  setPermissionSnapshot(permissions.value)

  function applySession(session: { accessToken: string; user: AuthUser }): void {
    token.value = session.accessToken
    user.value = session.user
    permissions.value = session.user.permissionCodes ?? []
    localStorage.setItem(AUTH_TOKEN_KEY, token.value)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    localStorage.setItem(PERMISSION_KEY, JSON.stringify(permissions.value))
    setPermissionSnapshot(permissions.value)
  }

  async function login(input: LoginInput): Promise<void> {
    const session = await authApi.login(input)
    applySession(session)
  }

  async function refreshProfile(): Promise<void> {
    const profile = await authApi.me()
    if (!profile) return
    user.value = profile
    permissions.value = profile.permissionCodes ?? []
    localStorage.setItem(USER_KEY, JSON.stringify(profile))
    localStorage.setItem(PERMISSION_KEY, JSON.stringify(permissions.value))
    setPermissionSnapshot(permissions.value)
  }

  async function logout(): Promise<void> {
    try { await authApi.logout() } finally {
      token.value = ''
      user.value = null
      permissions.value = []
      localStorage.removeItem(AUTH_TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(PERMISSION_KEY)
      setPermissionSnapshot([])
    }
  }

  function can(permission?: string): boolean {
    return !permission || permissions.value.includes('*') || permissions.value.includes(permission)
  }

  return { token, user, permissions, isAuthenticated, login, logout, refreshProfile, can, applySession }
})
