<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { formatBytes, getFileSuffix } from '@/utils'
import type { FileBody } from '@/services/types'
import useDownloadQuenuStore from '@/stores/downloadQuenu'
import apis from '@/services/apis'

const { downloadObjMap, download, quenu, cancelDownload } = useDownloadQuenuStore()
const props = defineProps<{ body: FileBody }>()
const resolvedUrl = ref(props.body.url)

watchEffect(() => {
  resolvedUrl.value = props.body.url
})

const downloadFile = async () => {
  if (props.body.assetId) {
    const { downloadUrl } = await apis.getFileDownload(props.body.assetId).send()
    resolvedUrl.value = downloadUrl
  }
  download(resolvedUrl.value)
}

const cancelDownloadFile = () => {
  cancelDownload(resolvedUrl.value)
}

const isDownloading = computed(() => {
  return downloadObjMap.get(resolvedUrl.value)?.isDownloading || false
})

const process = computed(() => {
  return downloadObjMap.get(resolvedUrl.value)?.process || 0
})

const isQuenu = computed(() => {
  return quenu.includes(resolvedUrl.value)
})
</script>

<template>
  <div class="file">
    <Icon :icon="getFileSuffix(body?.fileName)" :size="32" colorful />
    <div class="file-desc">
      <span class="file-name">{{ body?.fileName || '未知文件' }}</span>
      <span class="file-size">{{ formatBytes(body?.size) }}</span>
    </div>
    <el-text v-if="isQuenu" class="mx-1" size="small" type="warning" @click="cancelDownloadFile">
      等待下载
      <el-icon>
        <Close />
      </el-icon>
    </el-text>
    <Icon v-else-if="!isDownloading" icon="xiazai" :size="22" @click="downloadFile" />
    <el-progress
      v-else
      type="circle"
      :percentage="process"
      :width="22"
      :stroke-width="1"
      :show-text="false"
    />
  </div>
</template>
