const ACCOUNT_CACHE_VERSION_KEY = 'ACCOUNT_CACHE_VERSION'
const ACCOUNT_CACHE_VERSION = '2'

/** Remove data that belongs to a specific signed-in account. */
export const clearAccountScopedStorage = () => {
  localStorage.removeItem('cached')
  localStorage.removeItem('IS_INIT_USER_BASE')
}

/** Run one-time cleanup when the persisted account cache schema changes. */
export const migrateAccountScopedStorage = () => {
  if (localStorage.getItem(ACCOUNT_CACHE_VERSION_KEY) === ACCOUNT_CACHE_VERSION) return
  clearAccountScopedStorage()
  localStorage.setItem(ACCOUNT_CACHE_VERSION_KEY, ACCOUNT_CACHE_VERSION)
}
