export type CrmPrimitive = string | number | boolean | null | undefined
export interface CrmLineItem { product: string; quantity: number; unitPrice: number; sku?: string; spec?: string; unit?: string; discount?: number }
export type CrmValue = CrmPrimitive | CrmLineItem[] | string[]

export interface CrmRecord {
  id: string
  [key: string]: CrmValue
}

export interface PageResult<T> {
  rows: T[]
  total: number
}

export interface QueryState {
  page: number
  pageSize: number
  search?: string
  filters?: Record<string, string>
  sortBy?: string
  sortOrder?: 'ascending' | 'descending'
}
