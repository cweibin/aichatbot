const dev = process.env.NODE_ENV === 'development'

const baseUrl = dev ? 'http://localhost:6501/api' : 'https://www.example.com/api'
const wssUrl = dev ? 'ws://localhost:6501/api' : 'wss://www.example.com/api'

const config = {
  baseUrl,
  wssUrl,
  key: 'aezo-chat-m',
  Authorization: 'SQ-ACCESS-TOKEN'
}

export default config
