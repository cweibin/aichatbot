import { post } from '@/util/request.js'

export const sendMsgApi = params => post('/tools/chat/sendMsg', params)
