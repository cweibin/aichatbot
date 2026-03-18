package cn.aezo.chat_gpt.service;

import cn.aezo.chat_gpt.config.SqPayProp;
import cn.aezo.chat_gpt.util.MiscU;
import cn.aezo.chat_gpt.util.Result;
import cn.aezo.chat_gpt.util.SpringU;
import cn.aezo.chat_gpt.util.ValidU;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqPayProp sqPayProp;

    @Autowired
    private ChatService chatService;

    public Result createOrder(Map<String, Object> params) {
        Object goodsId = params.get("goodsId");
        String goodsPriceInfo = SpringU.getEnv("aezo-app-common.pay.goodsPrice." + goodsId);
        if(ValidU.isEmpty(goodsPriceInfo)) {
            return Result.failure("暂未上线此商品");
        }
        String[] goodsPriceArr = goodsPriceInfo.split(",");
        Map<String, Object> orderMap;
        List<Map<String, Object>> orderList = jdbcTemplate.queryForList(
                "select o.order_sn, o.goods_id, o.title, o.actual_price, o.pay_type, upper(substr(ui.username, 4)) username, o.pay_invoke_type, o.buy_ip, o.other_ipu " +
                    " from orders o join mt_user_info ui on ui.id = o.buy_user_id" +
                    " where o.buy_user_id = ? and o.valid_status = 1 and o.status = 1 " +
                    " and o.goods_id = ? and o.create_time > date_add(now(), interval -? minute)",
                StpUtil.getLoginId(), goodsId, Convert.toInt(sqPayProp.getOrderTimeout(), 10));
        if(ValidU.isEmpty(orderList)) {
            String ekeyPayOrderNo = RandomUtil.randomString(16).toUpperCase();
            int count = jdbcTemplate.update("insert into orders(id, order_sn, goods_id, title, goods_price, total_price, actual_price, " +
                            " pay_invoke_type, pay_type, buy_user_id, buy_ip, other_ipu, creator, create_time) values(?,?,?,?,?,?,?,?,?,?,?,?,?,now())",
                    IdUtil.getSnowflakeNextIdStr(), ekeyPayOrderNo, goodsId, params.get("title"), goodsPriceArr[0], goodsPriceArr[0], goodsPriceArr[0],
                    sqPayProp.getPayInvokeType(), "alipay", StpUtil.getLoginId(), params.get("clientip"), goodsPriceArr[1], StpUtil.getLoginId());
            if(count <= 0) {
                return Result.failure("创建订单失败");
            }
            Object username = StpUtil.getSession().get("username");
            username = username.toString().substring(3).toUpperCase();
            orderMap = MiscU.Instance.toMap("order_sn", ekeyPayOrderNo, "title", params.get("title"),
                    "actual_price", new BigDecimal(goodsPriceArr[0]), "pay_type", "alipay", "username", username,
                    "buy_ip", params.get("clientip"));
        } else {
            orderMap = orderList.get(0);
        }
        orderMap.put("param", MiscU.Instance.toMapExt(true, "goods_type", goodsPriceArr[1] + "," + goodsPriceArr[2]));
        return createEkeyOrder(orderMap);
    }

    public Result createEkeyOrder(Map<String, Object> orderMap) {
        Map<String, Object> param = (Map<String, Object>) orderMap.get("param");
        //param.put("gid", orderMap.get("goods_id"));
        if("ekey".equals(sqPayProp.getPayInvokeType())) {
            // epay param参数不能太长
            param.put("gid", sqPayProp.getEkey().getGid());
            param.put("email", orderMap.get("username"));
            param.put("ekey_label_text_logo", sqPayProp.getEkey().getEkey_label_text_logo());
            param.put("ekey_label_email", sqPayProp.getEkey().getEkey_label_email());
            param.put("other_info", MiscU.Instance.toMapExt(true,
                    "search_pwd", sqPayProp.getEkey().getSearch_pwd(),
                    "manager_email", sqPayProp.getEkey().getManager_email()));
        }
        Map<String, Object> postMap = MiscU.Instance.toMapExt(true,
                "pid", sqPayProp.getEpay().getPid(),
                "type", orderMap.get("pay_type"),
                "out_trade_no", orderMap.get("order_sn"),
                "notify_url", sqPayProp.getEpay().getNotify_url(),
                "return_url", sqPayProp.getEpay().getReturn_url(),
                "name", orderMap.get("title"),
                "money", orderMap.get("actual_price").toString(),
                "clientip", orderMap.get("buy_ip"),
                "param", JSONUtil.toJsonStr(param));
        String[] signData = getSign(postMap, true);
        // 请求接口
        String url = sqPayProp.getEpay().getUrl();
        url = url + "?" + signData[0] + "&sign_type=MD5&sign=" + signData[1];
        // 调用下单接口: https://pay.tiuu.cn/mapi.php?clientip=0:0:0:0:0:0:0:1&money=0.1&name=%E4%BD%93%E9%AA%8C%E5%A5%97%E9%A4%90&notify_url=https://1x10405s48.goho.co/api/tools/order/ekeyPaidApiHook&out_trade_no=UAM0W6PQD9YRS3X9&param=%7B%22goods_type%22:%22NumBag,20%22%7D&pid=1749&return_url=http://localhost:8080/&type=alipay&sign_type=MD5&sign=3b1fa3664ce864d91d7ec2c89d78a14b 请求参数: {pid=1749, type=alipay, out_trade_no=UAM0W6PQD9YRS3X9, notify_url=https://1x10405s48.goho.co/api/tools/order/ekeyPaidApiHook, return_url=http://localhost:8080/, name=体验套餐, money=0.1, clientip=0:0:0:0:0:0:0:1, param={"goods_type":"NumBag,20"}}
        log.info("调用下单接口: {} 请求参数: {}", url, postMap);
        Map<String, Object> retMap = SpringU.getBean(RestTemplate.class).postForObject(url, null, Map.class);
        log.info("接口返回数据: {}", retMap);
        if(!"1".equals(retMap.get("code") + "")) {
            log.error("下单失败: {}", retMap);
            return Result.failure(StrUtil.emptyToDefault((String) retMap.get("msg"), "下单失败"));
        }
        return Result.success(MiscU.Instance.toMap("payMethod", "ekey", "payurl", retMap.get("payurl")));
    }

    public static String[] getSign(Map<String, Object> postMap, boolean yesEncode) {
        // 签名
        // 1、将发送或接收到的所有参数按照参数名ASCII码从小到大排序（a-z），sign、sign_type、和空值/空字符串不参与签名！
        // 2、将排序后的参数拼接成URL键值对的格式，例如 a=b&c=d&e=f，参数值不要进行url编码。
        // 3、再将拼接好的字符串与商户密钥KEY进行MD5加密得出sign签名参数，sign = md5 ( a=b&c=d&e=f + KEY ) （注意：+ 为各语言的拼接符，不是字符！），md5结果为小写。
        Map<String, Object> postMapTemp = BeanUtil.copyProperties(postMap, Map.class);
        TreeMap<String, Object> postMapSort = MapUtil.sort(MiscU.removeNullAndBlankStrValue(postMapTemp));
        // TODO epay商品名称乱码，此处应该只编码param参数
        String postMapStr = HttpUtil.toParams((Map<String, ?>) postMapSort, yesEncode ? StandardCharsets.UTF_8 : null);
        String key = SpringU.getBean(SqPayProp.class).getEpay().getKey();
        return new String[]{postMapStr, SecureUtil.md5(postMapStr + key)};
    }

    @Transactional(rollbackFor = Exception.class)
    public String completedOrderEkey(Map<String, Object> params) {
        // 弃用 {pid=OC_PROD, trade_no=GBVG5M6YCOVP2ZJQ, out_trade_no=NHNTE0CJNBQKC0IQ, type=alipay, name=体验套餐, money=1.99, trade_status=TRADE_SUCCESS, param={"gid":"7","ekey_label_email":"用户名","goods_type":"NumBag,20","email":"XG136QCU3Q","ekey_label_text_logo":"One能聊天"}, pay_trade_no=2023062022001415381436320041, sign=dd2f3b5ce2c7aeebb53b014f94734071, sign_type=MD5}
        // {money=0.1, name=product, out_trade_no=LF8R5I3PUD9Z6RP8, param=%7B%22goods_type%22:%22NumBag,20%22,%22gid%22:%227%22,%22email%22:%22HFXSEFKEPK%22,%22ekey_label_text_logo%22:%22One%E8%83%BD%E8%81%8A%E5%A4%A9%22,%22ekey_label_email%22:%22%E7%94%A8%E6%88%B7%E5%90%8D%22,%22other_info%22:%7B%22search_pwd%22:%22666%22,%22m, pid=1749, trade_no=2023080316051917141, trade_status=TRADE_SUCCESS, type=alipay, sign=cdd472c5cd2bc534c337c1005f520446, sign_type=MD5}
        log.info("收到易支付通知: {}", params);
        if(ValidU.isEmpty(params.get("out_trade_no"))) {
            return "缺少商户订单号";
        }
        if(ValidU.isEmpty(params.get("param"))) {
            return "缺少扩展参数";
        }
        String signParam = (String) params.get("sign");
        if(ValidU.isEmpty(signParam)) {
            return "缺少签名";
        }
        params.remove("sign");
        params.remove("sign_type");
        String[] sign = OrderService.getSign(params, false);
        if(!signParam.equals(sign[1])) {
            return "验签失败";
        }
        String outTradeNo = (String) params.get("out_trade_no");
        List<Map<String, Object>> orderList = jdbcTemplate.queryForList(
        "select o.order_sn, o.goods_id, o.status, o.buy_user_id " +
                " from orders o " +
                " where o.order_sn = ? and o.valid_status = 1 ", outTradeNo);
        if(ValidU.isEmpty(orderList)) {
            return "未知订单";
        }
        Map<String, Object> orderMap = orderList.get(0);
        if("4".equals(orderMap.get("status").toString())) {
            return "success";
        }
        if(!"TRADE_SUCCESS".equals(params.get("trade_status"))) {
            jdbcTemplate.update("update orders set status = 6, update_time=now() where order_sn = ?", outTradeNo);
            return "success";
        }
        // 支付成功
        jdbcTemplate.update("update orders set status = 4, trade_no=?, pay_trade_no=?, update_time=now() where order_sn = ?",
                params.get("trade_no"), params.get("pay_trade_no"), outTradeNo);
        String param = URLUtil.decode((String) params.get("param"), StandardCharsets.UTF_8);
        JSONObject jsonObject = JSONUtil.parseObj(param);
        String goodsType = jsonObject.get("goods_type").toString();
        String[] goodsInfoArr = goodsType.split(",");
        String assetType = "n";
        if("DayBag".equals(goodsInfoArr[0])) {
            assetType = "dfn";
        }
        chatService.checkAndUpdateAsset((String) orderMap.get("buy_user_id"), assetType,
                Integer.valueOf(goodsInfoArr[1]), "购买" + params.get("name"));
        return "success";
    }
}
