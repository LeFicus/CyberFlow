<!--
  LoginPage - 登录页
  - “记住登录状态”决定 token 写入 localStorage 还是 sessionStorage
  - “忘记密码”给出可执行提示，不再是空链接
-->
<template>
  <main class="login-page">
    <section class="login-story">
      <div class="story-orb orb-one" aria-hidden="true"></div>
      <div class="story-orb orb-two" aria-hidden="true"></div>
      <div class="story-content">
        <div class="story-brand">
          <span class="brand-mark">CF</span>
          <span>CyberFlow</span>
        </div>

        <div class="story-copy">
          <p class="story-kicker">E-COMMERCE INTELLIGENCE PLATFORM</p>
          <h1>让每一次采集，<br /><em>都创造价值。</em></h1>
          <p>统一管理站点、商品与订单数据，帮助团队更快发现机会，做出更准确的决策。</p>
        </div>

        <div class="story-features">
          <div v-for="feature in features" :key="feature.title" class="story-feature">
            <span>{{ feature.number }}</span>
            <div>
              <strong>{{ feature.title }}</strong>
              <small>{{ feature.description }}</small>
            </div>
          </div>
        </div>

        <div class="story-footer">
          <span>数据仅对授权成员可见</span>
          <span class="footer-divider" aria-hidden="true"></span>
          <span>CyberFlow v2.0</span>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-form-wrap">
        <div class="mobile-brand">
          <span class="brand-mark">CF</span>
          <span>CyberFlow</span>
        </div>

        <header class="form-heading">
          <p>欢迎回来</p>
          <h2>登录工作台</h2>
          <span>输入您的账号信息以继续</span>
        </header>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username" label="用户名">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item prop="password" label="密码">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住登录状态</el-checkbox>
            <button type="button" class="text-link" @click="handleForgotPassword">忘记密码？</button>
          </div>

          <el-button
            native-type="submit"
            type="primary"
            :loading="loading"
            class="login-button"
          >
            {{ loading ? '正在登录…' : '登录工作台' }}
            <el-icon v-if="!loading"><ArrowRight /></el-icon>
          </el-button>
        </el-form>
      </div>

      <p class="panel-copyright">© 2026 CyberFlow · 数据安全值得信赖</p>
    </section>
  </main>
</template>

<script setup>
import { h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Lock, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(true)
const form = reactive({ username: '', password: '' })
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
    userStore.setToken(response.data.token, rememberMe.value)
    await userStore.refreshUserInfo()
    ElMessage.success('登录成功，欢迎回来')
    await router.push('/dashboard/overview')
    if ((response.data.userInfo?.roles || []).includes('ROLE_ADMIN') && response.data.siteHealthAlert) {
      await showSiteHealthAlert(response.data.siteHealthAlert)
    }
  } catch {
    // request 拦截器负责统一展示接口错误。
  } finally {
    loading.value = false
  }
}

function handleForgotPassword() {
  ElMessageBox.alert('请联系系统管理员重置密码，管理员可在“用户管理”中更新账号信息。', '忘记密码', {
    type: 'info',
    confirmButtonText: '我知道了',
  })
}

function showSiteHealthAlert(alert) {
  const rows = (alert.sites || []).map(site => h('li', { style: 'margin: 7px 0;' }, [
    h('strong', site.domain),
    `：${site.reason || '状态异常'}${site.serverName ? `（${site.serverName}）` : ''}`,
  ]))
  const children = [
    h('p', { style: 'margin: 0 0 10px; line-height: 1.7;' },
      `你当前有 ${alert.total} 个异常站点，请尽快检查并处理。`),
    h('ul', { style: 'max-height: 300px; margin: 0; padding-left: 20px; overflow: auto; line-height: 1.5;' }, rows),
  ]
  if (alert.remaining > 0) {
    children.push(h('p', { style: 'margin: 10px 0 0; color: #909399;' },
      `另有 ${alert.remaining} 个异常站点，请前往站点健康检查页面查看。`))
  }
  return ElMessageBox.alert(h('div', children), '个人站点异常提醒', {
    type: 'warning',
    confirmButtonText: '我知道了',
    closeOnClickModal: false,
  })
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, .92fr);
  background: var(--cf-canvas);
}

/* ---- story panel ---- */

.login-story {
  position: relative;
  display: flex;
  min-height: 100vh;
  overflow: hidden;
  color: var(--cf-on-primary);
  background: var(--cf-nav-bg);
}

.login-story::after {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgb(255 255 255 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 3%) 1px, transparent 1px);
  background-size: 56px 56px;
  content: '';
  pointer-events: none;
}

.story-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.orb-one {
  top: -180px;
  right: -130px;
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, rgb(79 107 237 / 36%), transparent 67%);
}

.orb-two {
  bottom: -190px;
  left: -170px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgb(112 89 224 / 30%), transparent 68%);
}

.story-content {
  position: relative;
  z-index: 1;
  display: flex;
  width: min(100%, 640px);
  flex-direction: column;
  padding: clamp(40px, 6vw, 80px) clamp(36px, 7vw, 104px);
}

.story-brand,
.mobile-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -.03em;
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 28%);
  border-radius: 10px;
  color: var(--cf-on-primary);
  background: linear-gradient(135deg, var(--cf-primary), var(--cf-accent));
  box-shadow: 0 8px 24px rgb(79 107 237 / 35%);
  font-size: 11px;
  font-weight: 700;
}

.story-copy {
  margin-top: clamp(72px, 13vh, 150px);
}

.story-kicker {
  margin-bottom: 18px;
  color: var(--cf-primary-soft-strong);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .18em;
}

.story-copy h1 {
  color: var(--cf-on-primary);
  font-size: clamp(36px, 4vw, 54px);
  font-weight: 600;
  letter-spacing: -.045em;
  line-height: 1.16;
}

.story-copy h1 em {
  color: var(--cf-primary-soft-strong);
  font-style: normal;
}

.story-copy > p:last-child {
  max-width: 380px;
  margin-top: 22px;
  color: var(--cf-nav-text);
  font-size: var(--cf-text-body);
  line-height: 1.9;
  opacity: .82;
}

.story-features {
  display: grid;
  gap: 18px;
  margin-top: auto;
  padding-top: 64px;
}

.story-feature {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.story-feature > span {
  color: var(--cf-primary-soft-strong);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .1em;
  opacity: .8;
}

.story-feature strong,
.story-feature small {
  display: block;
}

.story-feature strong {
  color: var(--cf-nav-text);
  font-size: var(--cf-text-body);
  font-weight: 600;
}

.story-feature small {
  margin-top: 4px;
  color: var(--cf-nav-muted);
  font-size: var(--cf-text-small);
}

.story-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 40px;
  color: var(--cf-nav-muted);
  font-size: var(--cf-text-small);
}

.footer-divider {
  width: 1px;
  height: 11px;
  background: rgb(255 255 255 / 16%);
}

/* ---- form panel ---- */

.login-panel {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  justify-content: center;
  padding: 48px clamp(32px, 7vw, 96px);
  background: var(--cf-surface);
}

.login-form-wrap {
  width: min(100%, 382px);
  margin: auto;
}

.mobile-brand {
  display: none;
  color: var(--cf-ink);
}

.form-heading p {
  margin-bottom: 8px;
  color: var(--cf-primary);
  font-size: var(--cf-text-small);
  font-weight: 600;
}

.form-heading h2 {
  color: var(--cf-ink);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -.03em;
  line-height: 1.25;
}

.form-heading > span {
  display: block;
  margin-top: 8px;
  color: var(--cf-muted);
  font-size: var(--cf-text-body);
}

.login-form {
  margin-top: 32px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 6px;
  padding: 0;
  color: var(--cf-ink-2);
  font-size: var(--cf-text-small);
  font-weight: 600;
  line-height: 1.4;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 48px;
  padding: 0 14px;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-surface);
  box-shadow: none;
  transition: border-color var(--cf-dur-fast) var(--cf-ease), box-shadow var(--cf-dur-fast) var(--cf-ease);
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--cf-line-strong);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--cf-primary);
  box-shadow: 0 0 0 3px rgb(79 107 237 / 10%);
}

.login-form :deep(.el-input__inner) {
  color: var(--cf-ink);
  font-size: var(--cf-text-body);
}

.login-form :deep(.el-input__prefix) {
  color: var(--cf-subtle);
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
  margin: 2px 0 22px;
}

.form-options :deep(.el-checkbox__label) {
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.text-link {
  padding: 2px;
  border-radius: 4px;
  color: var(--cf-primary);
  background: transparent;
  font-size: var(--cf-text-small);
  font-weight: 600;
  cursor: pointer;
}

.text-link:hover {
  color: var(--cf-primary-hover);
}

.login-button {
  width: 100%;
  height: 48px;
  border: 0;
  border-radius: var(--cf-radius-sm);
  color: var(--cf-on-primary);
  background: linear-gradient(100deg, var(--cf-primary), var(--cf-accent));
  box-shadow: 0 9px 20px -10px rgb(79 107 237 / 70%);
  font-size: var(--cf-text-body);
  font-weight: 600;
}

.login-button:hover,
.login-button:focus {
  background: linear-gradient(100deg, var(--cf-primary-hover), var(--cf-accent));
  box-shadow: 0 11px 24px -10px rgb(79 107 237 / 80%);
}

.login-button .el-icon {
  margin-left: 7px;
}

.panel-copyright {
  margin: auto 0 0;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  text-align: center;
}

@media (max-width: 1024px) {
  .login-page {
    display: block;
  }

  .login-story {
    display: none;
  }

  .login-panel {
    min-height: 100vh;
    padding: 40px 24px;
  }

  .mobile-brand {
    display: flex;
    margin-bottom: 48px;
  }
}

@media (max-width: 640px) {
  .login-panel {
    padding: 28px 20px;
  }

  .mobile-brand {
    margin-bottom: 32px;
  }

  .form-heading h2 {
    font-size: 26px;
  }

  .login-form {
    margin-top: 26px;
  }
}

@media (max-height: 720px) and (min-width: 1025px) {
  .story-copy {
    margin-top: 64px;
  }

  .story-features {
    padding-top: 32px;
  }
}
</style>
