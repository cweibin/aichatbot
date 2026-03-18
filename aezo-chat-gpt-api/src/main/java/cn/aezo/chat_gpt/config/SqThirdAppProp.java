package cn.aezo.chat_gpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// @Configuration // SqWxConfig中会使用@EnableConfigurationProperties进行激活
@Data
@ConfigurationProperties(prefix = "aezo-chat-gpt.thirdapp")
public class SqThirdAppProp {

    private List<Config> configs;

    @Data
    public static class Config {
        /**
         * 第三方APP内部代码
         */
        private String sqAppCode;

        /**
         * 第三方APP类型
         */
        private String sqAppType;

        /**
         * 设置第三方APP(微信小程序)的appid
         */
        private String appid;

        /**
         * 设置第三方APP(微信小程序)的Secret
         */
        private String secret;

        /**
         * 设置第三方APP(微信小程序)消息服务器配置的token
         */
        private String token;

        /**
         * 设置第三方APP(微信小程序)消息服务器配置的EncodingAESKey
         */
        private String aesKey;

        /**
         * 消息格式，XML或者JSON
         */
        private String msgDataFormat;

        private ConfigStorage configStorage;
    }

    @Data
    public static class ConfigStorage {
        private String httpClientType;
    }

}
