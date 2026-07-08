import { watch, type Ref } from 'vue'
import { pushModalGuard, popModalGuard } from '@/utils/modalHistoryGuard'

/**
 * 绑定一个弹窗的 v-model，使浏览器“后退”优先关闭该弹窗而不是退出系统。
 */
export function useModalBackGuard(visible: Ref<boolean>) {
  let guarded = false
  watch(visible, (val) => {
    if (val && !guarded) {
      guarded = true
      pushModalGuard(() => {
        visible.value = false
        guarded = false
      })
    } else if (!val && guarded) {
      guarded = false
      popModalGuard()
    }
  })
}
