package cn.aezo.chat_gpt.util;

import cn.aezo.chat_gpt.config.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import toolgood.words.StringSearch;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SensitiveU {
    public static StringSearch commonStringSearch = null;
    public static Map<String, StringSearch> platformStringSearchMap = new HashMap<>();

    @Autowired
    public JdbcTemplate jdbcTemplate;

    @Value("${aezo-app-common.sensitive-toolgood-enable:false}")
    private String sensitiveToolgoodEnable;

    @PostConstruct
    public void init() {
        if("false".equals(sensitiveToolgoodEnable)) {
            return;
        }
        commonStringSearch = new StringSearch();
        List<Map<String, Object>> list = jdbcTemplate.queryForList("select sensi_type, sensi_words from sys_sensi_words " +
                " where valid_status = 1");
        Map<String, List> sensiTypeMap = MiscU.groupByMapKey(list, "sensi_type");
        for (Map.Entry<String, List> item : sensiTypeMap.entrySet()) {
            List<Map<String, Object>> itemList = item.getValue();
            List<String> sensiWords = itemList.stream().map(x -> (String) x.get("sensi_words")).collect(Collectors.toList());
            if("*".equals(item.getKey()) && ValidU.isNotEmpty(sensiWords)) {
                commonStringSearch.SetKeywords(sensiWords);
            } else {
                StringSearch platformStringSearch = new StringSearch();
                platformStringSearch.SetKeywords(sensiWords);
                platformStringSearchMap.put(item.getKey(), platformStringSearch);
            }
        }
    }

    public static boolean isSensitive(String msg) {
        return isSensitive(msg, null, null);
    }

    public static boolean isSensitive(String msg, String platform, String openid) {
        String sensitiveToolgoodEnable = SpringU.getEnv("aezo-app-common.sensitive-toolgood-enable");
        if("false".equals(sensitiveToolgoodEnable)) {
            // do nothing...
        } else {
            // true auto
            boolean sens;
            StringSearch stringSearch = platformStringSearchMap.get(platform);
            if(stringSearch != null) {
                sens = stringSearch.ContainsAny(msg);
                if(sens) {
                    return true;
                }
            }
            if("true".equals(sensitiveToolgoodEnable)
                || "auto".equals(sensitiveToolgoodEnable) && !"WxMa".equals(platform)) {
                sens = commonStringSearch.ContainsAny(msg);
                if(sens) {
                    return true;
                }
            }
        }
        if("WxMa".equals(platform) && getSensitiveWxEnable()) {
            try {
                WxU.checkCommentAuto(msg, openid);
            } catch (BizException e) {
                return true;
            }
        }
        return false;
    }

    public static String replaceSensitive(String msg) {
        if(isSensitive(msg)) {
            return commonStringSearch.Replace(msg);
        }
        return msg;
    }

    public static boolean getSensitiveWxEnable() {
        return SpringU.getEnvFlag("aezo-app-common.sensitive-wx-enable");
    }
}
