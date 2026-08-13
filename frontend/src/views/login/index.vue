<template>
  <main class="login-page">
    <section class="login-story">
      <div class="story-orb orb-one"></div><div class="story-orb orb-two"></div>
      <div class="story-content">
        <div class="story-brand"><span class="brand-mark">CF</span><span>CyberFlow</span></div>
        <div class="story-copy">
          <p class="story-kicker">E-COMMERCE INTELLIGENCE PLATFORM</p>
          <h1>让每一次采集，<br /><em>都创造价值。</em></h1>
          <p>统一管理站点、商品与订单数据，帮助团队更快发现机会，做出更准确的决策。</p>
        </div>
        <div class="story-features"><div v-for="feature in features" :key="feature.title" class="story-feature"><span>{{ feature.number }}</span><div><strong>{{ feature.title }}</strong><small>{{ feature.description }}</small></div></div></div>
        <div class="story-footer"><span class="live-dot"></span>所有系统运行正常 <span class="footer-divider"></span> CyberFlow v2.0</div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-form-wrap">
        <div class="mobile-brand"><span class="brand-mark">CF</span>CyberFlow</div>
        <div class="form-heading"><p>欢迎回来</p><h2>登录工作台</h2><span>输入您的账号信息以继续</span></div>
        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" size="large" @submit.prevent="handleLogin">
          <el-form-item prop="username" label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item prop="password" label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password autocomplete="current-password" @keyup.enter="handleLogin" />
          </el-form-item>
          <div class="form-options"><el-checkbox v-model="rememberMe">记住登录状态</el-checkbox><a href="#" @click.prevent>忘记密码？</a></div>
          <el-button native-type="submit" type="primary" :loading="loading" class="login-button">{{ loading ? '正在登录…' : '登录工作台' }}<el-icon v-if="!loading"><ArrowRight /></el-icon></el-button>
        </el-form>
        <p class="login-hint"><span>开发环境</span> 默认账号：<b>admin</b> / <b>admin123</b></p>
      </div>
      <p class="panel-copyright">© 2026 CyberFlow · 数据安全值得信赖</p>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(true)
const form = reactive({ username: 'admin', password: 'admin123' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const features = [
  { number: '01', title: '多源数据采集', description: '连接站点、商品与订单数据' },
  { number: '02', title: '实时运营洞察', description: '用数据驱动每一个重要决策' },
]

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const response = await login(form.username, form.password)
    userStore.setToken(response.data.token)
    await userStore.refreshUserInfo()
    ElMessage.success('登录成功，欢迎回来')
    router.push('/dashboard/overview')
  } catch {
    // request 拦截器负责统一展示接口错误。
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { display: grid; min-height: 100vh; grid-template-columns: minmax(480px, 1.05fr) minmax(440px, .95fr); background: #f8faff; }
.login-story { position: relative; display: flex; min-height: 100vh; overflow: hidden; color: #fff; background: #111d35; }
.login-story::after { position: absolute; inset: 0; pointer-events: none; opacity: .24; background-image: linear-gradient(#ffffff08 1px, transparent 1px), linear-gradient(90deg, #ffffff08 1px, transparent 1px); background-size: 54px 54px; content: ''; }
.story-orb { position: absolute; border-radius: 50%; filter: blur(1px); pointer-events: none; }.orb-one { width: 460px; height: 460px; top: -180px; right: -130px; background: radial-gradient(circle, #536ff155, transparent 67%); }.orb-two { width: 420px; height: 420px; bottom: -190px; left: -170px; background: radial-gradient(circle, #835de74d, transparent 68%); }
.story-content { position: relative; z-index: 1; display: flex; width: min(100%, 630px); flex-direction: column; padding: clamp(40px, 7vw, 88px) clamp(38px, 8vw, 120px); }
.story-brand, .mobile-brand { display: flex; align-items: center; gap: 11px; font-size: 19px; font-weight: 750; letter-spacing: -.03em; }.brand-mark { display: grid; width: 36px; height: 36px; place-items: center; border: 1px solid #ffffff4a; border-radius: 11px; color: #fff; font-size: 11px; font-weight: 800; background: linear-gradient(135deg, #617dff, #7657ef); box-shadow: 0 8px 24px #6275f355; }
.story-copy { margin-top: clamp(90px, 15vh, 170px); }.story-kicker { margin: 0 0 20px; color: #8fa5e7; font-size: 10px; font-weight: 800; letter-spacing: .2em; }.story-copy h1 { margin: 0; color: #fff; font-size: clamp(38px, 4vw, 56px); font-weight: 720; letter-spacing: -.065em; line-height: 1.16; }.story-copy h1 em { color: #91a9ff; font-style: normal; }.story-copy > p:last-child { max-width: 375px; margin: 23px 0 0; color: #9eabc3; font-size: 14px; line-height: 1.9; }
.story-features { display: grid; gap: 20px; margin-top: auto; padding-top: 80px; }.story-feature { display: flex; align-items: flex-start; gap: 14px; }.story-feature > span { color: #667dc9; font-size: 10px; font-weight: 800; letter-spacing: .1em; }.story-feature strong, .story-feature small { display: block; }.story-feature strong { color: #e1e7f7; font-size: 12px; }.story-feature small { margin-top: 5px; color: #7d8eaf; font-size: 11px; }.story-footer { display: flex; align-items: center; gap: 8px; margin-top: 44px; color: #7182a3; font-size: 10px; }.live-dot { width: 6px; height: 6px; border-radius: 50%; background: #43ca99; box-shadow: 0 0 0 4px #43ca991a; }.footer-divider { width: 1px; height: 11px; margin: 0 3px; background: #344461; }
.login-panel { display: flex; flex-direction: column; justify-content: center; min-height: 100vh; padding: 48px clamp(36px, 8vw, 120px); background: #fff; }.login-form-wrap { width: min(100%, 390px); margin: auto; }.mobile-brand { display: none; color: #17243b; }.form-heading p { margin: 0 0 10px; color: #536ff1; font-size: 13px; font-weight: 700; }.form-heading h2 { margin: 0; color: #17243b; font-size: 30px; letter-spacing: -.05em; }.form-heading span { display: block; margin-top: 10px; color: #9aa7b8; font-size: 13px; }.login-form { margin-top: 40px; }.login-form :deep(.el-form-item) { margin-bottom: 21px; }.login-form :deep(.el-form-item__label) { height: auto; margin-bottom: 8px; padding: 0; color: #3b4961; font-size: 12px; font-weight: 650; line-height: 1; }.login-form :deep(.el-input__wrapper) { min-height: 48px; padding: 0 14px; border: 1px solid #e3e8f0; border-radius: 9px; box-shadow: none; transition: border .2s, box-shadow .2s; }.login-form :deep(.el-input__wrapper.is-focus) { border-color: #536ff1; box-shadow: 0 0 0 3px #536ff11c; }.login-form :deep(.el-input__inner) { color: #263550; font-size: 13px; }.login-form :deep(.el-input__prefix) { color: #a3afc0; }.form-options { display: flex; align-items: center; justify-content: space-between; margin: 1px 0 25px; }.form-options :deep(.el-checkbox__label) { color: #8995a8; font-size: 11px; }.form-options a { color: #536ff1; font-size: 11px; text-decoration: none; }.login-button { width: 100%; height: 48px; border: 0; border-radius: 9px; font-size: 13px; font-weight: 650; background: linear-gradient(100deg, #536ff1, #6b5de7); box-shadow: 0 9px 20px #536ff12e; }.login-button .el-icon { margin-left: 7px; }.login-hint { margin: 23px 0 0; color: #adb6c5; font-size: 10px; text-align: center; }.login-hint span { margin-right: 5px; padding: 4px 6px; border-radius: 4px; color: #7e8cf0; background: #f0f2ff; }.login-hint b { color: #78869b; font-weight: 700; }.panel-copyright { margin: auto 0 0; color: #b7c0ce; font-size: 10px; text-align: center; }
@media (max-width: 800px) { .login-page { display: block; }.login-story { display: none; }.login-panel { padding: 35px 28px; }.mobile-brand { display: flex; margin-bottom: 72px; } }
@media (max-height: 720px) and (min-width: 801px) { .story-copy { margin-top: 80px; }.story-features { padding-top: 40px; } }
</style>
