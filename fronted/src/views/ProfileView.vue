<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, UserFilled } from '@element-plus/icons-vue'
import PageHeading from '../components/PageHeading.vue'
import StatCard from '../components/StatCard.vue'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { http, useMockData } from '../api/client'

const auth = useAuthStore()
const profileRef = ref<FormInstance>()
const passwordRef = ref<FormInstance>()
const passwordOpen = ref(false)
const saving = ref(false)
const profile = reactive({ name: '', phone: '', email: '', position: '', department: '' })
const baseline = reactive({ name: '', phone: '', email: '' })
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileRules: FormRules = { name: [{ required: true, message: '请输入姓名', trigger: 'blur' }], phone: [{ pattern: /^\d{7,15}$/, message: '请输入有效手机号', trigger: 'blur' }], email: [{ type: 'email', message: '请输入有效邮箱', trigger: 'blur' }] }
const passwordRules: FormRules = { oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }], newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '密码至少 6 位', trigger: 'blur' }], confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }, { validator: (_rule, value: string, callback) => value === password.newPassword ? callback() : callback(new Error('两次输入的密码不一致')), trigger: 'blur' }] }
function hasUnsavedEdits(): boolean { return profile.name !== baseline.name || profile.phone !== baseline.phone || profile.email !== baseline.email }
function hydrate(): void {
  const user = auth.user
  Object.assign(profile, { position: user?.position ?? '', department: user?.department ?? '' })
  if (hasUnsavedEdits()) return
  Object.assign(profile, { name: user?.name ?? '', phone: user?.phone ?? '', email: user?.email ?? '' })
  Object.assign(baseline, { name: profile.name, phone: profile.phone, email: profile.email })
}
async function saveProfile(): Promise<void> {
  await profileRef.value?.validate()
  saving.value = true
  try {
    const submitted = { name: profile.name, phone: profile.phone, email: profile.email }
    if (useMockData) {
      Object.assign(baseline, submitted)
      if (auth.user) auth.applySession({ accessToken: auth.token, user: { ...auth.user, ...submitted } })
      hydrate()
      ElMessage.success('个人资料已更新')
    } else {
      await http.patch('/auth/me', submitted)
      Object.assign(baseline, submitted)
      try {
        await auth.refreshProfile()
        hydrate()
        ElMessage.success('个人资料已更新')
      } catch {
        Object.assign(baseline, submitted)
        ElMessage.warning('资料已提交，但权威资料同步暂不可用；刷新后会再次同步。')
      }
    }
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资料保存失败') }
  finally { saving.value = false }
}
async function changePassword(): Promise<void> {
  await passwordRef.value?.validate()
  saving.value = true
  try {
    await authApi.changePassword({ oldPassword: password.oldPassword, newPassword: password.newPassword })
    ElMessage.success('密码已更新')
    passwordOpen.value = false
    Object.assign(password, { oldPassword: '', newPassword: '', confirmPassword: '' })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '密码更新失败') }
  finally { saving.value = false }
}
watch(() => auth.user, hydrate, { deep: true })
onMounted(hydrate)
</script>

<template>
  <div class="org-page profile-page">
    <PageHeading title="个人资料" eyebrow="YOUR ACCOUNT" description="管理个人联系方式与登录安全设置。" />
    <section class="stats-grid org-stats"><StatCard label="当前角色" :value="auth.user?.role ?? 'CRM 用户'" note="权限根据角色自动计算" /><StatCard label="所在部门" :value="profile.department || '—'" note="组织成员信息" tone="mint" /><StatCard label="账号状态" value="正常" note="当前登录会话有效" tone="gold" /><StatCard label="数据模式" :value="useMockData ? '本地演示' : '真实 API'" note="由 VITE_USE_MOCK 环境变量控制" tone="rose" /></section>
    <section class="profile-layout">
      <article class="panel profile-card">
        <div class="profile-card-head"><span class="profile-avatar"><UserFilled /></span><div><h2>{{ auth.user?.name }}</h2><span>{{ auth.user?.account }} · {{ profile.position || 'CRM 用户' }}</span></div><el-tag type="success" effect="light" round>已登录</el-tag></div>
        <el-divider />
        <el-form ref="profileRef" :model="profile" :rules="profileRules" label-position="top" class="record-form record-form--two">
          <el-form-item label="真实姓名" prop="name"><el-input v-model="profile.name" /></el-form-item>
          <el-form-item label="登录账号"><el-input :model-value="auth.user?.account" disabled /></el-form-item>
          <el-form-item label="手机号码" prop="phone"><el-input v-model="profile.phone" /></el-form-item>
          <el-form-item label="电子邮箱" prop="email"><el-input v-model="profile.email" /></el-form-item>
          <el-form-item label="所在部门"><el-input v-model="profile.department" disabled /></el-form-item>
          <el-form-item label="当前岗位"><el-input v-model="profile.position" disabled /></el-form-item>
        </el-form>
        <div class="profile-footer-actions"><el-button class="button-secondary" :icon="Lock" @click="passwordOpen = true">修改密码</el-button><el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button></div>
      </article>
      <aside class="panel profile-security"><div class="panel-kicker">ACCOUNT SECURITY</div><h2>账号安全</h2><p>保护您的登录凭证与个人资料，仅在可信设备上保持登录。</p><div class="security-row"><span class="security-icon"><Lock /></span><div><strong>登录密码</strong><small>定期更新密码有助于保护账号</small></div><el-button text type="primary" @click="passwordOpen = true">修改</el-button></div><div class="security-row"><span class="security-icon security-icon--mint"><UserFilled /></span><div><strong>角色与权限</strong><small>{{ auth.permissions.includes('*') ? '超级管理员 · 全部系统权限' : `${auth.permissions.length} 项访问权限` }}</small></div><el-tag type="success" effect="plain" round>正常</el-tag></div><el-alert title="管理员可在角色管理中调整团队成员权限。" type="info" :closable="false" show-icon /></aside>
    </section>
    <el-dialog v-model="passwordOpen" title="修改登录密码" width="min(480px, 92vw)" destroy-on-close><el-form ref="passwordRef" :model="password" :rules="passwordRules" label-position="top"><el-form-item label="当前密码" prop="oldPassword"><el-input v-model="password.oldPassword" type="password" show-password /></el-form-item><el-form-item label="新密码" prop="newPassword"><el-input v-model="password.newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item><el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="password.confirmPassword" type="password" show-password /></el-form-item></el-form><template #footer><el-button class="button-secondary" @click="passwordOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="changePassword">更新密码</el-button></template></el-dialog>
  </div>
</template>
