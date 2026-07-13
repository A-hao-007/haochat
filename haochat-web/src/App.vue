<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AiAssistantBall from '@/components/AiAssistantBall/index.vue'
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
    <AiAssistantBall v-if="!isLoginPage" />
  </div>
</template>

<style scoped>
.app-wrapper {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #edf4fb;
}

.app-wrapper:not(.is-login-page) {
  background: radial-gradient(
      circle at 50% 20%,
      rgba(255, 255, 255, 92%),
      rgba(255, 255, 255, 0%) 36%
    ),
    linear-gradient(180deg, #f7fbff 0%, #e8f0f8 100%);
}
</style>
