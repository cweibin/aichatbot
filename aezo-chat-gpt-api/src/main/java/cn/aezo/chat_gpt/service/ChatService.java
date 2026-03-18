package cn.aezo.chat_gpt.service;

import cn.aezo.chat_gpt.config.BizException;
import cn.aezo.chat_gpt.mapper.AssetChatMapper;
import cn.aezo.chat_gpt.util.MiscU;
import cn.aezo.chat_gpt.util.Result;
import cn.aezo.chat_gpt.util.SpringU;
import cn.aezo.chat_gpt.util.ValidU;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.common.Choice;
import com.unfbx.chatgpt.entity.completions.CompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("assetChatService")
public class ChatService {
    @Value("${aezo-chat-gpt.chat.regist-num:${sq-mini-tools.chat.regist-num:10}}")
    private String chatRegistNum;

    @Value("${aezo-chat-gpt.chat.daily-free-num:${sq-mini-tools.chat.daily-free-num:5}}")
    private String chatDailyFreeNum;

    @Value("${aezo-chat-gpt.chat.regist-num-m2:${sq-mini-tools.chat.regist-num-m2:0}}")
    private String chatRegistNumM2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssetChatMapper chatMapper;

    public Result getUserChatAsset() {
        SaSession session = StpUtil.getSession();
        synchronized (session) {
            String userId = StpUtil.getLoginIdAsString();
            Map<String, Object> assetMap = getUserChatAsset(userId);
            if(assetMap.get("N") == null) {
                SpringU.getBean(ChatService.class).createChatAsset(userId, null);
                assetMap = getUserChatAsset(userId);
            } else if(new BigDecimal(assetMap.get("VERSION_M2").toString()).compareTo(BigDecimal.ZERO) == 0) {
                SpringU.getBean(ChatService.class).createChatAsset(userId, "M2");
                assetMap = getUserChatAsset(userId);
            }
            return Result.success(assetMap);
        }
    }

    public Map<String, Object> getUserChatAsset(String userId) {
        Map<String, Object> assetMap = jdbcTemplate.queryForMap(
                "select sum(case t.asset_type when 'N' then t.asset else 0 end) as n " +
                        ",max(case t.asset_type when 'N' then t.version else 0 end) as version_n " +
                        ",sum(case t.asset_type when 'DFN' then t.asset else 0 end) as dfn " +
                        ",max(case t.asset_type when 'DFN' then t.version else 0 end) as version_dfn " +
                        ",sum(case t.asset_type when 'M2' then t.asset else 0 end) as m2 " +
                        ",max(case t.asset_type when 'M2' then t.version else 0 end) as version_m2 " +
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
    public Result createChatAsset(String userId, String key) {
        if(key == null) {
            jdbcTemplate.update("insert into mt_user_asset(id, user_id, biz_type, asset_type, asset, create_time, update_time) " +
                    "values(?, ?, 'Chat', 'N', ?, now(), now())", IdUtil.getSnowflakeNextIdStr(), userId, chatRegistNum);

            jdbcTemplate.update("insert into mt_user_asset(id, user_id, biz_type, asset_type, asset, create_time, update_time) " +
                    "values(?, ?, 'Chat', 'DFN', ?, now(), now())", IdUtil.getSnowflakeNextIdStr(), userId, chatDailyFreeNum);
        }
        if(key == null || "M2".equals(key)) {
            jdbcTemplate.update("insert into mt_user_asset(id, user_id, biz_type, asset_type, asset, create_time, update_time) " +
                    "values(?, ?, 'Chat', 'M2', ?, now(), now())", IdUtil.getSnowflakeNextIdStr(), userId, chatRegistNumM2);
        }
        return Result.success();
    }

    @Transactional(rollbackFor = Exception.class)
    public void checkAndUpdateAsset(String userId, String assetType, int assetRate, String updateMsg) {
        Map<String, Object> userAssetMap = getUserAsset(userId, assetType, assetRate);
        assetType = (String) userAssetMap.get("assetType");
        int upCount = chatMapper.updateUserAsset(userId, assetType,
                (BigDecimal) userAssetMap.get("asset"), (Integer) userAssetMap.get("version"));
        if(upCount == 0) {
            throw new BizException("次数更新失败, 请稍后再试~", "chat.asset_calc");
        }
        upCount = chatMapper.insertUserAssetHis(MiscU.Instance.toMap(
                "id", IdUtil.getSnowflakeNextIdStr(), "userId", userId, "bizType", "Chat",
                "assetType", assetType, "asset", assetRate, "remark", updateMsg));
        if(upCount == 0) {
            throw new BizException("次数更新失败, 请稍后再试~", "chat.asset_his");
        }
    }

    private Map<String, Object> getUserAsset(String userId, String assetType, int assetRate) {
        Map<String, Object> userChatAsset = getUserChatAsset(userId);
        BigDecimal assetRemain = null;
        if(ValidU.isNotEmpty(assetType)) {
            BigDecimal asset = (BigDecimal) userChatAsset.get(assetType);
            BigDecimal assetRemainTemp = NumberUtil.add(asset, assetRate);
            if(BigDecimal.ZERO.compareTo(new BigDecimal(assetRate)) > 0 && BigDecimal.ZERO.compareTo(assetRemainTemp) > 0) {
                throw new BizException("剩余次数不足", "chat.asset_short");
            }
            assetRemain = assetRemainTemp;
        } else {
            String[] typeArr = new String[]{"dfn", "n"};
            for (String type : typeArr) {
                BigDecimal asset = (BigDecimal) userChatAsset.get(type);
                BigDecimal assetItem = NumberUtil.add(asset, assetRate);
                if(BigDecimal.ZERO.compareTo(new BigDecimal(assetRate)) > 0 && BigDecimal.ZERO.compareTo(assetItem) > 0) {
                    continue;
                }
                assetType = type;
                assetRemain = assetItem;
                break;
            }
            if(ValidU.isEmpty(assetType)) {
                throw new BizException("剩余次数不足", "chat.asset_short");
            }
        }
        Map<String, Object> assetTypeMap = new HashMap<>();
        assetTypeMap.put("asset", assetRemain);
        assetTypeMap.put("version", Integer.valueOf(userChatAsset.get("version_" + assetType).toString()));
        assetTypeMap.put("assetType", assetType.toUpperCase());
        return assetTypeMap;
    }

    public Result rewardedVideoAdAsset() {
        String remark = "观看激励视频";
        Integer count = jdbcTemplate.queryForObject("select count(1) from mt_user_asset_his where user_id = ? and biz_type = 'Chat'" +
                " and remark = ? and create_time > CURDATE()", Integer.class, StpUtil.getLoginIdAsString(), remark);
        Integer adsMax = Integer.valueOf(SpringU.getEnv("aezo-chat-gpt.chat.ads-max"));
        if(count.compareTo(adsMax) >= 0) {
            return Result.failure("您今天观看的视频次数已达上限", MiscU.Instance.toMap("rewardedCount", count));
        }
        Integer adsNum = Integer.valueOf(SpringU.getEnv("aezo-chat-gpt.chat.ads-num"));
        checkAndUpdateAsset(StpUtil.getLoginIdAsString(), "dfn", adsNum, remark);
        return Result.success(MiscU.Instance.toMap("rewardedCount", count + 1));
    }

    public Result findPromptList(Map<String, Object> params) {
        if(ValidU.isNotEmpty(params.get("id"))) {
            List<Map<String, Object>> promptList = chatMapper.findPromptList(params);
            return Result.success(ValidU.isNotEmpty(promptList) ? promptList.get(0) : new HashMap<>());
        } else if(ValidU.isNotEmpty(params.get("ids")) || "1".equals(params.get("commonOpt") + "")) {
            List<Map<String, Object>> promptList = chatMapper.findPromptList(params);
            return Result.success(promptList);
        } else {
            Page<Map> page = new Page(Long.valueOf(ValidU.isEmpty(params.get("current")) ? "1" : params.get("current").toString()), 20L);
            IPage<Map<String, Object>> list = chatMapper.findPromptList(page, params);
            return Result.success(list);
        }
    }

    private static void removeIrrelevantChar(CompletionResponse completions, String userId) {
        if(ValidU.isEmpty(completions.getChoices())) {
            return;
        }
        Choice choice = completions.getChoices()[0];
        String msg = choice.getText();
        if(msg == null) {
            msg = "";
        }
        msg = msg.replaceAll("openai:", "")
                .replaceAll("openai：", "")
                .replaceAll("OpenAi:", "")
                .replaceAll("OpenAi：", "")
                .replaceAll("OpenAI：", "")
                .replaceAll("OpenAI:", "")
                .replaceAll(userId + ":", "")
                .replaceAll(userId + "：", "")
                .replaceAll("^\\n|\\n$", "");
        choice.setText(msg);
    }
}
