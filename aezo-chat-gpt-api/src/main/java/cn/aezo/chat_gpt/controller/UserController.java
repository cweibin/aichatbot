package cn.aezo.chat_gpt.controller;

import cn.aezo.chat_gpt.config.AppConfig;
import cn.aezo.chat_gpt.service.UserService;
import cn.aezo.chat_gpt.util.MiscU;
import cn.aezo.chat_gpt.util.Result;
import cn.aezo.chat_gpt.util.ValidU;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/core/user")
public class UserController {
    @Autowired
    private CommonController commonController;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result login(@RequestParam(value = "loginType", required = false) String loginType,
                        @RequestBody Map<String, Object> params) {
        String userId = null;
        String openid = null;
        if("WxMa".equals(loginType)) {
            Result<Map> result = userService.loginByWxMa(params);
            if(Result.isFailure(result)) {
                return result;
            }
            userId = (String) result.getData().get("userId");
            openid = (String) result.getData().get("openid");
        } else if("WxMp".equals(loginType)) {
            Result<Map> result = userService.loginByWxMp(params);
            if(Result.isFailure(result)) {
                return result;
            }
            userId = (String) result.getData().get("userId");
            openid = (String) result.getData().get("openid");
        } else {
            String username = (String) params.get("username");
            String password = (String) params.get("password");
            if(Boolean.TRUE.equals(ValidU.haveEmptyOne(MiscU.Instance.toList(username, password)))) {
                return Result.failure("请输入用户名和密码");
            }
            userId = userService.checkAndGetUserId(username, password);
        }
        if(ValidU.isEmpty(userId)) {
            return Result.failure("登录失败");
        }
        StpUtil.login(userId);

        Map<String, Object> userInfo = userService.getUserInfo(userId);
        StpUtil.getSession().set("userId", userInfo.get("id"));
        StpUtil.getSession().set("username", userInfo.get("username"));
        StpUtil.getSession().set("name", userInfo.get("nick_name"));
        StpUtil.getSession().set("userLevel", userInfo.get("user_level"));
        if(openid != null) {
            StpUtil.getSession().set("openid", openid);
        }

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.success(MiscU.Instance.toMap("tokenInfo", tokenInfo, "openid", openid));
    }

    @RequestMapping("/info")
    public Result getUserInfo() {
        Object loginId = StpUtil.getLoginId();
        Map<String, Object> userInfo = userService.getUserInfo(loginId);
        List<String> roles = MiscU.Instance.toList((String) userInfo.get("user_level"));
        return Result.success(MiscU.Instance.toMap("userInfo", userInfo, "roles", roles));
    }

    @RequestMapping("/register/sendIdentifyingCode")
    public Result sendIdentifyingCode(@RequestBody Map<String, Object> params) {
        String email = (String) params.get("email");
        String mobile = (String) params.get("mobile");
        String username = email;
        if("SMS".equals(params.get("sendMethod"))) {
            username = mobile;
        }
        if (ValidU.isEmpty(username)) {
            return Result.failure("缺少必须参数");
        }
        List<Map<String, Object>> list = userService.getUserByUsername(username);
        if(ValidU.isNotEmpty(list)) {
            return Result.failure("已存在此用户，可直接登录", "auth.register.user_exists");
        }
        params.put("sendType", "register");
        params.put("sendTypeTitle", "注册");
        return commonController.sendIdentifyingCode(username, params);
    }

    @PostMapping("/registerOrBind")
    public Result registerOrBind(@RequestBody Map<String, Object> params) {
        // 为电话/邮箱
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        String identifyingCode = (String) params.get("identifyingCode");
        if (!ValidU.isAllNotEmpty(username, password, identifyingCode)) {
            return Result.failure("缺少必须参数");
        }
        String cacheKey = "register-" + username;
        String identifyingCodeTrue = AppConfig.IdentifyingCodeCache.get(cacheKey);
        if(ValidU.isEmpty(identifyingCodeTrue)) {
            return Result.failure("验证码已过期", "auth.register.code_invalid");
        }
        if(!identifyingCodeTrue.equals(identifyingCode)) {
            return Result.failure("验证码不正确");
        }
        Result result;
        if(StpUtil.isLogin()) {
            result = userService.bind(username, password);
        } else {
            String inviterUserId = (String) params.get("inviterUserId");
            Map<String, Object> registerMap = userService.register(username, password, inviterUserId);
            result = Result.success(registerMap);
        }
        AppConfig.IdentifyingCodeCache.remove(cacheKey);
        return result;
    }
}
