<script setup lang="ts">
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AppShell from './layouts/AppShell.vue'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const auth = useAuthStore()
</script>

<template>
  <el-config-provider :locale="zhCn" :button="{ autoInsertSpace: true }">
    <router-view v-slot="{ Component }">
      <AppShell v-if="auth.isAuthenticated && !route.meta.public"><component :is="Component" :key="route.path" /></AppShell>
      <component :is="Component" v-else :key="route.path" />
    </router-view>
  </el-config-provider>
</template>
