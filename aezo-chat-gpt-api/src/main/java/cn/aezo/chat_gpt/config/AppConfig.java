package cn.aezo.chat_gpt.config;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class AppConfig {
    /**
     * 缓存时效: 15分钟
     */
    public static final int IdentifyingCodeCacheMinutes = 15;
    public static final TimedCache<String, String> IdentifyingCodeCache = CacheUtil.newTimedCache(1000 * 60 * IdentifyingCodeCacheMinutes);

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Bean
    public Filter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Primary
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMinutes(1))
                .setReadTimeout(Duration.ofMinutes(1))
                .build();

        restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}
