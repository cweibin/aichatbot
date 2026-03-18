package cn.aezo.chat_gpt.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.bean.WxMaKefuMessage;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import cn.binarywang.wx.miniapp.message.WxMaMessageHandler;
import cn.binarywang.wx.miniapp.message.WxMaMessageRouter;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.common.error.WxRuntimeException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceHttpClientImpl;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.api.impl.WxMpServiceJoddHttpImpl;
import me.chanjar.weixin.mp.api.impl.WxMpServiceOkHttpImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties(SqThirdAppProp.class)
public class SqThirdAppConfig {
    private final SqThirdAppProp properties;

    private static final Map<String, WxMaMessageRouter> routers = Maps.newHashMap();
    private static Map<String, WxMaService> maServices;
    private static Map<String, WxMpService> mpServices;
    private static Map<String, SqThirdAppProp.Config> thirdAppConfigMap;

    @Autowired
    public SqThirdAppConfig(SqThirdAppProp properties) {
        this.properties = properties;
        thirdAppConfigMap = CollUtil.fieldValueMap(properties.getConfigs(), "sqAppCode");
    }

    public static SqThirdAppProp.Config getThirdAppConfig(String sqAppCode) {
        return thirdAppConfigMap.get(sqAppCode);
    }

    public static WxMaService getMaService(String appid) {
        WxMaService wxService = maServices.get(appid);
        if (wxService == null) {
            throw new IllegalArgumentException(String.format("未找到对应appid=[%s]的配置，请核实！", appid));
        }

        return wxService;
    }

    public static WxMpService getMpService(String appid) {
        WxMpService wxService = mpServices.get(appid);
        if (wxService == null) {
            throw new IllegalArgumentException(String.format("未找到对应appid=[%s]的配置，请核实！", appid));
        }

        return wxService;
    }

    public static WxMaMessageRouter getRouter(String appid) {
        return routers.get(appid);
    }

    @PostConstruct
    public void init() {
        List<SqThirdAppProp.Config> configs = this.properties.getConfigs();
        if (configs == null) {
            throw new WxRuntimeException("需添加aezo-chat-gpt.thirdapp.configs[]相关配置");
        }

        maServices = configs.stream().filter(x -> "WxMa".equals(x.getSqAppType()))
                .map(x -> {
                    WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
                    // 使用下面的配置时，需要同时引入jedis-lock的依赖，否则会报类无法找到的异常
                    // WxMaDefaultConfigImpl config = new WxMaRedisConfigImpl(new JedisPool());
                    config.setAppid(x.getAppid());
                    config.setSecret(x.getSecret());
                    config.setToken(x.getToken());
                    config.setAesKey(x.getAesKey());
                    config.setMsgDataFormat(x.getMsgDataFormat());

                    WxMaService service = new WxMaServiceImpl();
                    service.setWxMaConfig(config);
                    routers.put(x.getAppid(), this.newRouter(service));
                    return service;
                }).collect(Collectors.toMap(s -> s.getWxMaConfig().getAppid(), a -> a));

        mpServices = configs.stream().filter(x -> "WxMp".equals(x.getSqAppType()))
                .map(x -> {
                    WxMpService wxMpService;
                    switch (x.getConfigStorage().getHttpClientType()) {
                        case "OkHttp":
                            wxMpService = new WxMpServiceOkHttpImpl();
                            break;
                        case "JoddHttp":
                            wxMpService = new WxMpServiceJoddHttpImpl();
                            break;
                        case "HttpClient":
                            wxMpService = new WxMpServiceHttpClientImpl();
                            break;
                        default:
                            wxMpService = new WxMpServiceImpl();
                            break;
                    }

                    WxMpDefaultConfigImpl wxMpDefaultConfig = new WxMpDefaultConfigImpl();
                    wxMpDefaultConfig.setAppId(x.getAppid());
                    BeanUtil.copyProperties(x, wxMpDefaultConfig);
                    wxMpService.setWxMpConfigStorage(wxMpDefaultConfig);
                    return wxMpService;
                }).collect(Collectors.toMap(s -> s.getWxMpConfigStorage().getAppId(), a -> a));
    }

    private WxMaMessageRouter newRouter(WxMaService service) {
        final WxMaMessageRouter router = new WxMaMessageRouter(service);
        router
                .rule().handler(logHandler).next()
                .rule().async(false).content("订阅消息").handler(subscribeMsgHandler).end()
                .rule().async(false).content("文本").handler(textHandler).end()
                .rule().async(false).content("图片").handler(picHandler).end()
                .rule().async(false).content("二维码").handler(qrcodeHandler).end();
        return router;
    }

    private final WxMaMessageHandler subscribeMsgHandler = (wxMessage, context, service, sessionManager) -> {
        service.getMsgService().sendSubscribeMsg(WxMaSubscribeMessage.builder()
                .templateId("此处更换为自己的模板id")
                .data(Lists.newArrayList(
                        new WxMaSubscribeMessage.MsgData("keyword1", "339208499")))
                .toUser(wxMessage.getFromUser())
                .build());
        return null;
    };

    private final WxMaMessageHandler logHandler = (wxMessage, context, service, sessionManager) -> {
        log.info("收到消息：" + wxMessage.toString());
        service.getMsgService().sendKefuMsg(WxMaKefuMessage.newTextBuilder().content("收到信息为：" + wxMessage.toJson())
                .toUser(wxMessage.getFromUser()).build());
        return null;
    };

    private final WxMaMessageHandler textHandler = (wxMessage, context, service, sessionManager) -> {
        service.getMsgService().sendKefuMsg(WxMaKefuMessage.newTextBuilder().content("回复文本消息")
                .toUser(wxMessage.getFromUser()).build());
        return null;
    };

    private final WxMaMessageHandler picHandler = (wxMessage, context, service, sessionManager) -> {
        try {
            WxMediaUploadResult uploadResult = service.getMediaService()
                    .uploadMedia("image", "png",
                            ClassLoader.getSystemResourceAsStream("tmp.png"));
            service.getMsgService().sendKefuMsg(
                    WxMaKefuMessage
                            .newImageBuilder()
                            .mediaId(uploadResult.getMediaId())
                            .toUser(wxMessage.getFromUser())
                            .build());
        } catch (WxErrorException e) {
            e.printStackTrace();
        }

        return null;
    };

    private final WxMaMessageHandler qrcodeHandler = (wxMessage, context, service, sessionManager) -> {
        try {
            final File file = service.getQrcodeService().createQrcode("123", 430);
            WxMediaUploadResult uploadResult = service.getMediaService().uploadMedia("image", file);
            service.getMsgService().sendKefuMsg(
                    WxMaKefuMessage
                            .newImageBuilder()
                            .mediaId(uploadResult.getMediaId())
                            .toUser(wxMessage.getFromUser())
                            .build());
        } catch (WxErrorException e) {
            e.printStackTrace();
        }

        return null;
    };

}
