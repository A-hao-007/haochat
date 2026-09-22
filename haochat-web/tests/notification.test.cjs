const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { resolve } = require('node:path')
const { test } = require('node:test')
const vm = require('node:vm')
const ts = require('typescript')

const source = readFileSync(resolve(__dirname, '../src/utils/notification.ts'), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
})

function loadNotify(Notification) {
  const context = vm.createContext({ exports: {}, Notification })
  vm.runInContext(outputText, context)
  return context.exports.default
}

function mockNotification(permission, requestPermission = async () => 'granted') {
  const created = []
  class FakeNotification {
    static permission = permission
    static requestPermission = requestPermission
    constructor(title, options) {
      this.title = title
      this.options = options
      created.push(this)
    }
    addEventListener(event, callback) {
      this[event] = callback
    }
  }
  return { Notification: FakeNotification, created }
}

const message = { name: 'Alice', text: 'Hello', icon: '/avatar.png' }

test('unsupported browsers do not interrupt message handling', async () => {
  await loadNotify(undefined)(message)
})

test('granted notifications retain their content and click handler', async () => {
  const { Notification, created } = mockNotification('granted')
  let clicks = 0
  await loadNotify(Notification)({ ...message, onClick: () => clicks++ })
  assert.equal(created.length, 1)
  assert.equal(created[0].title, 'HaoChat: Alice')
  assert.equal(created[0].options.body, 'Hello')
  assert.equal(created[0].options.icon, '/avatar.png')
  created[0].click()
  assert.equal(clicks, 1)
})

test('denied permission neither prompts nor creates a notification', async () => {
  let prompts = 0
  const { Notification, created } = mockNotification('denied', async () => {
    prompts++
    return 'granted'
  })
  await loadNotify(Notification)(message)
  assert.equal(prompts, 0)
  assert.equal(created.length, 0)
})

test('a newly granted permission creates a notification', async () => {
  const { Notification, created } = mockNotification('default')
  await loadNotify(Notification)(message)
  assert.equal(created.length, 1)
})

test('dismissing or denying the permission prompt creates no notification', async () => {
  for (const permission of ['default', 'denied']) {
    const { Notification, created } = mockNotification('default', async () => permission)
    await loadNotify(Notification)(message)
    assert.equal(created.length, 0)
  }
})

test('a rejected permission request does not reject message handling', async () => {
  const { Notification } = mockNotification('default', async () => {
    throw new Error('Permission request unavailable')
  })
  await loadNotify(Notification)(message)
})

test('a synchronous permission failure does not interrupt message handling', async () => {
  const { Notification } = mockNotification('default', () => {
    throw new Error('Permission request blocked')
  })
  await loadNotify(Notification)(message)
})

test('constructor failures are contained for existing and newly granted permissions', async () => {
  for (const permission of ['granted', 'default']) {
    class UnsupportedNotification {
      static permission = permission
      static requestPermission = async () => 'granted'
      constructor() {
        throw new TypeError('Notification constructor unavailable')
      }
    }
    await loadNotify(UnsupportedNotification)(message)
  }
})
