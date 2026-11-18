<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowRight, Lock, Phone, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'
import { useMockData } from '../api/client'
import { allNavEntries } from '../data/navigation'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const receivedCode = ref('')
const codeError = ref('')
let timer: ReturnType<typeof setInterval> | undefined
const showPassword = ref(false)
const form = reactive({
  account: useMockData ? 'admin' : String(route.query.account ?? ''),
  password: useMockData ? 'admin123' : '',
  phone: useMockData ? '13800001001' : String(route.query.phone ?? ''),
  verificationCode: useMockData ? '2468' : '',
})
const rules: FormRules = {
  account: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^\d{7,15}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  verificationCode: [{ required: true, message: '请输入短信验证码', trigger: 'blur' }, { pattern: /^\d{4}$/, message: '验证码必须为 4 位数字', trigger: 'blur' }],
}
const codeLabel = computed(() => countdown.value > 0 ? `${countdown.value}s 后重试` : sendingCode.value ? '发送中…' : '获取验证码')

async function sendCode(): Promise<void> {
  if (sendingCode.value || countdown.value > 0) return
  const valid = await formRef.value?.validateField(['account', 'phone']).catch(() => false)
  if (valid === false) return
  sendingCode.value = true
  codeError.value = ''
  receivedCode.value = ''
  try {
    const result = await authApi.sendLoginCode({ account: form.account, phone: form.phone })
    receivedCode.value = result.demoCode ?? result.code ?? ''
    if (receivedCode.value) ElMessage.success('验证码已发送，请查看验证码提示')
    else receivedCode.value = result.message || '验证码已发送，请查看固定测试手机号'
    countdown.value = 60
    timer = setInterval(() => { countdown.value -= 1; if (countdown.value <= 0 && timer) clearInterval(timer) }, 1000)
  } catch (error) { codeError.value = error instanceof Error ? error.message : '发送验证码失败' }
  finally { sendingCode.value = false }
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login({ ...form })
    ElMessage.success(`欢迎回来，${auth.user?.name}`)
    const requested = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const requestedPermission = requested ? String(router.resolve(requested).meta.permission ?? '') : ''
    const destination = requested && auth.can(requestedPermission) ? requested : allNavEntries.find((item) => auth.can(item.permission))?.path ?? '/403'
    await router.replace(destination)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后再试') }
  finally { loading.value = false }
}

onMounted(() => { if (useMockData) authApi.seedDemoCode() })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <main class="auth-page">
    <section class="auth-visual">
      <div class="auth-brand"><span class="brand-mark"><i></i><i></i><i></i></span><span>evergreen <small>CRM</small></span></div>
      <div class="auth-copy">
        <div class="page-eyebrow"><span class="eyebrow-mark"></span>RELATIONSHIPS, MADE CLEAR</div>
        <h1>让每一次<br /><em>客户连接</em><br />都有下一步。</h1>
        <p>从线索到回款，从团队协作到客户成功。把重要的经营信息，放在恰到好处的位置。</p>
      </div>
      <div class="auth-orbit auth-orbit--one"></div><div class="auth-orbit auth-orbit--two"></div>
      <div class="auth-visual-footer"><span class="status-pulse"></span>客户经营 · 销售协作 · 团队成长 <span>© 2026 Evergreen CRM</span></div>
    </section>
    <section class="auth-form-side">
      <div class="auth-form-wrap">
        <div class="auth-mobile-brand"><span class="brand-mark"><i></i><i></i><i></i></span> evergreen CRM</div>
        <div class="auth-form-heading"><div class="page-eyebrow">WELCOME BACK</div><h2>登录工作台</h2><p>输入您的账号信息，继续推进客户经营。</p></div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @submit.prevent="submit">
          <el-form-item label="登录账号" prop="account"><el-input v-model="form.account" size="large" :prefix-icon="User" placeholder="请输入账号" autocomplete="username" /></el-form-item>
          <el-form-item label="登录密码" prop="password"><el-input v-model="form.password" size="large" :prefix-icon="Lock" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password"><template #suffix><button type="button" class="field-action" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></template></el-input></el-form-item>
          <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" size="large" :prefix-icon="Phone" placeholder="请输入账号绑定的手机号" autocomplete="tel" /></el-form-item>
          <el-form-item label="短信验证码" prop="verificationCode"><el-input v-model="form.verificationCode" size="large" placeholder="4 位验证码" maxlength="4" inputmode="numeric"><template #append><button type="button" class="code-button" :disabled="sendingCode || countdown > 0" @click="sendCode">{{ codeLabel }}</button></template></el-input></el-form-item>
          <div v-if="receivedCode" class="code-hint"><span class="code-hint__dot"></span><span>本次验证码：<strong>{{ receivedCode }}</strong></span><small>验证码有效期 2 分钟</small></div>
          <div v-if="codeError" class="code-hint code-hint--error"><span class="code-hint__dot"></span><span>{{ codeError }}</span></div>
          <div v-if="useMockData" class="demo-tip"><span class="demo-tip__dot"></span><div><strong>演示模式 · 已预填管理员账号</strong><span>账号 admin · 密码 admin123 · 手机号 13800001001 · 验证码 2468，可直接点登录；销售账号 sales / 123456</span></div></div>
          <el-button type="primary" native-type="submit" class="auth-submit" size="large" :loading="loading">登录工作台 <ArrowRight /></el-button>
        </el-form>
        <div class="auth-form-bottom"><span>还没有账号？</span><router-link to="/register">创建账号</router-link><span class="secure-note"><Lock /> 安全加密连接</span></div>
      </div>
    </section>
  </main>
</template>
