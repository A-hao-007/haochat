<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
// 全局导入 toast 样式
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
// element dark 模式
import 'element-plus/theme-chalk/dark/css-vars.css'

const route = useRoute()
const isLoginPage = computed(() => route.path === '/login')
</script>

<template>
  <div class="app-wrapper" :class="{ 'is-login-page': isLoginPage }">
    <RouterView />
  </div>
</template>

<style scoped>
.app-wrapper {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.app-wrapper:not(.is-login-page) {
  background-image: url('@/assets/login_bg.jpg');
  background-repeat: no-repeat;
  background-position: center;
  background-size: cover;
}

@supports (background-image: url('@/assets/login_bg.webp')) {
  .app-wrapper:not(.is-login-page) {
    background-image: url('@/assets/login_bg.webp');
  }
}
</style>
