<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import type { ImageBody } from '@/services/types'
import { useImgPreviewStore } from '@/stores/preview'
import { formatImage } from '@/utils'
import apis from '@/services/apis'

const props = defineProps<{ body: ImageBody }>()

const imageStore = useImgPreviewStore()
const hasLoadError = ref(false)
const isLoading = ref(true)
const resolvedUrl = ref(props.body?.url || '')

watchEffect(async () => {
  hasLoadError.value = false
  resolvedUrl.value = props.body?.url || ''
  if (!props.body?.assetId) return
  const { downloadUrl } = await apis.getFileDownload(props.body.assetId).send()
  resolvedUrl.value = downloadUrl
})

/**
 * 核心就是的到高度，产生明确占位防止图片加载时页面抖动
 * @param width 宽度
 * @param height 高度
 */
const getImageHeight = computed(() => {
  const { width, height } = props.body
  return formatImage(width, height)
})

// 没有图片的情况下计算出按比例的宽度
const getWidthStyle = () => {
  const { width, height } = props.body
  return `width: ${(getImageHeight.value / height) * width}px`
}

const handleError = () => {
  isLoading.value = false
  hasLoadError.value = true
}
</script>

<template>
  <div
    class="image"
    :style="{ height: getImageHeight + 'px' }"
    @click="imageStore.show(resolvedUrl as string)"
  >
    <div v-if="hasLoadError" class="image-slot" :style="getWidthStyle()">
      <Icon icon="dazed" :size="36" colorful />
      加载失败
    </div>
    <template v-else>
      <img
        v-if="resolvedUrl"
        :src="resolvedUrl"
        draggable="false"
        @click="imageStore.show(resolvedUrl as string)"
        @error="handleError"
        :alt="resolvedUrl"
      />
    </template>
  </div>
</template>
