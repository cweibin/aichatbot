<template>
  <view class="chat-page">
    <!-- Topbar -->
    <view class="topbar">
      <view class="topbar__back" @click="goBack">← 返回</view>
      <view class="topbar__title">壹能聊天</view>
      <view class="topbar__spacer"></view>
    </view>

    <!-- Hero Card -->
    <view class="glass-card">
      <view class="seal">印</view>
      <view class="hero__top">
        <view class="hero__info">
          <view class="hero__kicker">AI 助理</view>
          <view class="hero__title">智能对话<br/>诗意人生</view>
          <view class="hero__desc">连接已建立，言语之间，智慧自现。</view>
        </view>
        <view class="status-badge">
          <text class="status-dot"></text>
          <text>已连接</text>
        </view>
      </view>
      <view class="hero__actions">
        <view class="hero__chip">账号模式</view>
        <view class="hero__chip hero__chip--ghost" @click="openHistory">历史记录</view>
      </view>

      <view class="quick-prompts">
        <view class="quick-card" v-for="(item, index) in quickPrompts" :key="index" @click="useQuickPrompt(item.prompt)">
          <view class="quick-card__icon">{{ item.icon }}</view>
          <view class="quick-card__title">{{ item.title }}</view>
          <view class="quick-card__text">{{ item.prompt }}</view>
        </view>
      </view>
    </view>

    <!-- Chat Surface -->
    <view class="chat-surface">
      <view class="message" v-for="(item, index) in messages" :key="index" :class="{ 'message--self': item.self }">
        <view class="message-avatar" :class="item.self ? 'message-avatar--user' : 'message-avatar--ai'">{{ item.self ? '吾' : 'AI' }}</view>
        <view class="message-content">
          <view class="message-header">
            <text class="message-name">{{ item.self ? '你' : '壹' }}</text>
            <text class="message-time">{{ item.time }}</text>
          </view>
          <view class="message-bubble" :class="item.self ? 'message-bubble--user' : 'message-bubble--ai'">
            <text>{{ item.text }}</text>
          </view>
        </view>
      </view>
      <view class="message" v-if="loading">
        <view class="message-avatar message-avatar--ai">AI</view>
        <view class="message-content">
          <view class="message-header">
            <text class="message-name">One</text>
            <text class="message-time">正在思考</text>
          </view>
          <view class="message-bubble message-bubble--ai">
            <view class="typing-indicator">
              <view class="typing-indicator__dot"></view>
              <view class="typing-indicator__dot"></view>
              <view class="typing-indicator__dot"></view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- Composer -->
    <view class="composer">
      <view class="composer__bar">
        <view class="composer__tool" @click="openMenu">+</view>
        <view class="composer__field">
          <textarea
            v-model="msg"
            class="composer__input"
            :adjust-position="false"
            :focus="false"
            auto-height
            :fixed="false"
            maxlength="1000"
            cursor-spacing="10"
            confirm-type="send"
            :show-confirm-bar="false"
            :placeholder="loading ? 'AI 正在思考中...' : '输入你的问题...'"
            @confirm="sendMsg"
          />
        </view>
        <button class="composer__send" :disabled="loading" @click="sendMsg">发送</button>
      </view>
    </view>
  </view>
</template>

<script>
import { sendMsgApi } from '@/api/chat.js'

export default {
  data() {
    return {
      quickPrompts: [
        { icon: '✨', title: '文案润色', prompt: '笔墨生辉，字字珠玑' },
        { icon: '📋', title: '工作助手', prompt: '条理分明，事半功倍' },
        { icon: '💡', title: '学习提问', prompt: '深入浅出，明理通达' }
      ],
      messages: [],
      msg: '',
      loading: false,
      guestUserId: ''
    }
  },
  computed: {
    statusText() {
      return '游客可聊'
    },
    heroDesc() {
      return '当前是 H5 游客模式，不需要登录，直接就可以开始对话。'
    }
  },
  onShow() {
    const cached = this.$squni.getStorageSync('guestUserId')
    this.guestUserId = cached || `guest-${Date.now()}`
    if (!cached) {
      this.$squni.setStorageSync('guestUserId', this.guestUserId)
    }
    if (this.messages.length === 0) {
      this.messages.push({
        self: false,
        time: this.formatTime(new Date()),
        text: '你好！我是 One AI 助手，有什么可以帮助你的吗？'
      })
    }
  },
  methods: {
    goBack() {
      uni.navigateBack({ delta: 1 })
    },
    openHistory() {
      this.$squni.toast('功能完善中')
    },
    openMenu() {
      this.$squni.toast('功能完善中')
    },
    useQuickPrompt(prompt) {
      this.msg = prompt
    },
    formatTime(date) {
      const pad = value => `${value}`.padStart(2, '0')
      return `${pad(date.getHours())}:${pad(date.getMinutes())}`
    },
    async sendMsg() {
      if (!this.msg || this.loading) {
        if (!this.msg) {
          this.$squni.toast('请先输入您的问题哦')
        }
        return
      }
      const question = this.msg
      this.messages.push({
        self: true,
        time: this.formatTime(new Date()),
        text: question
      })
      this.msg = ''
      this.loading = true
      this.$squni.scrollToBottom()
      try {
        const { status, data, message } = await sendMsgApi({
          userId: this.guestUserId,
          question
        })
        if (status === 'success') {
          this.messages.push({
            self: false,
            time: this.formatTime(new Date()),
            text: data && data.ack ? data.ack : ''
          })
        } else {
          this.messages.push({
            self: false,
            time: this.formatTime(new Date()),
            text: message || 'AI 暂时无法回应，请稍后再试~'
          })
        }
      } catch (error) {
        this.messages.push({
          self: false,
          time: this.formatTime(new Date()),
          text: '网络连接异常，请稍后再试~'
        })
      } finally {
        this.loading = false
        this.$squni.scrollToBottom()
      }
    }
  }
}
</script>
