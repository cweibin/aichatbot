package cn.aezo.chat_gpt.service;

import cn.aezo.chat_gpt.util.Result;

import java.util.List;
import java.util.Map;

public interface UserService {
    String checkAndGetUserId(String username, String password);

    List<Map<String, Object>> getUserByUsername(String username);

    List<String> getPermissionList(Object userId);

    List<String> getRoleList(Object userId);

    Map<String, Object> getUserInfo(Object userId);

    Map<String, Object> register(String username, String password, String inviterUserId);

    Map<String, Object> registerByOpenid(String openType, String openid, String inviterUserId);

    Result loginByWxMa(Map<String, Object> params);

    Result loginByWxMp(Map<String, Object> params);

    String encPassword(String password);

    boolean checkPassword(String password, String passwordPure);

    Result bind(String username, String password);
}
