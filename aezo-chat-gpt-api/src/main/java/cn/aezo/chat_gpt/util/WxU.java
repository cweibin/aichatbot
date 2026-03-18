package cn.aezo.chat_gpt.util;

import cn.aezo.chat_gpt.config.BizException;
import cn.aezo.chat_gpt.config.SqThirdAppConfig;
import cn.aezo.chat_gpt.config.SqThirdAppProp;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckRequest;
import cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckResponse;
import lombok.SneakyThrows;
import me.chanjar.weixin.common.error.WxErrorException;

public class WxU {
    public static void checkCommentAuto(String content, String openid) {
        SqThirdAppProp.Config thirdAppConfig = SqThirdAppConfig.getThirdAppConfig("aezo-chat-gpt");
        checkCommentAuto(thirdAppConfig.getAppid(), openid, content);
    }

    @SneakyThrows
    public static void checkCommentAuto(String appId, String openid, String content) {
        WxMaMsgSecCheckCheckResponse response = checkComment(appId, openid, content);
        if(!"pass".equals(response.getResult().getSuggest())) {
            throw new BizException("存在敏感字符");
        }
    }

    public static WxMaMsgSecCheckCheckResponse checkComment(String appId, String openid, String content) throws WxErrorException {
        WxMaMsgSecCheckCheckRequest request = WxMaMsgSecCheckCheckRequest.builder()
                .version("2")
                .openid(openid)
                .scene(2)
                .content(content)
                .build();
        final WxMaService wxService = SqThirdAppConfig.getMaService(appId);
        return wxService.getSecCheckService().checkMessage(request);
    }
}
