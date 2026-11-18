import { moduleDefinitions } from './modules'
import { navGroups } from './navigation'

export const routePermissions = new Map(navGroups.flatMap((group) => group.items.map((item) => [item.path, item.permission] as const)))

export function moduleForPath(path: string) {
  return Object.values(moduleDefinitions).find((module) => module.path === path)
}
