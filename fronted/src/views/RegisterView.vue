<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Lock, Phone, User } from '@element-plus/icons-vue'
import { authApi } from '../api/auth'
import { useMockData } from '../api/client'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const receivedCode = ref('')
const codeError = ref('')
let timer: ReturnType<typeof setInterval> | undefined
const form = reactive({ account: '', phone: '', verificationCode: '', password: '', confirmPassword: '' })
const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => value === form.password ? callback() : callback(new Error('两次输入的密码不一致'))
  const rules: FormRules = {
  account: [{ required: true, message: '请输入登录用户名', trigger: 'blur' }, { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,63}$/, message: '用户名需以字母开头，至少 3 位', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^\d{7,15}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  verificationCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }, { pattern: /^\d{4}$/, message: '验证码必须为 4 位数字', trigger: 'blur' }],
  password: [{ required: true, message: '请设置登录密码', trigger: 'blur' }, { min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请再次输入密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }],
}
async function sendCode(): Promise<void> {
  if (sending.value || countdown.value > 0) return
  const valid = await formRef.value?.validateField('phone').catch(() => false)
  if (valid === false) return
  sending.value = true
  codeError.value = ''
  receivedCode.value = ''
  try {
    const result = await authApi.sendRegistrationCode({ phone: form.phone })
    receivedCode.value = result.demoCode ?? result.code ?? ''
    if (receivedCode.value) ElMessage.success('验证码已发送，请查看验证码提示')
    else receivedCode.value = result.message || '验证码已发送，请查看固定测试手机号'
    countdown.value = 60
    timer = setInterval(() => { countdown.value -= 1; if (countdown.value <= 0 && timer) clearInterval(timer) }, 1000)
  } catch (error) { codeError.value = error instanceof Error ? error.message : '验证码发送失败' }
  finally { sending.value = false }
}
async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const session = await authApi.register({ ...form })
    auth.applySession(session)
    ElMessage.success('账号创建成功，已自动进入工作台')
    await router.replace('/')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '注册失败') }
  finally { loading.value = false }
}
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <main class="auth-page auth-page--register">
    <section class="auth-visual"><div class="auth-brand"><span class="brand-mark"><i></i><i></i><i></i></span><span>evergreen <small>CRM</small></span></div><div class="auth-copy"><div class="page-eyebrow"><span class="eyebrow-mark"></span>GROW WITH YOUR CUSTOMERS</div><h1>好的关系，<br /><em>从理解开始。</em></h1><p>创建工作台账号，和团队一起把客户经营做得更有序、更长久。</p></div><div class="auth-orbit auth-orbit--one"></div><div class="auth-orbit auth-orbit--two"></div><div class="auth-visual-footer"><span class="status-pulse"></span>从一次连接，开始长期合作 <span>© 2026 Evergreen CRM</span></div></section>
    <section class="auth-form-side"><div class="auth-form-wrap"><div class="auth-mobile-brand"><span class="brand-mark"><i></i><i></i><i></i></span> evergreen CRM</div><router-link to="/login" class="back-link"><ArrowLeft /> 返回登录</router-link><div class="auth-form-heading"><div class="page-eyebrow">CREATE YOUR ACCOUNT</div><h2>创建账号</h2><p>验证手机号并设置您的登录密码。</p></div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @submit.prevent="submit">
          <el-form-item label="登录用户名" prop="account"><el-input v-model="form.account" size="large" :prefix-icon="User" placeholder="字母开头，3-64 位" autocomplete="username" /></el-form-item>
          <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" size="large" :prefix-icon="Phone" placeholder="请输入手机号" autocomplete="tel" /></el-form-item>
          <el-form-item label="短信验证码" prop="verificationCode"><el-input v-model="form.verificationCode" size="large" placeholder="4 位验证码" maxlength="4" inputmode="numeric"><template #append><button type="button" class="code-button" :disabled="sending || countdown > 0" @click="sendCode">{{ countdown ? `${countdown}s 后重试` : '获取验证码' }}</button></template></el-input></el-form-item>
          <div v-if="receivedCode" class="code-hint"><span class="code-hint__dot"></span><span>本次验证码：<strong>{{ receivedCode }}</strong></span><small>验证码有效期 2 分钟</small></div>
          <div v-if="codeError" class="code-hint code-hint--error"><span class="code-hint__dot"></span><span>{{ codeError }}</span></div>
        <el-form-item label="设置密码" prop="password"><el-input v-model="form.password" size="large" :prefix-icon="Lock" type="password" placeholder="至少 6 位" autocomplete="new-password" show-password /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" size="large" :prefix-icon="Lock" type="password" placeholder="再次输入密码" autocomplete="new-password" show-password /></el-form-item>
        <div v-if="useMockData" class="demo-tip"><span class="demo-tip__dot"></span><div><strong>前端演示模式</strong><span>验证码通过提示展示；Mock 注册数据仅保存在本浏览器。请勿使用真实密码或在其他服务中复用的密码。</span></div></div>
        <el-button type="primary" native-type="submit" class="auth-submit" size="large" :loading="loading">创建账号</el-button>
      </el-form>
      <div class="auth-form-bottom"><span>已有账号？</span><router-link to="/login">立即登录</router-link></div>
    </div></section>
  </main>
</template>
