import dayjs from 'dayjs'

export function formatMoney(value: unknown): string {
  const amount = Number(value ?? 0)
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 }).format(Number.isFinite(amount) ? amount : 0)
}

export function formatDate(value: unknown): string {
  if (!value) return '—'
  const date = dayjs(String(value))
  return date.isValid() ? date.format('YYYY-MM-DD') : String(value)
}

export function formatNumber(value: unknown): string {
  return new Intl.NumberFormat('zh-CN').format(Number(value ?? 0))
}

export function escapeCsv(value: unknown): string {
  const text = String(value ?? '').replaceAll('"', '""')
  return `"${text}"`
}
