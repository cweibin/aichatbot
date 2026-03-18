package cn.aezo.chat_gpt.modules.chat;

import cn.aezo.chat_gpt.config.BizException;
import cn.aezo.chat_gpt.modules.chat.mapper.ChatMapper;
import cn.aezo.chat_gpt.util.MiscU;
import cn.aezo.chat_gpt.util.Result;
import cn.aezo.chat_gpt.util.SpringU;
import cn.aezo.chat_gpt.util.ValidU;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {
    @Value("${sq-mini-tools.chat.regist-num:10}")
    private String chatRegistNum;

    @Value("${sq-mini-tools.chat.daily-free-num:5}")
    private String chatDailyFreeNum;

    @Value("${sq-mini-tools.openai.api-host:}")
    private String apiHost;

    @Value("${sq-mini-tools.openai.api-key:}")
    private String apiKey;

    @Value("${sq-mini-tools.openai.chat-model:gpt-3.5-turbo}")
    private String chatModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ChatMapper chatMapper;

    public String chatDirectly(String question, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", question);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModel);
        requestBody.put("messages", Arrays.asList(message));
        requestBody.put("user", userId);

        String apiUrl = StrUtil.removeSuffix(apiHost, "/") + "/v1/chat/completions";
        try {
            String responseBody = restTemplate.postForObject(apiUrl, new HttpEntity<>(requestBody, headers), String.class);
            return removeIrrelevantChar(parseChatContent(responseBody), userId);
        } catch (HttpStatusCodeException e) {
            throw new BizException(resolveErrorMessage(e.getResponseBodyAsString(), e.getStatusText()), "chat.openai_error", e);
        } catch (Exception e) {
            throw new BizException("调用 chat/completions 失败", "chat.openai_error", e);
        }
    }

    public Result getUserChatAsset() {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> assetMap = getUserChatAsset(userId);
        if(assetMap.get("N") == null) {
            SpringU.getBean(ChatService.class).createChatAsset(userId);
            assetMap = getUserChatAsset(userId);
        }
        return Result.success(assetMap);
    }

    public Map<String, Object> getUserChatAsset(String userId) {
        Map<String, Object> assetMap = jdbcTemplate.queryForMap(
                "select sum(case t.asset_type when 'N' then t.asset else 0 end) as n " +
                        ",max(case t.asset_type when 'N' then t.version else 0 end) as version_n " +
                        ",sum(case t.asset_type when 'DFN' then t.asset else 0 end) as dfn " +
                        ",max(case t.asset_type when 'DFN' then t.version else 0 end) as version_dfn " +
                        ",max(case when t.asset_type = 'DFN' and t.create_time < CURDATE() then 1 else 0 end) as old_dfn " +
                        "from mt_user_asset t where t.valid_status = 1 and t.biz_type = 'Chat' and t.user_id = ?", userId);
        if("1".equals(assetMap.get("old_dfn") + "")) {
            int count = jdbcTemplate.update(
                    "update mt_user_asset set asset = ?, version=version+1 ,create_time = now(), update_time = now() " +
                            "where valid_status = 1 and biz_type = 'Chat' and asset_type = 'DFN' and version = ? and user_id = ?",
                    chatDailyFreeNum, assetMap.get("version_dfn"), userId);
            if(count > 0) {
                assetMap.put("dfn", new BigDecimal(chatDailyFreeNum));
                assetMap.put("version_dfn", Integer.parseInt(assetMap.get("version_dfn").toString()) + 1);
                assetMap.put("old_dfn", new BigDecimal(0));
            }
        }
        return assetMap;
    }

    /**
     * 创建聊天资产
     * @author smalle
     * @since 2023/3/26
     */
    @Transactional(rollbackFor = Exception.class)
    public Result createChatAsset(String userId) {
        jdbcTemplate.update("insert into mt_user_asset(id, user_id, biz_type, asset_type, asset, create_time, update_time) " +
                "values(?, ?, 'Chat', 'N', ?, now(), now())", IdUtil.getSnowflakeNextIdStr(), userId, chatRegistNum);

        jdbcTemplate.update("insert into mt_user_asset(id, user_id, biz_type, asset_type, asset, create_time, update_time) " +
                "values(?, ?, 'Chat', 'DFN', ?, now(), now())", IdUtil.getSnowflakeNextIdStr(), userId, chatDailyFreeNum);
        return Result.success();
    }

    @Transactional(rollbackFor = Exception.class)
    public Result checkAndUpdateAsset(String userId) {
        Map<String, Object> userChatAsset = getUserChatAsset(userId);
        BigDecimal n = (BigDecimal) userChatAsset.get("n");
        Integer versionN = Integer.valueOf(userChatAsset.get("version_n").toString());
        BigDecimal dfn = (BigDecimal) userChatAsset.get("dfn");
        Integer versionDfn = Integer.valueOf(userChatAsset.get("version_dfn").toString());
        String assetType = "N";
        int assetRate = -1;
        if(dfn.compareTo(BigDecimal.ZERO) > 0) {
            dfn = NumberUtil.add(dfn, assetRate);
            assetType = "DFN";
        } else if(n.compareTo(BigDecimal.ZERO) > 0) {
            n = NumberUtil.add(n, assetRate);
        } else {
            return Result.failure("剩余次数不足", "chat.asset_short");
        }
        int upCount = chatMapper.updateUserAsset(userId, assetType, "N".equals(assetType) ? n : dfn, "N".equals(assetType) ? versionN : versionDfn);
        if(upCount == 0) {
            return Result.failure("接收消息失败, 请重新发送试试~", "chat.asset_calc");
        }
        upCount = chatMapper.insertUserAssetHis(MiscU.Instance.toMap(
                "id", IdUtil.getSnowflakeNextIdStr(), "userId", userId, "bizType", "Chat",
                "assetType", assetType, "asset", assetRate, "remark", "聊天消耗"));
        if(upCount == 0) {
            return Result.failure("接收消息失败, 请重新发送试试~", "chat.asset_his");
        }
        return Result.success();
    }

    private static String parseChatContent(String responseBody) {
        if(StrUtil.isBlank(responseBody)) {
            throw new BizException("chat/completions 返回为空", "chat.openai_error");
        }

        JSONObject response = JSONUtil.parseObj(responseBody);
        JSONArray choices = response.getJSONArray("choices");
        if(ValidU.isEmpty(choices)) {
            throw new BizException(resolveErrorMessage(responseBody, "chat/completions 未返回 choices"), "chat.openai_error");
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice == null ? null : firstChoice.getJSONObject("message");
        String content = message == null ? null : message.getStr("content");
        if(StrUtil.isBlank(content)) {
            throw new BizException(resolveErrorMessage(responseBody, "chat/completions 未返回 message.content"), "chat.openai_error");
        }
        return content;
    }

    private static String resolveErrorMessage(String responseBody, String defaultMessage) {
        if(StrUtil.isBlank(responseBody)) {
            return defaultMessage;
        }
        try {
            JSONObject response = JSONUtil.parseObj(responseBody);
            String message = response.getStr("message");
            if(StrUtil.isBlank(message)) {
                JSONObject error = response.getJSONObject("error");
                if(error != null) {
                    message = error.getStr("message");
                }
            }
            return StrUtil.blankToDefault(message, defaultMessage);
        } catch (Exception e) {
            return defaultMessage;
        }
    }

    private static String removeIrrelevantChar(String msg, String userId) {
        if(msg == null) {
            msg = "";
        }
        return msg.replaceAll("openai:", "")
                .replaceAll("openai：", "")
                .replaceAll("OpenAi:", "")
                .replaceAll("OpenAi：", "")
                .replaceAll("OpenAI：", "")
                .replaceAll("OpenAI:", "")
                .replaceAll(userId + ":", "")
                .replaceAll(userId + "：", "")
                .replaceAll("^\\n|\\n$", "");
    }
}
