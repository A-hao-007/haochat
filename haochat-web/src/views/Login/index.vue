<script setup lang="ts">
import { onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useWsLoginStore, LoginStatus } from '@/stores/ws'
import QrCode from 'qrcode.vue'
import apis from '@/services/apis'
import { computedToken } from '@/services/request'
import { ElMessage } from 'element-plus'
import { getRecentAccounts, upsertAccount, type RecentAccount } from '@/utils/accountManager'

const router = useRouter()
const userStore = useUserStore()

if (userStore.isSign) router.replace('/')

const isLogin = ref(true)
const loading = ref(false)
const remember = ref(localStorage.getItem('REMEMBER_LOGIN') !== '0')
const recentAccounts = ref<RecentAccount[]>(getRecentAccounts())
const rememberedName = localStorage.getItem('REMEMBER_USERNAME') || ''
const loginForm = reactive({ username: remember.value ? rememberedName : '', password: '' })
const registerForm = reactive({
  username: '',
  name: '',
  password: '',
  confirmPassword: '',
  email: '',
  emailCode: '',
})
const sendingRegisterCode = ref(false)
const registerCodeCountdown = ref(0)

// 登录成功后统一处理登录态 + 记录最近账号
const applyLoginSuccess = (res: any) => {
  const accessToken = res.accessToken || res.token
  localStorage.setItem('TOKEN', accessToken)
  res.refreshToken && localStorage.setItem('REFRESH_TOKEN', res.refreshToken)
  localStorage.setItem(
    'USER_INFO',
    JSON.stringify({ uid: res.uid, name: res.name, avatar: res.avatar }),
  )
  localStorage.setItem('REMEMBER_LOGIN', remember.value ? '1' : '0')
  if (remember.value) {
    localStorage.setItem('REMEMBER_USERNAME', res.username || loginForm.username.trim())
  } else {
    localStorage.removeItem('REMEMBER_USERNAME')
  }
  upsertAccount(
    {
      uid: res.uid,
      name: res.name,
      avatar: res.avatar,
      username: res.username,
      token: accessToken,
      lastLogin: Date.now(),
    },
    remember.value,
  )
  computedToken.clear()
  computedToken.get()
  userStore.isSign = true
  userStore.userInfo = { uid: res.uid, name: res.name, avatar: res.avatar }
  // 整页刷新进入主页：换账号登录时内存里可能还留着上一个账号的消息/会话/当前房间等状态，
  // SPA 内部跳转不会清掉它们（曾导致新账号看到上一账号的聊天内容），刷新一次彻底从新 token 初始化
  window.location.replace('/')
}

const handleLogin = async () => {
  const id = loginForm.username.trim()
  if (!id) {
    ElMessage.warning('请输入用户名或邮箱')
    return
  }
  if (!loginForm.password) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    // 含 @ 视为邮箱登录
    const res = id.includes('@')
      ? await apis.emailLogin({ email: id, password: loginForm.password }).send()
      : await apis.login({ username: id, password: loginForm.password }).send()
    applyLoginSuccess(res)
    ElMessage.success('登录成功')
  } catch (err: any) {
    ElMessage.error(err?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

// —— 邮箱验证码登录 ——
const loginMode = ref<'password' | 'emailCode' | 'wechat'>('password')
const codeLoginForm = reactive({ email: '', code: '' })
const sendingLoginCode = ref(false)
const loginCodeCountdown = ref(0)

const sendLoginCode = async () => {
  const email = codeLoginForm.email.trim()
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  sendingLoginCode.value = true
  try {
    await apis.loginEmailCode({ email }).send()
    ElMessage.success('验证码已发送，请查收邮箱')
    loginCodeCountdown.value = 60
    const timer = setInterval(() => {
      loginCodeCountdown.value--
      if (loginCodeCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sendingLoginCode.value = false
  }
}

// —— 微信扫码登录 ——
// 二维码通过 WebSocket 请求/下发（RequestLoginQrCode -> LoginQrCode），
// 扫码关注后后端经 MQ 完成登录，LoginSuccess 的 WS 处理已负责写入 token/用户信息，这里只管跳转
const loginStore = useWsLoginStore()
const qrTimeout = ref(false)
let qrRetryTimer: ReturnType<typeof setInterval> | null = null
const stopQrRetry = () => {
  if (qrRetryTimer) {
    clearInterval(qrRetryTimer)
    qrRetryTimer = null
  }
}
watch(loginMode, (mode) => {
  stopQrRetry()
  if (mode !== 'wechat') return
  qrTimeout.value = false
  let attempts = 0
  loginStore.getLoginQrCode()
  // WS 可能尚未建连导致首次请求被丢弃，间隔重试几次；仍拿不到就提示未配置
  qrRetryTimer = setInterval(() => {
    if (loginStore.loginQrCode) {
      stopQrRetry()
      return
    }
    attempts++
    if (attempts >= 3) {
      qrTimeout.value = true
      stopQrRetry()
      return
    }
    loginStore.getLoginQrCode()
  }, 2000)
})
watch(
  () => loginStore.loginStatus,
  (s) => {
    if (s === LoginStatus.Success) {
      // 同 applyLoginSuccess：整页刷新，避免继承上一个账号的内存状态
      window.location.replace('/')
    }
  },
)
onUnmounted(stopQrRetry)

const handleCodeLogin = async () => {
  const email = codeLoginForm.email.trim()
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  if (!codeLoginForm.code.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    const res = await apis.emailCodeLogin({ email, code: codeLoginForm.code.trim() }).send()
    applyLoginSuccess(res)
    ElMessage.success('登录成功')
  } catch (err: any) {
    ElMessage.error(err?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

// 切换账号：有有效 token 则免密登录，否则回填用户名让其输入密码
const switchTo = (acc: RecentAccount) => {
  if (acc.token) {
    remember.value = true
    applyLoginSuccess({
      uid: acc.uid,
      name: acc.name,
      avatar: acc.avatar,
      username: acc.username,
      token: acc.token,
    })
    ElMessage.success(`已切换到 ${acc.name}`)
  } else {
    isLogin.value = true
    loginForm.username = acc.username || ''
    loginForm.password = ''
    ElMessage.info('请输入密码登录')
  }
}

// 忘记密码（邮箱验证码找回）
const forgot = reactive({
  show: false,
  email: '',
  code: '',
  newPassword: '',
  sending: false,
  saving: false,
})
const onForgotPassword = () => {
  forgot.email = ''
  forgot.code = ''
  forgot.newPassword = ''
  forgot.show = true
}
const sendForgotCode = async () => {
  if (!forgot.email.trim()) {
    ElMessage.warning('请输入邮箱')
    return
  }
  forgot.sending = true
  try {
    await apis.forgotCode({ email: forgot.email.trim() }).send()
    ElMessage.success('验证码已发送，请查收邮箱')
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    forgot.sending = false
  }
}
const doReset = async () => {
  if (!forgot.code.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(forgot.newPassword)) {
    ElMessage.warning('新密码至少 8 位，且含大小写字母和数字')
    return
  }
  forgot.saving = true
  try {
    await apis
      .forgotReset({
        email: forgot.email.trim(),
        code: forgot.code.trim(),
        newPassword: forgot.newPassword,
      })
      .send()
    ElMessage.success('密码已重置，请用新密码登录')
    forgot.show = false
    loginForm.username = forgot.email.trim()
    loginForm.password = ''
  } catch (e: any) {
    ElMessage.error(e?.message || '重置失败')
  } finally {
    forgot.saving = false
  }
}

const startRegisterCountdown = () => {
  registerCodeCountdown.value = 60
  const timer = setInterval(() => {
    registerCodeCountdown.value--
    if (registerCodeCountdown.value <= 0) clearInterval(timer)
  }, 1000)
}

const sendRegisterCode = async () => {
  const email = registerForm.email.trim()
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  sendingRegisterCode.value = true
  try {
    await apis.registerEmailCode({ email }).send()
    ElMessage.success('验证码已发送，请查收邮箱')
    startRegisterCountdown()
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sendingRegisterCode.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(registerForm.username.trim())) {
    ElMessage.warning('用户名3-20位字母/数字/下划线')
    return
  }
  if (!registerForm.name.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(registerForm.password)) {
    ElMessage.warning('密码至少8位，且包含大小写字母和数字')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(registerForm.email.trim())) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  if (!registerForm.emailCode.trim()) {
    ElMessage.warning('请输入邮箱验证码')
    return
  }
  loading.value = true
  try {
    await apis
      .register({
        username: registerForm.username.trim(),
        name: registerForm.name.trim(),
        password: registerForm.password,
        email: registerForm.email.trim(),
        emailCode: registerForm.emailCode.trim(),
      })
      .send()
    ElMessage.success('注册成功，请登录')
    isLogin.value = true
    loginForm.username = registerForm.username
    registerForm.username =
      registerForm.name =
      registerForm.password =
      registerForm.confirmPassword =
      registerForm.email =
      registerForm.emailCode =
        ''
  } catch (err: any) {
    ElMessage.error(err?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div
    class="login-page"
    @keyup.enter="
      isLogin ? (loginMode === 'password' ? handleLogin() : handleCodeLogin()) : handleRegister()
    "
  >
    <header class="login-brand">
      <span class="brand-mark">H</span>
      <span class="brand-name">HaoChat</span>
    </header>

    <div class="login-card">
      <h1 class="title">{{ isLogin ? '登录 HaoChat' : '注册 HaoChat' }}</h1>
      <p class="subtitle">
        {{
          !isLogin
            ? '创建你的 HaoChat 账号'
            : loginMode === 'password'
            ? '使用用户名和密码进入'
            : loginMode === 'emailCode'
            ? '使用邮箱验证码进入'
            : '使用微信扫码进入'
        }}
      </p>
      <div class="tabs">
        <button :class="['tab', { active: isLogin }]" @click="isLogin = true">登录</button>
        <button :class="['tab', { active: !isLogin }]" @click="isLogin = false">注册</button>
      </div>
      <!-- 最近登录账号：点击可切换/快速登录 -->
      <div v-if="isLogin && recentAccounts.length" class="recent-accounts">
        <button
          v-for="acc in recentAccounts"
          :key="acc.uid"
          type="button"
          class="recent-item"
          :title="acc.token ? `免密切换到 ${acc.name}` : `使用 ${acc.name} 登录`"
          @click="switchTo(acc)"
        >
          <img
            class="recent-avatar"
            :src="
              acc.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
            "
            :alt="acc.name"
          />
          <span class="recent-name">{{ acc.name }}</span>
        </button>
      </div>

      <!-- 登录方式切换：账号密码 / 邮箱验证码 -->
      <div v-if="isLogin" class="login-mode-switch">
        <a
          :class="['mode-link', { active: loginMode === 'password' }]"
          @click="loginMode = 'password'"
          >账号密码</a
        >
        <span class="mode-sep">|</span>
        <a
          :class="['mode-link', { active: loginMode === 'emailCode' }]"
          @click="loginMode = 'emailCode'"
          >邮箱验证码</a
        >
        <span class="mode-sep">|</span>
        <a :class="['mode-link', { active: loginMode === 'wechat' }]" @click="loginMode = 'wechat'"
          >微信扫码</a
        >
      </div>

      <form v-if="isLogin && loginMode === 'password'" class="form" @submit.prevent="handleLogin">
        <label class="field">
          <span>用户名 / 邮箱</span>
          <input
            v-model="loginForm.username"
            class="input"
            placeholder="输入用户名或邮箱"
            autocomplete="username"
          />
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="loginForm.password"
            class="input"
            type="password"
            placeholder="输入密码"
            autocomplete="current-password"
          />
        </label>
        <div class="form-row">
          <label class="remember">
            <input v-model="remember" type="checkbox" />
            <span>记住密码</span>
          </label>
          <a class="forgot-link" @click="onForgotPassword">忘记密码？</a>
        </div>
        <button class="btn" :disabled="loading">{{ loading ? '...' : '登录' }}</button>
      </form>
      <form
        v-else-if="isLogin && loginMode === 'emailCode'"
        class="form"
        @submit.prevent="handleCodeLogin"
      >
        <label class="field">
          <span>邮箱</span>
          <input
            v-model="codeLoginForm.email"
            class="input"
            placeholder="输入注册时绑定的邮箱"
            autocomplete="email"
          />
        </label>
        <label class="field">
          <span>验证码</span>
          <div class="code-row">
            <input
              v-model="codeLoginForm.code"
              class="input"
              placeholder="输入邮箱验证码"
              autocomplete="one-time-code"
            />
            <button
              type="button"
              class="code-btn"
              :disabled="sendingLoginCode || loginCodeCountdown > 0"
              @click="sendLoginCode"
            >
              {{
                loginCodeCountdown > 0
                  ? `${loginCodeCountdown}s`
                  : sendingLoginCode
                  ? '...'
                  : '发送验证码'
              }}
            </button>
          </div>
        </label>
        <button class="btn" :disabled="loading">{{ loading ? '...' : '登录' }}</button>
      </form>
      <div v-else-if="isLogin && loginMode === 'wechat'" class="wechat-login">
        <div class="qr-wrap">
          <QrCode
            v-if="loginStore.loginQrCode"
            :value="loginStore.loginQrCode"
            :size="180"
            :margin="2"
          />
          <div v-else class="qr-placeholder">
            {{ qrTimeout ? '二维码获取失败：微信登录未配置或服务异常' : '二维码加载中…' }}
          </div>
        </div>
        <p class="wechat-tip">
          {{
            loginStore.loginStatus === LoginStatus.Waiting
              ? '扫码成功，请在微信中点击授权完成登录'
              : '使用微信「扫一扫」，关注后自动登录'
          }}
        </p>
      </div>
      <form v-else class="form" @submit.prevent="handleRegister">
        <label class="field">
          <span>用户名</span>
          <input
            v-model="registerForm.username"
            class="input"
            placeholder="字母/数字/下划线 3-20 位"
            autocomplete="off"
          />
        </label>
        <label class="field">
          <span>昵称</span>
          <input
            v-model="registerForm.name"
            class="input"
            placeholder="输入昵称"
            autocomplete="off"
          />
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="registerForm.password"
            class="input"
            type="password"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </label>
        <label class="field">
          <span>确认密码</span>
          <input
            v-model="registerForm.confirmPassword"
            class="input"
            type="password"
            placeholder="再次输入密码"
            autocomplete="new-password"
          />
        </label>
        <label class="field">
          <span>邮箱</span>
          <input
            v-model="registerForm.email"
            class="input"
            placeholder="用于登录找回密码等"
            autocomplete="email"
          />
        </label>
        <label class="field">
          <span>邮箱验证码</span>
          <div class="code-row">
            <input
              v-model="registerForm.emailCode"
              class="input"
              placeholder="输入验证码"
              autocomplete="off"
            />
            <button
              type="button"
              class="code-btn"
              :disabled="sendingRegisterCode || registerCodeCountdown > 0"
              @click="sendRegisterCode"
            >
              {{
                registerCodeCountdown > 0
                  ? `${registerCodeCountdown}s`
                  : sendingRegisterCode
                  ? '...'
                  : '发送验证码'
              }}
            </button>
          </div>
        </label>
        <button class="btn" :disabled="loading">{{ loading ? '...' : '注册' }}</button>
      </form>
    </div>

    <el-dialog v-model="forgot.show" title="找回密码" width="360px" align-center append-to-body>
      <div class="forgot-form">
        <el-input v-model="forgot.email" placeholder="绑定的邮箱" />
        <div class="forgot-code-row">
          <el-input v-model="forgot.code" placeholder="验证码" />
          <el-button :loading="forgot.sending" @click="sendForgotCode">发送验证码</el-button>
        </div>
        <el-input
          v-model="forgot.newPassword"
          type="password"
          show-password
          placeholder="新密码（8位+，含大小写和数字）"
        />
      </div>
      <template #footer>
        <el-button @click="forgot.show = false">取消</el-button>
        <el-button type="primary" :loading="forgot.saving" @click="doReset">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  min-height: 100vh;
  max-height: 100vh;
  padding: 96px 24px 72px;
  overflow-y: auto;
  background: linear-gradient(180deg, #f7faff 0%, #eef4fb 100%);
}

.login-brand {
  position: absolute;
  top: 24px;
  left: 50%;
  display: inline-flex;
  gap: 12px;
  align-items: center;
  transform: translateX(-50%);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  background: #2f66e8;
  border-radius: 10px;
  box-shadow: 0 14px 34px rgba(47, 102, 232, 24%);
}

.brand-name {
  font-size: 20px;
  font-weight: 800;
  color: #12213b;
}

.login-card {
  width: min(392px, 100%);
  padding: 28px;
  background: #fff;
  border: 1px solid #e5edf6;
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(28, 44, 74, 12%);
}

.title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.25;
  color: #111d33;
  letter-spacing: 0;
}

.subtitle {
  margin: 8px 0 20px;
  font-size: 14px;
  color: #7b8ca6;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 5px;
  margin-bottom: 22px;
  background: #edf2f8;
  border: 1px solid #e2eaf4;
  border-radius: 11px;
}

.tab {
  height: 36px;
  font-size: 14px;
  font-weight: 700;
  color: #6d7f98;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
}

.tab.active {
  color: #2f66e8;
  background: #fff;
  box-shadow: 0 3px 10px rgba(35, 55, 87, 8%);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #27384f;
}

.input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  font-size: 14px;
  color: #1f2d44;
  background: #f8fbff;
  border: 1px solid #dfe8f3;
  border-radius: 10px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.input:focus {
  background: #fff;
  border-color: #8fb2ff;
  box-shadow: 0 0 0 3px rgba(47, 102, 232, 10%);
}

.input::placeholder {
  color: #aab8ca;
}

.btn {
  height: 40px;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 800;
  color: #fff;
  cursor: pointer;
  background: #2f66e8;
  border: 0;
  border-radius: 10px;
  transition: background 0.2s, transform 0.2s;
}

.btn:hover:not(:disabled) {
  background: #2859cc;
  transform: translateY(-1px);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.forgot-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.forgot-code-row {
  display: flex;
  gap: 8px;
}

.code-row {
  display: flex;
  gap: 8px;
}

.code-row .input {
  flex: 1;
}

.code-btn {
  flex-shrink: 0;
  height: 40px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 700;
  color: #2f66e8;
  white-space: nowrap;
  cursor: pointer;
  background: #eef3fd;
  border: 1px solid #dfe8f3;
  border-radius: 10px;
  transition: background 0.2s;
}

.code-btn:hover:not(:disabled) {
  background: #e1ebfb;
}

.code-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.recent-accounts {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.recent-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  width: 64px;
  padding: 8px 4px;
  cursor: pointer;
  background: #f6f9fd;
  border: 1px solid #e5edf6;
  border-radius: 10px;
  transition: border-color 0.2s, transform 0.2s;
}

.recent-item:hover {
  border-color: #8fb2ff;
  transform: translateY(-1px);
}

.recent-avatar {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 50%;
}

.recent-name {
  max-width: 100%;
  overflow: hidden;
  font-size: 12px;
  color: #27384f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -4px;
  font-size: 13px;
}

.login-mode-switch {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  font-size: 13px;
}

.mode-link {
  font-weight: 600;
  color: #7b8ca6;
  cursor: pointer;
}

.mode-link.active {
  color: #2f66e8;
}

.mode-link:hover {
  color: #2f66e8;
}

.mode-sep {
  color: #dbe3ee;
}

.wechat-login {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  padding: 12px 0 4px;
}

.qr-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 196px;
  height: 196px;
  background: #f8fbff;
  border: 1px solid #dfe8f3;
  border-radius: 12px;
}

.qr-placeholder {
  padding: 0 16px;
  font-size: 13px;
  line-height: 1.6;
  color: #7b8ca6;
  text-align: center;
}

.wechat-tip {
  margin: 0;
  font-size: 13px;
  color: #5b6b82;
}

.remember {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-weight: 600;
  color: #5b6b82;
  cursor: pointer;
}

.forgot-link {
  color: #2f66e8;
  cursor: pointer;
}

.forgot-link:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .login-page {
    padding: 64px 16px 16px;
  }

  .login-card {
    padding: 20px;
    border-radius: 14px;
  }

  .title {
    font-size: 20px;
  }

  .subtitle {
    margin: 6px 0 14px;
  }

  .tabs {
    margin-bottom: 14px;
  }
}
</style>
