package cn.aezo.chat_gpt.util;

import cn.aezo.chat_gpt.config.AppConfig;
import cn.aezo.chat_gpt.config.BizException;
import cn.hutool.json.JSONUtil;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20190711.SmsClient;
import com.tencentcloudapi.sms.v20190711.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20190711.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20190711.models.SendStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SmsU {
    private static volatile SmsU INSTANCE;
    private static final Pattern PATTERN = Pattern.compile("^((13[0-9])|(15[^4,\\D])|(18[0-9]))\\d{8}$");

    private IAcsClient clientAliyun = null;
    private static final String REGION_ID = "cn-hangzhou";
    private static final String SYS_DOMAIN = "dysmsapi.aliyuncs.com";
    private static final String SYS_VERSION = "2017-05-25";
    private static final String SYS_ACTION = "SendSms";

    private SmsClient clientTencent = null;

    private SmsU() {
    }

    public static SmsU getInstance() {
        if (INSTANCE == null) {
            synchronized (SmsU.class) {
                if(INSTANCE == null) {
                    INSTANCE = new SmsU();
                    Environment environment = SpringU.getBean(Environment.class);
                    String active = environment.getProperty("aezo-app-common.sms.active");
                    // 阿里云
                    if("aliyun".equals(active)) {
                        String accessKeyId = environment.getProperty("aezo-app-common.sms.aliyun.access-key-id");
                        String accessKeySecret = environment.getProperty("aezo-app-common.sms.aliyun.access-key-secret");
                        DefaultProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
                        INSTANCE.clientAliyun = new DefaultAcsClient(profile);
                    } else if("tencent".equals(active)) {
                        String accessKeyId = environment.getProperty("aezo-app-common.sms.tencent.access-key-id");
                        String accessKeySecret = environment.getProperty("aezo-app-common.sms.tencent.access-key-secret");
                        Credential cred = new Credential(accessKeyId, accessKeySecret);
                        HttpProfile httpProfile = new HttpProfile();
                        httpProfile.setReqMethod("POST");
                        ClientProfile clientProfile = new ClientProfile();
                        clientProfile.setSignMethod("HmacSHA256");
                        clientProfile.setHttpProfile(httpProfile);
                        INSTANCE.clientTencent = new SmsClient(cred, "ap-guangzhou", clientProfile);
                    }
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 发送短信
     * @param phoneNumber 手机号码
     * @param randomCode 验证码
     * @return code
     */
    public static String sendMessage(String phoneNumber, String randomCode) {
        Environment environment = SpringU.getBean(Environment.class);
        String active = environment.getProperty("aezo-app-common.sms.active");
        if("aliyun".equals(active)) {
            String signName = environment.getProperty("aezo-app-common.sms.aliyun.sign-name");
            String templateCode = environment.getProperty("aezo-app-common.sms.aliyun.template-code");
            String templateParam = environment.getProperty("aezo-app-common.sms.aliyun.template-param");
            sendMessage(phoneNumber, randomCode, signName, templateCode, templateParam);
        } else if("tencent".equals(active)) {
            String appId = environment.getProperty("aezo-app-common.sms.tencent.app-id");
            String signName = environment.getProperty("aezo-app-common.sms.tencent.sign-name");
            String templateCode = environment.getProperty("aezo-app-common.sms.tencent.template-code");
            String phoneNumberTemp = "+86" + phoneNumber;
            Map<String, Object> sendStatusMap = sendMessage(appId, signName, templateCode,
                    new String[]{randomCode, String.valueOf(AppConfig.IdentifyingCodeCacheMinutes)}, new String[]{phoneNumberTemp});
            if(!"Ok".equals(sendStatusMap.get(phoneNumberTemp))) {
                log.error("发送短信失败 {} {}", phoneNumber, sendStatusMap.get(phoneNumberTemp));
                throw new BizException("发送短信失败");
            }
        } else {
            throw new RuntimeException("未知状态");
        }
        return randomCode;
    }

    /**
     * 发送短信
     * @param phoneNumber 手机号码
     * @param randomCode 验证码
     * @param signName 短信签名
     * @param templateCode 模板CODE
     * @param templateParam 模板中的变量名称，例如模板中为${code}，此处就填写code
     * @return code
     */
    public static String sendMessage(String phoneNumber, String randomCode, String signName, String templateCode, String templateParam) {
        if (!isPhoneNumber(phoneNumber)) {
            throw new BizException("手机号格式不正确");
        }
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain(SYS_DOMAIN);
        request.setSysVersion(SYS_VERSION);
        request.setSysAction(SYS_ACTION);
        request.putQueryParameter("RegionId", REGION_ID);
        request.putQueryParameter("PhoneNumbers", phoneNumber);
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", templateCode);
        request.putQueryParameter("TemplateParam", "{\"" + templateParam + "\":\"" + randomCode + "\"}");

        // 发送的业务逻辑
        try {
            // {"Message":"账户余额不足","RequestId":"415E9BF0-6904-5920-933A-F27B920E2644","Code":"isv.AMOUNT_NOT_ENOUGH"}
            // {"Message":"触发分钟级流控Permits:1","RequestId":"9D7F3798-1CA1-5110-9056-1ECB14A91097","Code":"isv.BUSINESS_LIMIT_CONTROL"}
            // {"Message":"OK","RequestId":"2E6BD8A4-D1A3-5A25-A237-2AB702893632","Code":"OK","BizId":"303700784907161870^0"}
            CommonResponse response = getInstance().clientAliyun.getCommonResponse(request);
            if(response.getHttpStatus() != 200 || !"OK".equals(JSONUtil.parseObj(response.getData()).get("Message"))) {
                log.error("发送短信失败 {} {} {}", phoneNumber, response.getHttpStatus(), response.getData());
                throw new BizException("发送短信失败");
            }
            log.info("发送短信成功 {} {}", phoneNumber, randomCode);
        } catch (Exception e) {
            log.error("发送短信出错 {}", phoneNumber, e);
            throw new BizException("发送短信出错", e);
        }
        return randomCode;
    }

    /**
     * 方法描述 腾讯api 发送短信
     * @params [sdkAppId, signName, templateId, templateParamSet, phoneNumberSet] 应用, 短信签名内容，模板id，手机号，模板参数
     */
    public static Map<String, Object> sendMessage(String sdkAppId, String signName, String templateId, String[] templateParamSet, String[] phoneNumberSet) {
        try {
            SendSmsRequest req = new SendSmsRequest();
            req.setSmsSdkAppid(sdkAppId);
            req.setSign(signName);
            req.setTemplateID(templateId);
            req.setTemplateParamSet(templateParamSet);
            /**
             * 下发手机号码，采用 E.164 标准，+[国家或地区码][手机号]
             * 示例如：+8613711112222，其中前面有一个+号，86为国家码，15812345678为手机号，最多不要超过200个手机号
             */
            if (ValidU.isNotEmpty(phoneNumberSet)) {
                for (String item : phoneNumberSet) {
                    if(item.length() != 11) {
                        item = item.substring(3);
                    }
                    if(!isPhoneNumber(item)) {
                        throw new BizException("手机号格式不正确");
                    }
                }
            }
            req.setPhoneNumberSet(phoneNumberSet);
            // {"SendStatusSet":[{"SerialNo":"","PhoneNumber":"+8615812345678","Fee":0,"SessionContext":"","Code":"InvalidParameterValue.TemplateParameterFormatError","Message":"Verification code template parameter format error","IsoCode":""}],"RequestId":"45e90d93-4acf-41ff-b43d-52a8abd580a3"}
            // {"SendStatusSet":[{"SerialNo":"3369:244158770816849200408125577","PhoneNumber":"+8615812345678","Fee":1,"SessionContext":"","Code":"Ok","Message":"send success","IsoCode":"CN"}],"RequestId":"8cf8ff84-ad0e-4d15-b44d-dfbe3ac6e86f"}
            SendSmsResponse res = getInstance().clientTencent.SendSms(req);
            SendStatus[] sendStatusSet = res.getSendStatusSet();
            Map<String, Object> resMap = new HashMap<>();
            for (SendStatus sendStatus : sendStatusSet) {
                resMap.put(sendStatus.getPhoneNumber(), sendStatus.getCode());
            }
            return resMap;
        } catch (Exception e) {
            log.error("发送短信出错 {}", phoneNumberSet, e);
            throw new BizException("发送短信出错", e);
        }
    }

    public static boolean isPhoneNumber(String phoneNumber) {
        Matcher m = PATTERN.matcher(phoneNumber);
        return m.matches();
    }
}
