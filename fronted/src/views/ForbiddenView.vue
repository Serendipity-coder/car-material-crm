<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { allNavEntries } from '../data/navigation'
import { useAuthStore } from '../stores/auth'
const router = useRouter()
const auth = useAuthStore()
const fallbackPath = computed(() => allNavEntries.find((item) => auth.can(item.permission))?.path ?? '/profile')
</script>
<template>
  <main class="state-page state-page--embedded"><div class="state-card"><div class="state-icon state-icon--gold"><el-icon><Lock /></el-icon></div><div class="page-eyebrow">ACCESS CONTROL</div><h1>暂时无法访问</h1><p>您的角色没有这项操作权限。如需开通，请联系系统管理员。</p><el-button type="primary" @click="router.back()">返回上一页</el-button><el-button class="button-secondary" @click="router.push(fallbackPath)">前往可访问页面</el-button></div></main>
</template>

<style scoped>
.state-page--embedded { flex: 1; min-height: 0; background: transparent; }
</style>
