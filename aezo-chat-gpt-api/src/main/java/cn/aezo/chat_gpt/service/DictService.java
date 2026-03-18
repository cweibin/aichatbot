package cn.aezo.chat_gpt.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

@Service
public class DictService {
    public static String getUsername() {
        return (String) StpUtil.getSession().get("username");
    }

    public static String getName() {
        return (String) StpUtil.getSession().get("name");
    }

    public static String getUserId() {
        Object userId = StpUtil.getSession().get("userId");
        return userId != null ? userId.toString() : null;
    }

    public static String getSaasId() {
        Object partyId = getSessionSilence().get("partyId");
        return partyId != null ? partyId.toString() : null;
    }

    public static String getRoleCode() {
        return (String) getSessionSilence().get("roleCode");
    }

    public static SaSession getSessionSilence() {
        try {
            return StpUtil.getSession();
        } catch (NotLoginException e) {
            return new SaSession();
        }
    }

    public static String getUserLevel() {
        return (String) StpUtil.getSession().get("userLevel");
    }
}
