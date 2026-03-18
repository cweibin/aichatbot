package cn.aezo.chat_gpt.controller;

import cn.aezo.chat_gpt.config.AppConfig;
import cn.aezo.chat_gpt.util.Result;
import cn.aezo.chat_gpt.util.SmsU;
import cn.aezo.chat_gpt.util.ValidU;
import cn.aezo.share.reporttable.core.service.ReportEmailService;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.PathParam;
import java.util.Map;

@RestController
@RequestMapping("/core/common")
public class CommonController {

    @Autowired(required = false)
    private ReportEmailService emailService;

    @GetMapping("/createVerifyCode/{id}")
    public void createVerifyCode(@PathVariable("id") String id, @PathParam("width") Integer width,
                                 @PathParam("height") Integer height, @PathParam("codeCount") Integer codeCount, HttpServletResponse response) {
        if(ValidU.isEmpty(id)) {
            return;
        }
        width = Convert.toInt(width, 96);
        height = Convert.toInt(height, 34);
        codeCount = Convert.toInt(codeCount, 4);
        if(width > 300) {
            width = 300;
        }
        if(height > 200) {
            height = 200;
        }
        if(codeCount > 10) {
            codeCount = 10;
        }
        String verifyCode = RandomUtil.randomStringWithoutStr(codeCount, "oO0lL1q9QpP");
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(width, height, codeCount, 20);
        captcha.verify(verifyCode);
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
            captcha.write(outputStream);
            AppConfig.IdentifyingCodeCache.put(id, verifyCode);
        } catch (Exception e) {
            IoUtil.close(outputStream);
        }
    }

    @RequestMapping("/sendIdentifyingCode")
    public Result sendIdentifyingCode(@RequestParam("id") String id, @RequestBody Map<String, Object> params) {
        String sendType = (String) params.get("sendType");
        String sendTypeTitle = StrUtil.emptyToDefault((String) params.get("sendTypeTitle"), "");
        String sendMethod = (String) params.get("sendMethod");
        if (ValidU.isEmpty(sendType) || ValidU.isEmpty(sendMethod) || ValidU.isEmpty(id)) {
            return Result.failure("缺少必须参数");
        }

        String key = sendType + "-";
        if("Email".equals(sendMethod)) {
            key = key + id;
            String identifyingCode = AppConfig.IdentifyingCodeCache.get(key);
            if(ValidU.isNotEmpty(identifyingCode)) {
                return Result.successMessage("已发送验证码");
            }
            identifyingCode = RandomUtil.randomString("0123456789", 6);
            String content = StrUtil.format("您的验证码是：{}（{} 分钟内有效）", identifyingCode, AppConfig.IdentifyingCodeCacheMinutes);
            if(emailService == null) {
                return Result.failure("邮件服务未配置");
            }
            boolean sendFlag = emailService.sendEmail(sendTypeTitle + "验证码", content, id);
            if (!sendFlag) {
                return Result.failure("验证码发送失败，请确认邮箱是否填写正确");
            }
            AppConfig.IdentifyingCodeCache.put(key, identifyingCode);
        } else if("SMS".equals(sendMethod)) {
            key = key + id;
            String identifyingCode = AppConfig.IdentifyingCodeCache.get(key);
            if(ValidU.isNotEmpty(identifyingCode)) {
                return Result.successMessage("已发送验证码");
            }
            identifyingCode = RandomUtil.randomString("0123456789", 6);
            SmsU.sendMessage(id, identifyingCode);
            AppConfig.IdentifyingCodeCache.put(key, identifyingCode);
        } else {
            return Result.failure("非法请求");
        }
        return Result.success();
    }

}
