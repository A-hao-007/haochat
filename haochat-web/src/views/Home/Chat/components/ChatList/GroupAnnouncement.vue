<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useGroupStore } from '@/stores/group'
import { useUserInfo } from '@/hooks/useCached'
import { formatTimestamp } from '@/utils/computedTime'
import { RoleEnum } from '@/enums'

const groupStore = useGroupStore()
const expanded = ref(false)
const editing = ref(false)
const draft = ref('')
const saving = ref(false)

const announcement = computed(() => groupStore.countInfo.notice || '')
const hasAnnouncement = computed(() => !!announcement.value)
const noticeUid = computed(() => groupStore.countInfo.noticeUid)
const publisher = useUserInfo(noticeUid)
const publishTime = computed(() =>
  groupStore.countInfo.noticeTime ? formatTimestamp(groupStore.countInfo.noticeTime) : '',
)
const readCount = computed(() => groupStore.countInfo.noticeReadCount || 0)
const totalCount = computed(() => groupStore.countInfo.noticeTotalCount || 0)
const readByMe = computed(() => !!groupStore.countInfo.noticeReadByMe)

/** 当前用户是否为群主或管理员 */
const canEdit = computed(() => {
  const role = groupStore.countInfo.role
  return role === RoleEnum.LORD || role === RoleEnum.ADMIN
})

const toggleExpand = () => {
  expanded.value = !expanded.value
  if (expanded.value && hasAnnouncement.value && !readByMe.value) {
    groupStore.readNotice().catch(() => {
      // A read marker is best-effort and should not block expanding the announcement.
    })
  }
}

const startEdit = () => {
  draft.value = announcement.value
  editing.value = true
  expanded.value = true
}

const saveAnnouncement = async () => {
  saving.value = true
  try {
    await groupStore.updateNotice(draft.value.trim())
    editing.value = false
    ElMessage.success('群公告已更新')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 切换群聊时重置编辑/展开态，避免把上一个群的草稿带到新群
watch(
  () => groupStore.countInfo.roomId,
  () => {
    editing.value = false
    expanded.value = false
  },
)
</script>

<template>
  <div v-if="hasAnnouncement || canEdit" class="group-announcement" :class="{ expanded }">
    <!-- 折叠态：摘要条 -->
    <div class="announcement-summary" @click="toggleExpand">
      <div class="summary-icon">
        <IEpNotification :size="14" />
      </div>
      <template v-if="hasAnnouncement">
        <span class="summary-text">{{ announcement }}</span>
        <span v-if="!readByMe" class="unread-dot" />
      </template>
      <span v-else class="summary-text placeholder"
        >暂无群公告{{ canEdit ? '，点击发布一条' : '' }}</span
      >
      <IEpArrowDown :size="12" class="arrow" :class="{ flipped: expanded }" />
    </div>

    <!-- 展开态：详情卡片 -->
    <div v-if="expanded" class="announcement-card">
      <template v-if="editing">
        <textarea
          v-model="draft"
          class="announcement-editor"
          placeholder="编辑群公告，所有成员都能看到~"
          rows="4"
          maxlength="500"
        />
        <div class="announcement-actions">
          <el-button size="small" @click="editing = false">取消</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="saveAnnouncement"
            >发布</el-button
          >
        </div>
      </template>
      <template v-else-if="hasAnnouncement">
        <p class="announcement-text">{{ announcement }}</p>
        <div class="announcement-meta">
          <div class="meta-left">
            <Avatar :src="publisher.avatar" :size="18" />
            <span class="meta-publisher">{{ publisher.name || '未知' }}</span>
            <span class="meta-time">{{ publishTime }}</span>
          </div>
          <span class="meta-read">{{ readCount }}/{{ totalCount }} 人已读</span>
        </div>
        <div v-if="canEdit" class="announcement-edit-btn" @click="startEdit">
          <IEpEdit :size="12" /> 编辑公告
        </div>
      </template>
      <template v-else>
        <div class="announcement-empty">
          <IEpBell :size="28" class="empty-icon" />
          <p>还没有群公告，发布一条让大家都看到吧</p>
          <el-button size="small" type="primary" @click="startEdit">发布群公告</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.group-announcement {
  flex-shrink: 0;
  margin: 8px 16px 0;
  overflow: hidden;
  background: var(--bg-active);
  border: 1px solid var(--color-primary-light);
  border-radius: var(--radius-md);
  transition: border-color var(--transition-fast);

  &.expanded {
    border-color: var(--color-primary);
  }

  .announcement-summary {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: 8px 12px;
    font-size: 13px;
    font-weight: 500;
    color: var(--color-primary);
    cursor: pointer;
    user-select: none;

    .summary-icon {
      display: flex;
      flex-shrink: 0;
      align-items: center;
    }

    .summary-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      font-weight: 400;
      color: var(--font-main);
      text-overflow: ellipsis;
      white-space: nowrap;

      &.placeholder {
        color: var(--font-light);
      }
    }

    .unread-dot {
      flex-shrink: 0;
      width: 6px;
      height: 6px;
      background: #ef4444;
      border-radius: 50%;
    }

    .arrow {
      flex-shrink: 0;
      transition: transform var(--transition-fast);

      &.flipped {
        transform: rotate(-180deg);
      }
    }
  }

  .announcement-card {
    padding: 4px 12px 12px;
    border-top: 1px solid var(--color-primary-light);

    .announcement-text {
      margin: 8px 0 10px;
      font-size: 13px;
      line-height: 1.6;
      color: var(--font-main);
      white-space: pre-wrap;
    }

    .announcement-meta {
      display: flex;
      gap: 8px;
      align-items: center;
      justify-content: space-between;
      padding-top: 8px;
      border-top: 1px dashed var(--border-light);

      .meta-left {
        display: flex;
        gap: 6px;
        align-items: center;
        min-width: 0;
        overflow: hidden;

        .meta-publisher {
          font-size: 12px;
          color: var(--font-secondary);
          white-space: nowrap;
        }

        .meta-time {
          font-size: 12px;
          color: var(--font-light);
          white-space: nowrap;
        }
      }

      .meta-read {
        flex-shrink: 0;
        font-size: 12px;
        color: var(--font-light);
      }
    }

    .announcement-edit-btn {
      display: inline-flex;
      gap: 4px;
      align-items: center;
      width: fit-content;
      padding: 4px 10px;
      margin-top: 10px;
      font-size: 12px;
      color: var(--color-primary);
      cursor: pointer;
      background: var(--color-primary-light);
      border-radius: var(--radius-sm);

      &:hover {
        color: #fff;
        background: var(--color-primary);
      }
    }

    .announcement-empty {
      display: flex;
      flex-direction: column;
      gap: 8px;
      align-items: center;
      padding: 16px 0 8px;
      text-align: center;

      .empty-icon {
        color: var(--font-light);
      }

      p {
        margin: 0;
        font-size: 12px;
        color: var(--font-light);
      }
    }

    .announcement-editor {
      width: 100%;
      padding: 8px;
      margin-top: 8px;
      font-family: inherit;
      font-size: 13px;
      color: var(--font-main);
      resize: vertical;
      background: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);

      &:focus {
        border-color: var(--color-primary);
        outline: none;
      }
    }

    .announcement-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
      margin-top: 8px;
    }
  }
}
</style>
