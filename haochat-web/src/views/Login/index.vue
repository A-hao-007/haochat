<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import apis from '@/services/apis'
import { computedToken } from '@/services/request'
import wsIns from '@/utils/websocket'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

if (userStore.isSign) router.replace('/')

const isLogin = ref(true)
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', name: '', password: '', confirmPassword: '' })

const handleLogin = async () => {
  if (!loginForm.username.trim()) { ElMessage.warning('请输入用户名'); return }
  if (!loginForm.password) { ElMessage.warning('请输入密码'); return }
  loading.value = true
  try {
    const res = await apis.login({ username: loginForm.username.trim(), password: loginForm.password }).send()
    localStorage.setItem('TOKEN', res.token)
    localStorage.setItem('USER_INFO', JSON.stringify({ uid: res.uid, name: res.name, avatar: res.avatar }))
    computedToken.clear(); computedToken.get()
    userStore.isSign = true
    userStore.userInfo = { uid: res.uid, name: res.name, avatar: res.avatar }
    wsIns.reconnect()
    userStore.getUserDetailAction()
    chatStore.getSessionList(true)
    router.replace('/')
    ElMessage.success('登录成功')
  } catch (err: any) { ElMessage.error(err?.message || '登录失败') }
  finally { loading.value = false }
}

const handleRegister = async () => {
  if (!registerForm.username.trim()) { ElMessage.warning('请输入用户名'); return }
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(registerForm.username.trim())) { ElMessage.warning('用户名3-20位字母/数字/下划线'); return }
  if (!registerForm.name.trim()) { ElMessage.warning('请输入昵称'); return }
  if (!registerForm.password || registerForm.password.length < 6) { ElMessage.warning('密码至少6位'); return }
  if (registerForm.password !== registerForm.confirmPassword) { ElMessage.warning('两次密码不一致'); return }
  loading.value = true
  try {
    await apis.register({ username: registerForm.username.trim(), name: registerForm.name.trim(), password: registerForm.password }).send()
    ElMessage.success('注册成功，请登录')
    isLogin.value = true
    loginForm.username = registerForm.username
    registerForm.username = registerForm.name = registerForm.password = registerForm.confirmPassword = ''
  } catch (err: any) { ElMessage.error(err?.message || '注册失败') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="login-page" @keyup.enter="isLogin ? handleLogin() : handleRegister()">
    <div class="login-card">
      <img class="logo" src="@/assets/logo.jpeg" alt="HaoChat" />
      <h1 class="title">HaoChat</h1>
      <div class="tabs">
        <button :class="['tab', { active: isLogin }]" @click="isLogin = true">登录</button>
        <button :class="['tab', { active: !isLogin }]" @click="isLogin = false">注册</button>
      </div>
      <form v-if="isLogin" class="form" @submit.prevent="handleLogin">
        <input v-model="loginForm.username" class="input" placeholder="用户名" autocomplete="username" />
        <input v-model="loginForm.password" class="input" type="password" placeholder="密码" autocomplete="current-password" />
        <button class="btn" :disabled="loading">{{ loading ? '...' : '登录' }}</button>
      </form>
      <form v-else class="form" @submit.prevent="handleRegister">
        <input v-model="registerForm.username" class="input" placeholder="用户名（字母/数字/下划线 3-20位）" autocomplete="off" />
        <input v-model="registerForm.name" class="input" placeholder="昵称" autocomplete="off" />
        <input v-model="registerForm.password" class="input" type="password" placeholder="密码（至少6位）" autocomplete="new-password" />
        <input v-model="registerForm.confirmPassword" class="input" type="password" placeholder="确认密码" autocomplete="new-password" />
        <button class="btn" :disabled="loading">{{ loading ? '...' : '注册' }}</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex; align-items: center; justify-content: center;
  width: 100vw; height: 100vh;
  background: linear-gradient(135deg, #1a1a2e, #16213e, #0f3460);
}
.login-card {
  width: 360px; padding: 40px 36px;
  background: rgba(255,255,255,0.95); border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3); text-align: center;
}
.logo { width: 64px; height: 64px; border-radius: 16px; margin-bottom: 8px; }
.title { font-size: 24px; font-weight: 700; color: #1a1a2e; margin-bottom: 20px; }
.tabs { display: flex; margin-bottom: 20px; border-bottom: 2px solid #eee; }
.tab { flex: 1; padding: 10px 0; font-size: 15px; font-weight: 600; color: #999; background: none; border: none; cursor: pointer; }
.tab.active { color: #1d90f5; border-bottom: 2px solid #1d90f5; margin-bottom: -2px; }
.form { display: flex; flex-direction: column; gap: 14px; }
.input {
  padding: 12px 14px; font-size: 14px; color: #333; background: #f5f6f8;
  border: 2px solid transparent; border-radius: 10px; outline: none; transition: all 0.25s;
}
.input:focus { border-color: #1d90f5; background: #fff; box-shadow: 0 0 0 3px rgba(29,144,245,0.1); }
.input::placeholder { color: #bbb; }
.btn {
  padding: 12px; margin-top: 4px; font-size: 15px; font-weight: 600; color: #fff;
  background: #1d90f5; border: none; border-radius: 10px; cursor: pointer; transition: all 0.3s;
}
.btn:hover:not(:disabled) { background: #1a7fd9; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
