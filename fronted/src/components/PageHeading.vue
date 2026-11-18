<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { moduleDefinitions } from '../data/modules'

const props = defineProps<{ title?: string; eyebrow?: string; description?: string; accent?: string }>()
const route = useRoute()
const pageTitle = computed(() => props.title ?? String(route.meta.title ?? moduleDefinitions[String(route.meta.moduleKey)]?.title ?? 'CRM 工作台'))
</script>

<template>
  <header class="page-heading">
    <div class="page-heading__copy">
      <div class="page-eyebrow"><span class="eyebrow-mark"></span>{{ eyebrow ?? accent ?? String(route.meta.eyebrow ?? 'CUSTOMER RELATIONSHIP') }}</div>
      <h1>{{ pageTitle }}</h1>
      <p>{{ description ?? String(route.meta.description ?? moduleDefinitions[String(route.meta.moduleKey)]?.description ?? '让客户经营更清晰，让每一步协作都有回响。') }}</p>
    </div>
    <div class="page-heading__actions"><slot name="actions" /></div>
  </header>
</template>
