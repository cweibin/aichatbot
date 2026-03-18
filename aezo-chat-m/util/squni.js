import config from '@/common/config.js'

const globalKeyPrefix = config.key ? `${config.key}$` : 'squni$'

export const setStorageSync = (key, value) => uni.setStorageSync(globalKeyPrefix + key, value)
export const getStorageSync = key => uni.getStorageSync(globalKeyPrefix + key)
export const removeStorageSync = key => uni.removeStorageSync(globalKeyPrefix + key)

export const toast = (message, icon) => {
  uni.showToast({
    title: message,
    icon: icon || 'none'
  })
}

export const copy = value => {
  uni.setClipboardData({
    data: value
  })
}

export const scrollToBottom = () => {
  setTimeout(() => {
    uni.pageScrollTo({
      scrollTop: 999999,
      duration: 0
    })
  }, 50)
}

export default {
  setStorageSync,
  getStorageSync,
  removeStorageSync,
  toast,
  copy,
  scrollToBottom
}
