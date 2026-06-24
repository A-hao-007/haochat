<script setup lang="ts">
import { ref } from 'vue'
import { useGroupStore } from '@/stores/group'
import { useGlobalStore } from '@/stores/global'
import { RoleEnum } from '@/enums'

const groupStore = useGroupStore()
const globalStore = useGlobalStore()
const collapsed = ref(false)
const editing = ref(false)
const announcement = ref('')

/** 当前用户是否为群主或管理员 */
const canEdit = () => {
  const currentUid = 0 // TODO: get from user store
  const currentMember = groupStore.userList?.find((m) => m.uid === currentUid)
  return currentMember && (currentMember.roleId === RoleEnum.LORD || currentMember.roleId === RoleEnum.ADMIN)
}

const saveAnnouncement = () => {
  // TODO: 调用后端 API 保存群公告
  editing.value = false
}
</script>

<template>
  <div v-if="announcement || canEdit()" class="group-announcement" :class="{ collapsed }">
    <div class="announcement-header" @click="collapsed = !collapsed">
      <IEpNotification :size="14" />
      <span class="announcement-title">群公告</span>
      <IEpArrowDown v-if="!collapsed" :size="12" class="arrow" />
      <IEpArrowRight v-else :size="12" class="arrow" />
    </div>
    <div v-show="!collapsed" class="announcement-body">
      <template v-if="editing">
        <textarea
          v-model="announcement"
          class="announcement-editor"
          placeholder="编辑群公告..."
          rows="3"
        />
        <div class="announcement-actions">
          <el-button size="small" @click="editing = false">取消</el-button>
          <el-button size="small" type="primary" @click="saveAnnouncement">发布</el-button>
        </div>
      </template>
      <template v-else>
        <p class="announcement-text">{{ announcement || '暂无群公告' }}</p>
        <span v-if="canEdit()" class="announcement-edit-btn" @click="editing = true">
          <IEpEdit :size="12" /> 编辑
        </span>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.group-announcement {
  margin: 8px 16px 0;
  background: var(--bg-active);
  border: 1px solid var(--color-primary-light);
  border-radius: var(--radius-md);
  overflow: hidden;
  flex-shrink: 0;

  .announcement-header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px;
    cursor: pointer;
    color: var(--color-primary);
    font-size: 13px;
    font-weight: 500;
    user-select: none;

    .arrow {
      margin-left: auto;
    }
  }

  .announcement-body {
    padding: 0 12px 10px;

    .announcement-text {
      font-size: 13px;
      color: var(--font-secondary);
      line-height: 1.5;
      white-space: pre-wrap;
    }

    .announcement-edit-btn {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-top: 6px;
      font-size: 12px;
      color: var(--color-primary);
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }

    .announcement-editor {
      width: 100%;
      padding: 8px;
      font-size: 13px;
      color: var(--font-main);
      background: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      resize: vertical;
      font-family: inherit;

      &:focus {
        outline: none;
        border-color: var(--color-primary);
      }
    }

    .announcement-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 8px;
    }
  }
}
</style>
