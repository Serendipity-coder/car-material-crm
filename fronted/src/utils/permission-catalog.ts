import type { PermissionNode } from '../data/organization'

export const PERMISSION_CATALOG_DRAFT_KEY = 'crm_frontend_permission_catalog_draft_v1'

export interface PermissionCatalogAddition {
  parentId: string | null
  node: PermissionNode
}

export interface PermissionCatalogDraft {
  overrides: Record<string, { name?: string; permission?: string }>
  additions: PermissionCatalogAddition[]
}

function emptyDraft(): PermissionCatalogDraft {
  return { overrides: {}, additions: [] }
}

function isPermissionType(value: unknown): value is PermissionNode['type'] {
  return value === 'M' || value === 'C' || value === 'F'
}

export function readPermissionCatalogDraft(): PermissionCatalogDraft {
  try {
    const raw: unknown = JSON.parse(localStorage.getItem(PERMISSION_CATALOG_DRAFT_KEY) ?? 'null')
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return emptyDraft()
    const value = raw as Partial<PermissionCatalogDraft>
    const overrides = value.overrides && typeof value.overrides === 'object' && !Array.isArray(value.overrides)
      ? Object.fromEntries(Object.entries(value.overrides).flatMap(([id, patch]) => {
          if (!patch || typeof patch !== 'object') return []
          const item = patch as { name?: unknown; permission?: unknown }
          return [[id, {
            ...(typeof item.name === 'string' ? { name: item.name } : {}),
            ...(typeof item.permission === 'string' ? { permission: item.permission } : {}),
          }]]
        }))
      : {}
    const additions = Array.isArray(value.additions) ? value.additions.flatMap((entry) => {
      if (!entry || typeof entry !== 'object') return []
      const item = entry as PermissionCatalogAddition
      const node = item.node
      if (!node || typeof node !== 'object' || typeof node.id !== 'string' || !node.id || typeof node.name !== 'string' || !node.name || typeof node.permission !== 'string' || !node.permission || !isPermissionType(node.type)) return []
      return [{ parentId: typeof item.parentId === 'string' && item.parentId ? item.parentId : null, node: { id: node.id, name: node.name, type: node.type, permission: node.permission } }]
    }) : []
    return { overrides, additions }
  } catch {
    return emptyDraft()
  }
}

export function savePermissionCatalogDraft(draft: PermissionCatalogDraft): void {
  localStorage.setItem(PERMISSION_CATALOG_DRAFT_KEY, JSON.stringify(draft))
}

function findNode(nodes: PermissionNode[], id: string): PermissionNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const child = node.children ? findNode(node.children, id) : undefined
    if (child) return child
  }
  return undefined
}

function applyOverrides(nodes: PermissionNode[], overrides: PermissionCatalogDraft['overrides']): void {
  for (const node of nodes) {
    const patch = overrides[node.id]
    if (patch) {
      if (typeof patch.name === 'string') node.name = patch.name
      if (typeof patch.permission === 'string') node.permission = patch.permission
    }
    if (node.children) applyOverrides(node.children, overrides)
  }
}

export function applyPermissionCatalogDraft(baseTree: PermissionNode[]): PermissionNode[] {
  const tree = structuredClone(baseTree)
  const draft = readPermissionCatalogDraft()
  applyOverrides(tree, draft.overrides)
  for (const addition of draft.additions) {
    if (findNode(tree, addition.node.id)) continue
    const node = structuredClone(addition.node)
    const patch = draft.overrides[node.id]
    if (patch?.name) node.name = patch.name
    if (patch?.permission) node.permission = patch.permission
    if (!addition.parentId) tree.push(node)
    else {
      const parent = findNode(tree, addition.parentId)
      if (parent) {
        parent.children ??= []
        parent.children.push(node)
      }
    }
  }
  return tree
}
