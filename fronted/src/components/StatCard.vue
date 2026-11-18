<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ label: string; value: string | number; note?: string; tone?: 'mint' | 'gold' | 'rose'; icon?: string; trend?: string }>()
const toneClass = computed(() => `stat-card--${props.tone ?? 'ink'}`)
</script>

<template>
  <article class="stat-card" :class="toneClass">
    <div class="stat-card__top"><span>{{ label }}</span><span v-if="icon" class="stat-card__icon"><slot name="icon" /></span></div>
    <div class="stat-card__value">{{ value }}</div>
    <div class="stat-card__foot"><span>{{ note ?? '较上期保持稳定' }}</span><span v-if="trend" class="stat-card__trend">{{ trend }}</span></div>
  </article>
</template>
