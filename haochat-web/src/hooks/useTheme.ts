import { ref, watchEffect } from 'vue'

type Theme = 'dark' | 'light'

const THEME_KEY = 'HAOCHAT_THEME'

// 全局共享的响应式主题状态
const currentTheme = ref<Theme>('dark')

/**
 * 主题管理 composable
 * 用于控制应用的暗黑/明亮主题切换
 */
export function useTheme() {
  /**
   * 初始化主题：从 localStorage 读取，或使用系统偏好
   */
  const initTheme = () => {
    // 优先读取本地存储
    const stored = localStorage.getItem(THEME_KEY) as Theme | null
    if (stored === 'dark' || stored === 'light') {
      currentTheme.value = stored
    } else {
      // 回退到系统偏好
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      currentTheme.value = prefersDark ? 'dark' : 'light'
    }
    applyTheme(currentTheme.value)
  }

  /**
   * 应用主题到 DOM
   */
  const applyTheme = (theme: Theme) => {
    document.documentElement.setAttribute('data-theme', theme)
    // 同步 Element Plus 自身的暗黑模式（其组件默认样式依赖 html.dark）
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }

  /**
   * 切换主题
   */
  const toggleTheme = () => {
    currentTheme.value = currentTheme.value === 'dark' ? 'light' : 'dark'
    applyTheme(currentTheme.value)
    localStorage.setItem(THEME_KEY, currentTheme.value)
  }

  /**
   * 设置主题
   */
  const setTheme = (theme: Theme) => {
    currentTheme.value = theme
    applyTheme(theme)
    localStorage.setItem(THEME_KEY, theme)
  }

  // 监听系统主题变化
  if (typeof window !== 'undefined') {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      // 只有在用户未手动设置时才跟随系统
      if (!localStorage.getItem(THEME_KEY)) {
        setTheme(e.matches ? 'dark' : 'light')
      }
    })
  }

  return {
    currentTheme,
    initTheme,
    toggleTheme,
    setTheme,
    isDark: () => currentTheme.value === 'dark',
    isLight: () => currentTheme.value === 'light',
  }
}
