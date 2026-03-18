/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useRef, useEffect } from 'react';
import { Menu, X, ChevronLeft, Sparkles, Briefcase, Lightbulb, Crown, Package, MessageCircle, Trash2, AlertTriangle } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

// --- Types ---
type Message = {
  id: string;
  role: 'user' | 'ai';
  content: string;
  timestamp: Date;
};

type ApiResult<T> = {
  status: string;
  message?: string;
  data?: T;
  code?: number;
};

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:6501/api';
const TOKEN_KEY = 'one-chat-token';
const TOKEN_HEADER = 'SQ-ACCESS-TOKEN';

const ensureUserId = () => {
  if (typeof window === 'undefined') return '1';
  const key = 'one-chat-user-id';
  let id = window.localStorage.getItem(key);
  if (!id) {
    const uuid = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `u_${Date.now()}_${Math.random().toString(16).slice(2)}`;
    id = uuid;
    window.localStorage.setItem(key, id);
  }
  return id;
};

const getToken = () => {
  if (typeof window === 'undefined') return '';
  return window.localStorage.getItem(TOKEN_KEY) || '';
};

const setToken = (token: string) => {
  if (typeof window === 'undefined') return;
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token);
  } else {
    window.localStorage.removeItem(TOKEN_KEY);
  }
};

const request = async <T,>(path: string, options: { method?: 'GET' | 'POST'; body?: unknown; auth?: boolean } = {}) => {
  const { method = 'POST', body, auth = false } = options;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getToken();
    if (token) {
      headers[TOKEN_HEADER] = token;
    }
  }
  const resp = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }
  return resp.json() as Promise<ApiResult<T>>;
};

const sendMsgApi = async (params: { userId: string; question: string; }) =>
  request<{ ack?: string }>('/tools/chat/sendMsg', { body: params });

const loginApi = async (params: { username: string; password: string }) =>
  request<{ tokenInfo?: { tokenValue?: string } }>(`/core/user/login?loginType=ACCOUNT`, { body: params });

const getUserChatAssetApi = async () =>
  request<{ n?: number | string; dfn?: number | string }>(`/tools/chat/getUserChatAsset`, { body: {}, auth: true });

const getUserInfoApi = async () =>
  request<{ userInfo?: { id?: string; nick_name?: string; username?: string }; roles?: string[] }>(`/core/user/info`, { body: {}, auth: true });

// --- Helper Functions ---
const formatTime = (date: Date) => {
  const hours = date.getHours();
  const minutes = date.getMinutes();
  
  const earthlyBranches = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
  const branchIndex = Math.floor(((hours + 1) % 24) / 2);
  const branch = earthlyBranches[branchIndex];
  
  const ke = Math.floor(minutes / 15) + 1;
  const keStr = ['初', '一刻', '二刻', '三刻'][ke - 1] || '初';

  return `${branch}时${keStr}`;
};

// --- Components ---

const TopBar = ({ title, onBack, rightElement }: { title: string, onBack?: () => void, rightElement?: React.ReactNode }) => (
  <div className="flex items-center justify-between px-4 py-6 shrink-0">
    <div className="w-20">
      {onBack && (
        <button onClick={onBack} className="inline-flex items-center gap-1.5 px-4 py-2 border border-ink-pale rounded-full text-sm text-ink-medium hover:bg-ink-pale/50 transition-colors">
          <ChevronLeft size={16} />
          返回
        </button>
      )}
    </div>
    <div className="text-lg font-semibold tracking-[0.15em] text-ink-dark">{title}</div>
    <div className="w-20 flex justify-end">
      {rightElement}
    </div>
  </div>
);

const QuickPrompt = ({ icon, title, desc, onClick }: { icon: React.ReactNode, title: string, desc: string, onClick: () => void }) => (
  <button onClick={onClick} className="relative p-3.5 bg-paper-white rounded-2xl border border-ink-pale text-left hover:bg-ink-pale/30 transition-colors group overflow-hidden">
    <div className="absolute top-0 right-0 w-4 h-4 bg-gradient-to-bl from-celadon-transparent to-transparent rounded-tr-2xl" />
    <div className="w-9 h-9 rounded-xl bg-celadon-transparent flex items-center justify-center mb-2 text-celadon-dark group-hover:scale-110 transition-transform">
      {icon}
    </div>
    <div className="text-xs font-semibold text-ink-dark mb-1">{title}</div>
    <div className="text-[10px] leading-relaxed text-ink-light">{desc}</div>
  </button>
);

const HeroCard = ({ onHistoryClick, onPromptClick }: { onHistoryClick: () => void, onPromptClick: (text: string) => void }) => (
  <div className="relative bg-paper-white/85 backdrop-blur-md rounded-3xl shadow-[0_16px_48px_rgba(26,26,26,0.08)] p-6 mx-4 my-2 shrink-0">
    <div className="absolute inset-1.5 border border-celadon/10 rounded-[20px] pointer-events-none" />
    <div className="absolute top-3 right-4 w-7 h-7 bg-cinnabar text-white text-base flex items-center justify-center rounded-sm opacity-20 rotate-12 font-serif">壹</div>
    
    <div className="flex justify-between items-start gap-4">
      <div>
        <div className="inline-block px-2.5 py-1 bg-celadon-dark text-white text-[11px] tracking-[0.15em] mb-2.5">AI 助理</div>
        <div className="text-2xl font-bold leading-snug tracking-wider text-ink-dark">智能对话<br/>诗意人生</div>
        <div className="mt-2 text-[13px] leading-relaxed text-ink-light">连接已建立，言语之间，智慧自现。</div>
      </div>
      <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[11px] bg-celadon-transparent text-celadon-dark">
        <div className="w-2 h-2 rounded-full bg-current" />
        <span>已连接</span>
      </div>
    </div>

    <div className="flex gap-2.5 mt-4">
      <span className="px-3.5 py-1.5 rounded-full text-xs bg-celadon-transparent text-celadon-dark">账号模式</span>
      <button onClick={onHistoryClick} className="px-3.5 py-1.5 rounded-full text-xs bg-transparent border border-ink-pale text-ink-medium hover:bg-ink-pale/50 transition-colors">历史记录</button>
    </div>

    <div className="grid grid-cols-3 gap-2.5 mt-5">
      <QuickPrompt icon={<Sparkles size={18} />} title="文案润色" desc="笔墨生辉，字字珠玑" onClick={() => onPromptClick("请帮我润色一段文案，使其更具诗意：")} />
      <QuickPrompt icon={<Briefcase size={18} />} title="工作助手" desc="条理分明，事半功倍" onClick={() => onPromptClick("请帮我整理一份工作计划：")} />
      <QuickPrompt icon={<Lightbulb size={18} />} title="学习提问" desc="深入浅出，明理通达" onClick={() => onPromptClick("请用通俗易懂的方式解释：")} />
    </div>
  </div>
);

const MessageBubble = ({ message }: { message: Message; key?: React.Key }) => {
  const isAi = message.role === 'ai';
  
  return (
    <div className={`flex gap-2.5 mb-5 ${isAi ? '' : 'flex-row-reverse'}`}>
      <div className={`w-9 h-9 flex items-center justify-center text-xs font-semibold shrink-0 rounded-xl text-white ${isAi ? 'bg-gradient-to-br from-ink-dark to-ink-medium' : 'bg-gradient-to-br from-celadon-dark to-celadon'}`}>
        {isAi ? 'AI' : '吾'}
      </div>
      <div className={`max-w-[72%] ${isAi ? '' : 'flex flex-col items-end'}`}>
        <div className={`flex items-center gap-1.5 mb-1 px-1 ${isAi ? '' : 'flex-row-reverse'}`}>
          <span className="text-xs font-semibold text-ink-dark">{isAi ? '壹' : '你'}</span>
          <span className="text-[10px] text-ink-clear">{formatTime(message.timestamp)}</span>
        </div>
        <div className={`px-3.5 py-3 text-sm leading-relaxed whitespace-pre-wrap ${isAi ? 'bg-paper-white text-ink-dark rounded-[18px_24px_24px_18px] border border-ink-pale' : 'bg-gradient-to-br from-celadon-dark to-celadon text-white rounded-[24px_18px_18px_24px]'}`}>
          {message.content}
        </div>
      </div>
    </div>
  );
};

const TypingIndicator = () => (
  <div className="flex gap-2.5 mb-5">
    <div className="w-9 h-9 flex items-center justify-center text-xs font-semibold shrink-0 rounded-xl text-white bg-gradient-to-br from-ink-dark to-ink-medium">
      AI
    </div>
    <div className="max-w-[72%]">
      <div className="flex items-center gap-1.5 mb-1 px-1">
        <span className="text-xs font-semibold text-ink-dark">壹</span>
        <span className="text-[10px] text-ink-clear">凝思中</span>
      </div>
      <div className="px-3.5 py-3 bg-paper-white rounded-[18px_24px_24px_18px] border border-ink-pale flex items-center gap-1 h-[46px]">
        <motion.div className="w-2 h-2 rounded-full bg-celadon-dark" animate={{ y: [0, -6, 0], opacity: [0.3, 1, 0.3] }} transition={{ duration: 1.6, repeat: Infinity, delay: 0 }} />
        <motion.div className="w-2 h-2 rounded-full bg-celadon-dark" animate={{ y: [0, -6, 0], opacity: [0.3, 1, 0.3] }} transition={{ duration: 1.6, repeat: Infinity, delay: 0.2 }} />
        <motion.div className="w-2 h-2 rounded-full bg-celadon-dark" animate={{ y: [0, -6, 0], opacity: [0.3, 1, 0.3] }} transition={{ duration: 1.6, repeat: Infinity, delay: 0.4 }} />
      </div>
    </div>
  </div>
);

const Composer = ({ input, setInput, onSend, onMenuClick }: { input: string, setInput: (v: string) => void, onSend: () => void, onMenuClick: () => void }) => (
  <div className="absolute left-4 right-4 bottom-6 z-10">
    <div className="flex items-center gap-2.5 p-2.5 bg-paper-white/90 backdrop-blur-md rounded-3xl border border-ink-pale shadow-[0_16px_48px_rgba(26,26,26,0.08)]">
      <button onClick={onMenuClick} className="w-10 h-10 rounded-xl bg-ink-pale flex items-center justify-center text-ink-medium hover:bg-ink-pale/80 transition-colors shrink-0">
        <Menu size={20} />
      </button>
      <input 
        type="text" 
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && onSend()}
        placeholder="请言..."
        className="flex-1 h-10 px-3.5 bg-paper-white rounded-xl text-sm text-ink-dark outline-none placeholder:text-ink-clear min-w-0"
      />
      <button 
        onClick={onSend}
        disabled={!input.trim()}
        className="h-10 px-5 bg-ink-dark text-white text-[13px] font-semibold tracking-widest rounded-xl disabled:opacity-50 hover:bg-ink-medium transition-colors shrink-0"
      >
        发送
      </button>
    </div>
  </div>
);

const QuickAction = ({ icon, label, colorClass }: { icon: React.ReactNode, label: string, colorClass: string }) => (
  <button className="flex flex-col items-center gap-2 p-3 bg-paper-white rounded-xl border border-ink-pale hover:bg-ink-pale/30 transition-colors">
    <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${colorClass}`}>
      {icon}
    </div>
    <span className="text-[11px] text-ink-medium whitespace-nowrap">{label}</span>
  </button>
);

const MenuDrawer = ({
  onClose,
  onClearHistory,
  token,
  asset,
  userInfo,
  onLoginClick,
  onLogout,
}: {
  onClose: () => void;
  onClearHistory: () => void;
  token: string;
  asset: { n?: number | string; dfn?: number | string } | null;
  userInfo: { id?: string; nick_name?: string; username?: string } | null;
  onLoginClick: () => void;
  onLogout: () => void;
}) => (
  <>
    <motion.div 
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      onClick={onClose}
      className="absolute inset-0 bg-ink-dark/30 z-40 backdrop-blur-sm"
    />
    <motion.div 
      initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
      transition={{ type: 'spring', damping: 25, stiffness: 200 }}
      className="absolute bottom-0 left-0 right-0 bg-paper-white rounded-t-3xl pb-6 z-50 shadow-[0_-10px_40px_rgba(0,0,0,0.1)]"
    >
      <div className="flex justify-center py-3">
        <div className="w-10 h-1 rounded-full bg-ink-pale" />
      </div>
      
      <div className="flex items-center justify-between px-5 pb-4 border-b border-ink-pale">
        <span className="text-base font-semibold tracking-widest text-ink-dark">我的菜单</span>
        <button onClick={onClose} className="w-7 h-7 rounded-full bg-ink-pale flex items-center justify-center text-ink-medium hover:bg-ink-pale/80">
          <X size={16} />
        </button>
      </div>

      <div className="p-5">
        <div className="flex items-center gap-3.5 p-4 bg-celadon-transparent rounded-2xl mb-3.5">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-celadon-dark to-celadon flex items-center justify-center text-base font-semibold text-white">吾</div>
          <div>
            <div className="text-[15px] font-semibold text-ink-dark mb-1">{userInfo?.nick_name || userInfo?.username || '雅士'}</div>
            <div className="text-xs text-ink-light">ID：{userInfo?.id || '壹贰叁肆伍'}</div>
          </div>
        </div>

        <div className="flex items-center justify-between p-3.5 bg-paper-white rounded-xl border border-ink-pale mb-3.5">
          <div>
            <div className="text-sm font-semibold text-ink-dark mb-1">登录状态</div>
            <div className="text-xs text-ink-light">{token ? '已登录' : '未登录'}</div>
          </div>
          {token ? (
            <button onClick={onLogout} className="px-4 py-2 bg-ink-pale text-ink-medium text-[13px] font-medium rounded-full hover:bg-ink-pale/70 transition-colors">退出</button>
          ) : (
            <button onClick={onLoginClick} className="px-4 py-2 bg-celadon-dark text-white text-[13px] font-medium rounded-full hover:bg-celadon transition-colors">登录</button>
          )}
        </div>

        <div className="grid grid-cols-2 gap-2.5 mb-3.5">
          <div className="p-3.5 bg-paper-white rounded-xl border border-ink-pale text-center">
            <div className="text-2xl font-bold text-celadon-dark">{asset?.n ?? '--'}</div>
            <div className="text-[11px] text-ink-light mt-1">拥有额度</div>
          </div>
          <div className="p-3.5 bg-paper-white rounded-xl border border-ink-pale text-center">
            <div className="text-2xl font-bold text-celadon-dark">{asset?.dfn ?? '--'}</div>
            <div className="text-[11px] text-ink-light mt-1">每日免费</div>
          </div>
        </div>

        <div className="flex justify-between items-center p-3.5 bg-paper-white rounded-xl border border-ink-pale mb-2.5">
          <div>
            <div className="text-sm font-semibold text-ink-dark mb-1">邀请新友</div>
            <div className="text-xs text-ink-light">赠十次/人，日限五人</div>
          </div>
          <button className="px-4 py-2 bg-celadon-dark text-white text-[13px] font-medium rounded-full hover:bg-celadon transition-colors">邀请</button>
        </div>

        <div className="flex justify-between items-center p-3.5 bg-paper-white rounded-xl border border-ink-pale mb-2.5">
          <div>
            <div className="text-sm font-semibold text-ink-dark mb-1">观视频攒次数</div>
            <div className="text-xs text-ink-light">赠三次/个，日限十次</div>
          </div>
          <button className="px-4 py-2 bg-celadon-dark text-white text-[13px] font-medium rounded-full hover:bg-celadon transition-colors">观看</button>
        </div>

        <div className="grid grid-cols-4 gap-2.5 mt-3.5">
          <QuickAction icon={<Crown size={18} />} label="开通会员" colorClass="bg-cinnabar-transparent text-cinnabar-dark" />
          <QuickAction icon={<Package size={18} />} label="次数包" colorClass="bg-celadon-transparent text-celadon-dark" />
          <QuickAction icon={<MessageCircle size={18} />} label="客服领次数" colorClass="bg-celadon-transparent text-celadon-dark" />
          <div onClick={() => { onClearHistory(); onClose(); }}>
            <QuickAction icon={<Trash2 size={18} />} label="清除记忆" colorClass="bg-ink-pale text-ink-medium" />
          </div>
        </div>
      </div>
    </motion.div>
  </>
);

// --- Main App ---

export default function App() {
  const [currentView, setCurrentView] = useState<'chat' | 'history'>('chat');
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    { id: 'initial', role: 'ai', content: '客官好，在下壹能，有何见教？', timestamp: new Date() }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userId] = useState(() => ensureUserId());
  const [asset, setAsset] = useState<{ n?: number | string; dfn?: number | string } | null>(null);
  const [token, setTokenState] = useState(() => getToken());
  const [userInfo, setUserInfo] = useState<{ id?: string; nick_name?: string; username?: string } | null>(null);
  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginForm, setLoginForm] = useState({ username: '', password: '' });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isTyping]);

  const refreshAsset = async () => {
    try {
      const res = await getUserChatAssetApi();
      if (res.status === 'success') {
        setAsset(res.data || null);
        return true;
      }
      if (res.message && res.message.includes('令牌失效')) {
        setToken('');
        setTokenState('');
      }
      setAsset(null);
      return false;
    } catch (err) {
      console.error(err);
      setAsset(null);
      return false;
    }
  };

  const refreshUserInfo = async () => {
    try {
      const res = await getUserInfoApi();
      if (res.status === 'success') {
        setUserInfo(res.data?.userInfo || null);
        return true;
      }
      if (res.message && res.message.includes('令牌失效')) {
        setToken('');
        setTokenState('');
      }
      setUserInfo(null);
      return false;
    } catch (err) {
      console.error(err);
      setUserInfo(null);
      return false;
    }
  };

  useEffect(() => {
    if (!token) {
      setAsset(null);
      setUserInfo(null);
      return;
    }
    refreshAsset();
    refreshUserInfo();
  }, [token]);

  const handleLogin = async () => {
    if (!loginForm.username.trim() || !loginForm.password.trim()) {
      setError('请输入用户名和密码');
      return;
    }
    setIsLoggingIn(true);
    setError(null);
    try {
      const res = await loginApi({
        username: loginForm.username.trim(),
        password: loginForm.password.trim(),
      });
      if (res.status !== 'success' || !res.data?.tokenInfo?.tokenValue) {
        const message = res.message || '登录失败';
        if (message.includes('无效的用户') || message.includes('用户名')) {
          setError('用户名不存在或无效');
        } else if (message.includes('密码不正确')) {
          setError('密码错误');
        } else if (message.includes('请输入用户名和密码')) {
          setError('请输入用户名和密码');
        } else {
          setError(message);
        }
        return;
      }
      const tokenValue = res.data.tokenInfo.tokenValue;
      setToken(tokenValue);
      setTokenState(tokenValue);
      setIsLoginOpen(false);
      setLoginForm({ username: '', password: '' });
    } catch (err) {
      console.error(err);
      setError('网络连接异常，请稍后再试~');
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleLogout = () => {
    setToken('');
    setTokenState('');
    setAsset(null);
    setUserInfo(null);
  };

  const handleSend = async () => {
    if (!input.trim()) return;

    const userText = input.trim();
    const newUserMsg: Message = { id: Date.now().toString(), role: 'user', content: userText, timestamp: new Date() };
    
    setMessages(prev => [...prev, newUserMsg]);
    setInput('');
    setIsTyping(true);
    setError(null);

    try {
      const response = await sendMsgApi({
        userId,
        question: userText,
      });

      if (!response || response.status !== 'success') {
        setError(response?.message || '服务返回异常，请稍后再试~');
        return;
      }

      const newAiMsg: Message = { 
        id: (Date.now() + 1).toString(), 
        role: 'ai', 
        content: response.data?.ack || '（无言）', 
        timestamp: new Date() 
      };
      setMessages(prev => [...prev, newAiMsg]);
      if (token) {
        refreshAsset();
      }
    } catch (err) {
      console.error(err);
      setError('网络连接异常，请稍后再试~');
    } finally {
      setIsTyping(false);
    }
  };

  const handleClearHistory = () => {
    setMessages([{ id: Date.now().toString(), role: 'ai', content: '客官好，在下壹能，有何见教？', timestamp: new Date() }]);
  };

  return (
    <div className="w-full h-screen max-w-md mx-auto bg-paper-white relative overflow-hidden font-serif flex flex-col shadow-2xl sm:rounded-[40px] sm:h-[812px] sm:my-8 sm:border-[8px] sm:border-ink-dark">
      {/* Background gradients */}
      <div className="absolute top-[-10%] left-[-20%] w-[140%] h-[70%] bg-[radial-gradient(ellipse_at_center,_var(--color-celadon-transparent)_0%,_transparent_70%)] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[-20%] w-[120%] h-[60%] bg-[radial-gradient(ellipse_at_center,_var(--color-gold-transparent)_0%,_transparent_70%)] pointer-events-none" />

      {currentView === 'chat' ? (
        <div className="flex flex-col h-full relative z-0">
          <TopBar title="壹能聊天" />
          
          <div className="flex-1 overflow-y-auto px-4 pb-28 pt-2">
            <HeroCard 
              onHistoryClick={() => setCurrentView('history')} 
              onPromptClick={(text) => setInput(text)}
            />
            
            <div className="mt-6 px-2">
              {messages.map(m => <MessageBubble key={m.id} message={m} />)}
              {isTyping && <TypingIndicator />}
              
              {error && (
                <div className="flex justify-center mb-4">
                  <div className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-cinnabar-transparent text-cinnabar-dark text-xs rounded-full">
                    <AlertTriangle size={14} />
                    <span>{error}</span>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          </div>

          <Composer 
            input={input} 
            setInput={setInput} 
            onSend={handleSend} 
            onMenuClick={() => setIsMenuOpen(true)} 
          />
        </div>
      ) : (
        <div className="flex flex-col h-full relative z-0">
          <TopBar title="聊天历史" onBack={() => setCurrentView('chat')} />
          
          <div className="flex-1 overflow-y-auto px-6 py-4 pb-24">
            {messages.length <= 1 ? (
              <div className="flex flex-col items-center justify-center h-full text-ink-light opacity-60">
                <MessageCircle size={48} className="mb-4" />
                <p>暂无历史记录</p>
              </div>
            ) : (
              messages.map(m => <MessageBubble key={m.id} message={m} />)
            )}
          </div>

          {messages.length > 1 && (
            <div className="absolute bottom-8 left-0 right-0 flex justify-center">
              <button 
                onClick={handleClearHistory}
                className="flex items-center gap-1.5 px-5 py-2.5 bg-paper-white border border-cinnabar-transparent rounded-full text-[13px] text-cinnabar-dark shadow-sm hover:bg-cinnabar-transparent/50 transition-colors"
              >
                <Trash2 size={16} />
                清空历史
              </button>
            </div>
          )}
        </div>
      )}

      <AnimatePresence>
        {isMenuOpen && (
          <MenuDrawer
            onClose={() => setIsMenuOpen(false)}
            onClearHistory={handleClearHistory}
            token={token}
            asset={asset}
            userInfo={userInfo}
            onLoginClick={() => setIsLoginOpen(true)}
            onLogout={handleLogout}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {isLoginOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              onClick={() => setIsLoginOpen(false)}
              className="absolute inset-0 bg-ink-dark/40 z-50 backdrop-blur-sm"
            />
            <motion.div
              initial={{ y: 40, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 40, opacity: 0 }}
              transition={{ type: 'spring', damping: 22, stiffness: 240 }}
              className="absolute left-6 right-6 top-1/2 -translate-y-1/2 bg-paper-white rounded-3xl p-6 z-[60] shadow-[0_20px_60px_rgba(0,0,0,0.2)] border border-ink-pale"
            >
              <div className="text-base font-semibold text-ink-dark tracking-widest mb-4">账号登录</div>
              <div className="flex flex-col gap-3">
                <input
                  type="text"
                  placeholder="用户名"
                  value={loginForm.username}
                  onChange={(e) => setLoginForm(prev => ({ ...prev, username: e.target.value }))}
                  className="h-11 px-4 bg-paper-white rounded-xl border border-ink-pale text-sm text-ink-dark outline-none placeholder:text-ink-clear"
                />
                <input
                  type="password"
                  placeholder="密码"
                  value={loginForm.password}
                  onChange={(e) => setLoginForm(prev => ({ ...prev, password: e.target.value }))}
                  className="h-11 px-4 bg-paper-white rounded-xl border border-ink-pale text-sm text-ink-dark outline-none placeholder:text-ink-clear"
                />
                <button
                  onClick={handleLogin}
                  disabled={isLoggingIn}
                  className="h-11 rounded-xl bg-ink-dark text-white text-[13px] font-semibold tracking-widest hover:bg-ink-medium transition-colors disabled:opacity-60"
                >
                  {isLoggingIn ? '登录中...' : '登录'}
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
