package cn.aezo.chat_gpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "aezo-app-common.pay")
@Configuration
public class SqPayProp {

    private String payInvokeType;
    private Integer orderTimeout;
    private Ekey ekey;
    private Epay epay;

    @Data
    public static class Ekey {
        private String gid;
        private String search_pwd;
        private String manager_email;
        private String ekey_label_text_logo;
        private String ekey_label_email;
    }

    @Data
    public static class Epay {
        private String url;
        private String pid;
        private String key;
        private String notify_url;
        private String return_url;
    }

}
