// notification
type NotificationType = {
  text: string
  icon?: string
  name: string
  onClick?: () => void
}

const notification = ({ name, text, icon, onClick }: NotificationType) => {
  const notification = new Notification(`HaoChat: ${name}`, {
    body: text,
    icon: icon,
  })

  notification.addEventListener('click', () => {
    onClick?.()
  })
}

const notify = async ({ name, text, icon, onClick }: NotificationType) => {
  if (typeof Notification === 'undefined') return

  try {
    let permission = Notification.permission
    if (permission === 'default') {
      permission = await Notification.requestPermission()
    }
    if (permission === 'granted') {
      notification({ name, text, icon, onClick })
    }
  } catch {
    // 系统通知不可用时，仍需继续处理聊天消息和未读计数。
  }
}

export default notify
