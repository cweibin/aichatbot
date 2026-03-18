package cn.aezo.chat_gpt.config;

import java.util.HashMap;
import java.util.Map;

public class ThreadLocalContext {
    public static final String Permissions = "Permissions";

    private static final ThreadLocal<Map<String, Object>> THREAD_LOCAL = new ThreadLocal<>();

    public static void putContext(String key, Object value) {
        getContextAll().put(key, value);
    }

    public static Object getContext(String key) {
        return getContextAll().get(key);
    }

    public static Map<String, Object> getContextAll() {
        Map<String, Object> info = THREAD_LOCAL.get();
        if(info == null) {
            info = new HashMap<>();
            setContext(info);
        }
        return info;
    }

    public static void removerContext() {
        THREAD_LOCAL.remove();
    }

    private static void setContext(Map<String, Object> ctx) {
        THREAD_LOCAL.set(ctx);
    }
}
