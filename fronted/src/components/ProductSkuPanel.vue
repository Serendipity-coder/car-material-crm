<script setup lang="ts">
import { computed } from 'vue'
import type { CrmRecord } from '../api/types'
import { formatMoney } from '../utils/format'

const props = withDefaults(defineProps<{
  record: CrmRecord
  showSensitivePrice?: boolean
}>(), {
  showSensitivePrice: false,
})

type RawRow = Record<string, unknown>
interface DisplayRow {
  key: string
  source: RawRow
}

const rawRecord = computed(() => props.record && typeof props.record === 'object'
  ? props.record as unknown as RawRow
  : null)

function present(value: unknown): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function firstPresent(row: RawRow, keys: string[]): unknown {
  for (const key of keys) {
    if (present(row[key])) return row[key]
  }
  return undefined
}

function hasAny(row: RawRow, keys: string[]): boolean {
  return keys.some((key) => present(row[key]))
}

const baseSkuKeys = ['sku', 'spec', 'unit', 'status', 'stock', 'price']
const childSkuKeys = {
  sku: ['sku', 'skuCode', 'productCode', 'code'],
  spec: ['spec', 'specification', 'model'],
  unit: ['unit'],
  status: ['status', 'salesStatus', 'saleStatus'],
  stock: ['stock', 'inventory'],
  price: ['price', 'standardPrice'],
}

const rows = computed<DisplayRow[]>(() => {
  const record = rawRecord.value
  if (!record) return []

  const result: DisplayRow[] = []
  // The existing product row is displayed as-is; it is not converted or persisted as a child SKU.
  if (hasAny(record, baseSkuKeys)) result.push({ key: 'product-row', source: record })

  // Only an actual array of object records supplied by the API is eligible for display.
  const apiSkus = record.skus
  if (Array.isArray(apiSkus)) {
    apiSkus.forEach((item, index) => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return
      const source = item as RawRow
      if (Object.values(childSkuKeys).some((keys) => hasAny(source, keys))) {
        result.push({ key: `api-sku-${index}`, source })
      }
    })
  }

  return result
})

const showStockColumn = computed(() => rows.value.some(({ source }, index) => index === 0 && source === rawRecord.value
  ? present(source.stock)
  : present(firstPresent(source, childSkuKeys.stock))))
const showPriceColumn = computed(() => props.showSensitivePrice && rows.value.some(({ source }, index) => index === 0 && source === rawRecord.value
  ? present(source.price)
  : present(firstPresent(source, childSkuKeys.price))))

function valueFor(row: DisplayRow, field: 'sku' | 'spec' | 'unit' | 'status' | 'stock' | 'price'): unknown {
  if (row.source === rawRecord.value) return row.source[field]
  return firstPresent(row.source, childSkuKeys[field])
}

function displayValue(value: unknown): string {
  return present(value) ? String(value) : '—'
}

function displayPrice(value: unknown): string {
  if (!present(value)) return '—'
  const amount = Number(value)
  return Number.isFinite(amount) ? formatMoney(amount) : String(value)
}

function statusTagType(value: unknown): 'success' | 'warning' | 'danger' | 'info' {
  const status = String(value ?? '')
  if (['启用', '在售', '销售中', '进行中'].includes(status)) return 'success'
  if (['待处理', '待上架', '待审核'].includes(status)) return 'warning'
  if (['已暂停', '已停用', '停售', '已下架'].includes(status)) return 'danger'
  return 'info'
}
</script>

<template>
  <section class="product-sku-panel" aria-labelledby="product-sku-panel-title">
    <header class="product-sku-panel__header">
      <div>
        <div class="product-sku-panel__eyebrow">PRODUCT SPECIFICATIONS</div>
        <h3 id="product-sku-panel-title">SKU / 规格信息</h3>
        <p>展示产品档案现有字段及接口已返回的 SKU 子记录</p>
      </div>
      <el-tag v-if="rows.length" type="success" effect="plain" round>{{ rows.length }} 条</el-tag>
    </header>

    <el-table v-if="rows.length" :data="rows" row-key="key" class="product-sku-table" size="small">
      <el-table-column label="产品编码" min-width="150">
        <template #default="{ row }"><span class="product-sku-code">{{ displayValue(valueFor(row, 'sku')) }}</span></template>
      </el-table-column>
      <el-table-column label="规格" min-width="170">
        <template #default="{ row }">{{ displayValue(valueFor(row, 'spec')) }}</template>
      </el-table-column>
      <el-table-column label="单位" width="90">
        <template #default="{ row }">{{ displayValue(valueFor(row, 'unit')) }}</template>
      </el-table-column>
      <el-table-column label="销售状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="present(valueFor(row, 'status'))" :type="statusTagType(valueFor(row, 'status'))" effect="light" round>
            {{ displayValue(valueFor(row, 'status')) }}
          </el-tag>
          <span v-else class="product-sku-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="showStockColumn" label="库存" width="110" align="right">
        <template #default="{ row }">{{ displayValue(valueFor(row, 'stock')) }}</template>
      </el-table-column>
      <el-table-column v-if="showPriceColumn" label="标准价格" width="140" align="right">
        <template #default="{ row }"><span class="product-sku-price">{{ displayPrice(valueFor(row, 'price')) }}</span></template>
      </el-table-column>
    </el-table>

    <el-empty v-else class="product-sku-empty" :image-size="52" description="暂无可展示的 SKU / 规格记录" />
  </section>
</template>

<style scoped>
.product-sku-panel {
  overflow: hidden;
  border: 1px solid #e5eee7;
  border-radius: 12px;
  background: #fff;
}

.product-sku-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 17px 19px 14px;
  border-bottom: 1px solid #edf2ee;
}

.product-sku-panel__eyebrow {
  color: #83a18b;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.05px;
}

.product-sku-panel__header h3 {
  margin: 5px 0 3px;
  color: #304b39;
  font-size: 15px;
  font-weight: 700;
}

.product-sku-panel__header p {
  margin: 0;
  color: #89978c;
  font-size: 12px;
}

.product-sku-table {
  width: 100%;
  --el-table-border-color: #edf2ee;
  --el-table-header-bg-color: #f7faf7;
  --el-table-row-hover-bg-color: #f7fbf8;
  --el-table-header-text-color: #718276;
  --el-table-text-color: #536458;
}

.product-sku-code {
  color: #3d7252;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.product-sku-price {
  color: #496a53;
  font-variant-numeric: tabular-nums;
}

.product-sku-muted {
  color: #a4afa6;
}

.product-sku-empty {
  padding: 8px 0 14px;
}

@media (max-width: 640px) {
  .product-sku-panel__header {
    padding: 14px;
  }
}
</style>
