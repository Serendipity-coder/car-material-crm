<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, Check, DataBoard, Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import DashboardView from './DashboardView.vue'

const auth = useAuthStore()
const isAuthenticated = computed(() => auth.isAuthenticated)
</script>

<template>
  <DashboardView v-if="isAuthenticated" />
  <main v-else class="public-home">
    <header class="public-home__nav">
      <router-link to="/" class="public-home__brand"><span class="brand-mark"><i></i><i></i><i></i></span><span>evergreen <small>CRM</small></span></router-link>
      <div class="public-home__actions"><router-link to="/login" class="public-home__login">登录</router-link><router-link to="/register" class="public-home__register">创建账号 <ArrowRight /></router-link></div>
    </header>
    <section class="public-home__hero">
      <div class="public-home__copy">
        <div class="page-eyebrow"><span class="eyebrow-mark"></span>EVERGREEN CRM WORKSPACE</div>
        <h1>让每一次<br /><em>客户连接</em><br />都有下一步。</h1>
        <p>从线索、客户到订单和回款，在一个清晰的工作台里管理汽修、洗车、美容与汽配耗材业务。</p>
        <div class="public-home__hero-actions"><router-link to="/login" class="public-home__primary">进入工作台 <ArrowRight /></router-link><router-link to="/register" class="public-home__secondary">免费创建账号</router-link></div>
        <div class="public-home__trust"><span><Check /> 客户经营</span><span><Check /> 销售协作</span><span><Check /> 团队成长</span></div>
      </div>
      <div class="public-home__preview">
        <div class="public-home__preview-top"><span class="public-home__preview-dots"><i></i><i></i><i></i></span><span>网站首页 · 工作台预览</span><span class="public-home__preview-status"><i></i>实时协作</span></div>
        <div class="public-home__preview-body"><div class="public-home__preview-sidebar"><span class="public-home__preview-logo"><span class="brand-mark"><i></i><i></i><i></i></span></span><b></b><b></b><b></b><b></b><b></b></div><div class="public-home__preview-content"><div class="public-home__preview-heading"><span></span><i></i></div><div class="public-home__preview-stats"><b></b><b></b><b></b></div><div class="public-home__preview-panels"><div></div><div></div></div><div class="public-home__preview-table"><i></i><i></i><i></i><i></i></div></div></div>
        <div class="public-home__preview-caption"><DataBoard /> 登录后查看授权范围内的经营概览、待办和业务数据</div>
      </div>
    </section>
    <section class="public-home__features"><article><span class="public-home__feature-icon"><DataBoard /></span><div><strong>经营概览</strong><p>销售、客户、商机和回款进展一目了然。</p></div></article><article><span class="public-home__feature-icon"><User /></span><div><strong>客户全生命周期</strong><p>从线索转化到跟进、报价和订单协作。</p></div></article><article><span class="public-home__feature-icon"><Lock /></span><div><strong>权限与数据安全</strong><p>按角色和部门控制业务数据访问范围。</p></div></article></section>
  </main>
</template>
