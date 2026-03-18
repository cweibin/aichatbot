package cn.aezo.chat_gpt.controller;

import cn.aezo.chat_gpt.service.OrderService;
import cn.aezo.chat_gpt.util.Result;
import cn.hutool.extra.servlet.ServletUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/tools/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @RequestMapping("/createOrder")
    public Result createOrder(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        params.put("clientip", ServletUtil.getClientIP(request, null));
        return orderService.createOrder(params);
    }

    /**
     * 通过第三方应用支付成功后，调用此接口，给用户开通资源
     * @author smalle
     * @since 2023/6/11
     * @param
     * @throws
     * @return cn.aezo.chat_gpt.util.Result
     */
    @RequestMapping("/ekeyPaidApiHook")
    public String ekeyPaidApiHook(@RequestParam Map<String, Object> params) {
        return orderService.completedOrderEkey(params);
    }
}
