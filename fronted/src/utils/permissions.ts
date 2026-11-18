import type { Directive } from 'vue'

export function hasPermission(codes: string[] = [], required?: string): boolean {
  if (!required || codes.includes('*')) return true
  return codes.includes(required)
}

let permissionSnapshot: string[] = []

export function setPermissionSnapshot(codes: string[]): void {
  permissionSnapshot = codes
}

export const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    if (!hasPermission(permissionSnapshot, binding.value)) el.style.display = 'none'
  },
  updated(el, binding) {
    el.style.display = hasPermission(permissionSnapshot, binding.value) ? '' : 'none'
  },
}
