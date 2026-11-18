<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { allNavEntries } from '../data/navigation'
import { useAuthStore } from '../stores/auth'
const router = useRouter()
const auth = useAuthStore()
const fallbackPath = computed(() => allNavEntries.find((item) => auth.can(item.permission))?.path ?? '/profile')
</script>
<template><main class="state-page state-page--embedded"><div class="state-card"><div class="state-code">404</div><div class="page-eyebrow">PAGE NOT FOUND</div><h1>这里暂时没有内容</h1><p>检查一下地址，或返回可访问页面继续工作。</p><el-button type="primary" @click="router.push(fallbackPath)">返回可访问页面</el-button></div></main></template>

<style scoped>
.state-page--embedded { flex: 1; min-height: 0; background: transparent; }
</style>
