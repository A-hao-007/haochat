<script setup lang="ts">
defineProps<{
  /** 骨架屏变体 */
  variant?: 'text' | 'circle' | 'rect' | 'card'
  /** 宽度 */
  width?: string | number
  /** 高度 */
  height?: string | number
  /** 是否显示动画 */
  animated?: boolean
}>()
</script>

<template>
  <div
    class="skeleton"
    :class="[
      `skeleton--${variant || 'text'}`,
      { 'skeleton--animated': animated !== false },
    ]"
    :style="{
      width: typeof width === 'number' ? `${width}px` : width,
      height: typeof height === 'number' ? `${height}px` : height,
    }"
  />
</template>

<style lang="scss" scoped>
.skeleton {
  background: linear-gradient(
    90deg,
    var(--bg-tertiary) 25%,
    var(--bg-hover) 37%,
    var(--bg-tertiary) 63%
  );
  background-size: 400% 100%;
  border-radius: var(--radius-xs);

  &--animated {
    animation: shimmer 1.4s ease infinite;
  }

  &--text {
    height: 14px;
    margin: 6px 0;
  }

  &--circle {
    border-radius: 50%;
  }

  &--rect {
    border-radius: var(--radius-sm);
  }

  &--card {
    border-radius: var(--radius-md);
    height: 72px;
  }
}

@keyframes shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
