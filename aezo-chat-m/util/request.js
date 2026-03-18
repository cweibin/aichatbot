import config from '@/common/config.js'

export const post = (url, params, options = {}) =>
  request(url, params, { method: 'POST', ...options })

const request = (url, params, options) =>
  new Promise((resolve, reject) => {
    uni.request({
      method: options.method || 'GET',
      url: (options.baseUrl || config.baseUrl) + url,
      header: {
        'Content-Type': 'application/json'
      },
      data: params,
      success(resp) {
        resolve(resp.data)
      },
      fail(err) {
        reject(err)
      }
    })
  })
