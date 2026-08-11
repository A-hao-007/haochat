<script setup lang="ts">
import { computed } from 'vue'
import type { VoiceBody } from '@/services/types'
import { useVoicePreviewStore } from '@/stores/preview'

const props = defineProps<{ body: VoiceBody }>()
const voiceStore = useVoicePreviewStore()

const isPlay = computed(() => voiceStore.previewUrl === props.body.url && voiceStore.isPlaying)
const cacheKey = computed(() => `VOICE_TRANSCRIPT_${props.body.assetId || props.body.url}`)
const transcript = computed(() => props.body.transcript || sessionStorage.getItem(cacheKey.value) || '')
</script>

<template>
  <div class="voice-message">
    <div class="voice" title="播放语音；右键可转成文字" @click="voiceStore.open(body.url)">
      <div class="saying">
        <span :class="['shelter', { play: isPlay }]" />
        <Icon icon="saying" :size="18" />
      </div>
      <span class="num">{{ body?.second }}"</span>
    </div>
    <div v-if="transcript" class="transcript">
      <span class="transcript-label">语音转写</span>
      <p>{{ transcript }}</p>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.voice-message {
  min-width: 100px;
}

.transcript {
  min-width: 190px;
  max-width: 360px;
  padding: 10px 11px;
  margin-top: 8px;
  color: #3e4b60;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(111, 131, 162, 0.18);
  border-radius: 10px;

  .transcript-label {
    color: #7b8799;
    font-size: 10px;
    font-weight: 700;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}
</style>
