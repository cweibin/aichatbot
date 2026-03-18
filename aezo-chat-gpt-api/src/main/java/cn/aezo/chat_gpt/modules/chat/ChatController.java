package cn.aezo.chat_gpt.modules.chat;

import cn.aezo.chat_gpt.limit.LimitRequestNum;
import cn.aezo.chat_gpt.util.MiscU;
import cn.aezo.chat_gpt.util.Result;
import cn.aezo.chat_gpt.util.ValidU;
import cn.aezo.chat_gpt.modules.chat.websocket.MessageLocalCache;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * HTTP方式进行聊天<br/>
 * 阻塞式接口走 chat/completions 兼容协议<br/>
 * 模型通过 sq-mini-tools.openai.chat-model 配置<br/>
 * 通过WSS等流式响应时，可选择聊天模型进行流式输出<br/>
 * 更多模型参考<br/>
 * @see com.unfbx.chatgpt.entity.chat.ChatCompletion.Model
 */
@Slf4j
@RestController
@RequestMapping("/tools/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    @Qualifier("assetChatService")
    private cn.aezo.chat_gpt.service.ChatService assetChatService;
    /**
     * 阻塞式对话，兼容 OpenAI chat/completions 协议
     * 流式会话请参考 WebSocketServer
     */
    @LimitRequestNum
    @RequestMapping("/sendMsg")
    public Result sendMsg(HttpServletRequest request, @RequestBody Map<String, Object>parmas) {
        String userId = (String) parmas.get("userId");
        String question = (String) parmas.get("question");
        if(ValidU.isEmpty(question)) {
            return Result.failure("请先输入您的问题哦");
        }
        log.info(userId + "发送消息: " + question);
        String ack = chatService.chatDirectly(question, userId);
        log.info(userId + "接收消息: " + ack);
        return Result.success(MiscU.Instance.toMap("ack", ack));
    }

    @RequestMapping("/getUserChatAsset")
    public Result getUserChatAsset() {
        return assetChatService.getUserChatAsset();
    }

    @PostMapping("/rewardedVideoAdAsset")
    public Result rewardedVideoAdAsset() {
        return assetChatService.rewardedVideoAdAsset();
    }

    @RequestMapping("/startNewChat")
    public Result startNewChat() {
        String userId = StpUtil.getLoginIdAsString();
        MessageLocalCache.CACHE.remove(userId);
        return Result.success();
    }

    @RequestMapping("/findPromptList")
    public Result findPromptList(@RequestBody Map<String, Object> params) {
        return assetChatService.findPromptList(params);
    }
}
