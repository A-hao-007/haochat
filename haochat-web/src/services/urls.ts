// 本地配置到 .env 里面修改。生产配置在 .env.production 里面
const prefix = import.meta.env.PROD ? import.meta.env.VITE_API_PREFIX : ''
export default {
  // -------------- 认证相关 ---------------
  login: `${prefix}/capi/user/public/login`,
  register: `${prefix}/capi/user/public/register`,
  registerEmailCode: `${prefix}/capi/user/public/register/email/code`,
  emailLogin: `${prefix}/capi/user/public/email/login`,
  loginEmailCode: `${prefix}/capi/user/public/login/email/code`, // 发送登录验证码
  emailCodeLogin: `${prefix}/capi/user/public/email/code/login`, // 邮箱验证码登录
  forgotCode: `${prefix}/capi/user/public/password/forgot/code`,
  forgotReset: `${prefix}/capi/user/public/password/forgot/reset`,
  refreshToken: `${prefix}/capi/user/public/token/refresh`,
  logout: `${prefix}/capi/user/public/logout`,
  bindEmailCode: `${prefix}/capi/user/email/code`,
  bindEmail: `${prefix}/capi/user/email`,
  searchUser: `${prefix}/capi/user/public/search`,
  updateAvatar: `${prefix}/capi/user/avatar`,
  modifyPassword: `${prefix}/capi/user/password`, // 修改密码

  getMemberStatistic: `${prefix}/capi/chat/public/member/statistic`,
  getUserInfoBatch: `${prefix}/capi/user/public/summary/userInfo/batch`,
  getBadgesBatch: `${prefix}/capi/user/public/badges/batch`,
  getAllUserBaseInfo: `${prefix}/capi/room/group/member/list`, // 房间内的所有群成员列表-@专用
  getAiBots: `${prefix}/capi/chat/public/ai/bots`, // 已启用的AI助手列表
  getMsgList: `${prefix}/capi/chat/public/msg/page`,
  sendMsg: `${prefix}/capi/chat/msg`,
  confirmAgentAction: `${prefix}/capi/chat/msg/confirm`, // 确认/取消AI助手发起的高风险操作提议
  stopAiStream: `${prefix}/capi/chat/ai/stop`, // 停止AI流式回复
  getUserInfoDetail: `${prefix}/capi/user/userInfo`, // 获取用户信息详情
  modifyUserName: `${prefix}/capi/user/name`, // 修改用户名
  getBadgeList: `${prefix}/capi/user/badges`, // 徽章列表
  setUserBadge: `${prefix}/capi/user/badge`, // 设置用户徽章
  markMsg: `${prefix}/capi/chat/msg/mark`, // 消息标记
  blockUser: `${prefix}/capi/user/black`, // 拉黑用户
  recallMsg: `${prefix}/capi/chat/msg/recall`, // 撤回消息
  editMsg: `${prefix}/capi/chat/msg/edit`, // 编辑消息
  fileUpload: `${prefix}/capi/oss/upload/url`, // 文件上传
  fileUploadInit: `${prefix}/capi/file/upload/init`,
  fileUploadComplete: `${prefix}/capi/file/upload/complete`,
  fileUploadRetry: `${prefix}/capi/file/upload`,
  fileDownload: `${prefix}/capi/file`,
  bindAvatarAsset: `${prefix}/capi/file/avatar`,
  addEmoji: `${prefix}/capi/user/emoji`, // 增加表情
  deleteEmoji: `${prefix}/capi/user/emoji`, // 删除表情
  getEmoji: `${prefix}/capi/user/emoji/list`, // 查询表情包

  // -------------- 好友相关 ---------------
  getContactList: `${prefix}/capi/user/friend/page`, // 联系人列表
  requestFriendList: `${prefix}/capi/user/friend/apply/page`, // 好友申请列表
  sentFriendList: `${prefix}/capi/user/friend/apply/sent/page`, // 发出的好友申请列表
  sendAddFriendRequest: `${prefix}/capi/user/friend/apply`, // 申请好友
  rejectFriendRequest: `${prefix}/capi/user/friend/apply/reject`, // 拒绝好友申请
  deleteFriend: `${prefix}/capi/user/friend`, // 删除好友
  newFriendCount: `${prefix}/capi/user/friend/apply/unread`, // 申请未读数

  // -------------- 聊天室相关 ---------------
  getSessionList: `${prefix}/capi/chat/public/contact/page`, // 会话列表
  getMsgReadList: `${prefix}/capi/chat/msg/read/page`, // 消息的已读未读列表
  getMsgReadCount: `${prefix}/capi/chat/msg/read`, // 消息已读未读数
  createGroup: `${prefix}/capi/room/group`, // 新增群组
  getGroupUserList: `${prefix}/capi/room/public/group/member/page`,
  inviteGroupMember: `${prefix}/capi/room/group/member`, // 邀请群成员
  exitGroup: `${prefix}/capi/room/group/member/exit`, // 退群
  muteGroupMember: `${prefix}/capi/room/group/member/mute`, // 禁言群成员
  addAdmin: `${prefix}/capi/room/group/admin`, // 添加管理员
  revokeAdmin: `${prefix}/capi/room/group/admin`, // 添加管理员
  groupDetail: `${prefix}/capi/room/public/group`, // 群组详情
  transferLord: `${prefix}/capi/room/group/lord`, // 转让群主
  updateGroupNotice: `${prefix}/capi/room/group/notice`, // 更新群公告
  readGroupNotice: `${prefix}/capi/room/group/notice/read`, // 标记群公告已读
  updateGroupNickname: `${prefix}/capi/room/group/nickname`, // 更新我在群里的昵称
  myGroupList: `${prefix}/capi/room/group/my-list`, // 我的群组列表
  groupLogPage: `${prefix}/capi/room/group/log/page`, // 群操作日志
  sessionDetail: `${prefix}/capi/chat/public/contact/detail`, // 会话详情
  sessionDetailWithFriends: `${prefix}/capi/chat/public/contact/detail/friend`, // 会话详情(联系人列表发消息用)
  pinContact: `${prefix}/capi/chat/contact/pin`,
  muteContact: `${prefix}/capi/chat/contact/mute`,
  deleteContact: `${prefix}/capi/chat/contact`,
  setFriendRemark: `${prefix}/capi/user/friend/remark`,
  updateStatusMsg: `${prefix}/capi/user/statusMsg`,
  changePassword: `${prefix}/capi/user/password`,
  uploadFile: `${prefix}/capi/file/upload`,
}
