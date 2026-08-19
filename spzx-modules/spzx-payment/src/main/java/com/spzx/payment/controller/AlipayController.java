package com.spzx.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.security.annotation.RequiresLogin;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/alipay")
public class AlipayController extends BaseController {

    @Autowired
    private IAlipayService alipayService;

    @Autowired
    private IPaymentInfoService paymentInfoService;


    /**
     <form name="punchout_form" method="post"
     action="https://openapi.alipay.com/gateway.do?charset=utf-8&method=alipay.trade.wap.pay&sign=lK5j63dso84gMFUS2JXiDhSlMrlJ9j2XtU98pHmwA%2BJojH38dSmIOAuHvlp3NfZtaLe7wZ8IxhQzpIxxPMoGVQ7xhkhmy8%2BR2EGiJ0PE3eo0ExYhBvbU%2FFO2lV%2FBiovJyLmeM8R7qQnvmlhDv7tmtcl%2BcW%2Bky7Xl%2BjzUX0rvUZb0%2B0oHcllIjOYK1f9dxZjko5ybD5HAHfoVSQSQtnzM%2FN4n7a1GAAwk3DrHStBUZbIt%2FXBDDPKOjg6ay0XOJ9dM9mSxJQ8ThTwEQJf03qiVWd0W6WJC8wO9Q6eVQPVeYrwH0e8WLpJth0zzrhSKj22dS54iuHh9nPHK8sOvBM977A%3D%3D&return_url=http%3A%2F%2Fry-spzx.atguigu.cn%2F%23%2Fpages%2Fmoney%2FpaySuccess&notify_url=http%3A%2F%2Fspzx0717.free.idcfengye.com%2Fpayment%2Falipay%2Fcallback%2Fnotify&version=1.0&app_id=2021001163617452&sign_type=RSA2&timestamp=2024-11-12+15%3A41%3A25&alipay_sdk=alipay-sdk-java-4.8.73.ALL&format=json">
     <input type="hidden" name="biz_content" value="{&quot;out_trade_no&quot;:&quot;e3f8542210154321b5855c04d4f45613&quot;,&quot;total_amount&quot;:&quot;0.01&quot;,&quot;subject&quot;:&quot;小米 红米Note10 5G手机 白色 + 8G &quot;,&quot;product_code&quot;:&quot;QUICK_WAP_WAY&quot;}">
     <input type="submit" value="立即支付" style="display:none" >
     </form>
     <script>document.forms[0].submit();</script>
     */
    @Operation(summary = "支付宝下单")
    @RequiresLogin
    @RequestMapping("submitAlipay/{orderNo}")
    @ResponseBody
    public AjaxResult submitAlipay(@PathVariable(value = "orderNo") String orderNo) {
        //支付宝支付接口，返回支付表单，通过表单打开用户手机上的支付宝，弹出密码框，输入密码确认支付。
        String h5Form = alipayService.submitAlipay(orderNo);
        log.info(h5Form);
        return success(h5Form);
    }



    /**
     * 支付宝同步回调   用于通知用户付款成功
     * @return
     */
//    @Operation(summary = "支付宝同步回调")
//    @GetMapping("callback/return")
//    //@RequiresLogin
//    public String callBack() {
//        log.info("AlipayController...callBack方法执行了...");
//        // 同步回调给用户展示信息
//        //    http://ry-spzx.atguigu.cn/#/pages/money/paySuccess
//        return "redirect:/" + AlipayConfig.return_order_url;
//    }


    /**
     * 支付宝异步回调   用于通知商家平台付款成功
     *  1. 在进行异步通知交互时，如果支付宝收到的应答不是 success ，支付宝会认为通知失败，会通过一定的策略定期重新发起通知。
     *  通知的间隔频率为：4m、10m、10m、1h、2h、6h、15h。
     * 2. 商家设置的异步地址（notify_url）需保证无任何字符，如空格、HTML 标签，且不能重定向。
     * （如果重定向，支付宝会收不到 success 字符，会被支付宝服务器判定为该页面程序运行出现异常，而重发处理结果通知）
     * @param paramMap  支付宝扣款后返回的数据
     * @param request
     * @return
     *
     *
    paramMap={gmt_create=2024-11-12 15:41:57, charset=utf-8,
    seller_email=zhuchangqing@atguigu.com, subject=小米 红米Note10 5G手机 白色   8G ,
    sign=f5lvEmtdZGTeahy5jG8cQaoYxNq70NK2i9PoUzlA9Loniw8c+hJcUJmrXi1io8XKEjG9m/jGJC2F6RQxkKwXn5zZITE1ckHMSMEyEq48VS9dv1xBoD3pS6BKw7diidyxUuxRQoC7W8Ceg6NGnM4rZDueE67buVfMfddINJVjRhBmLRydzxEie4FSSUBdfxMSphzgNjOAuB6YG83+K7W0Lx2v/iQv1Cn/aFpLxSr6p7F3g/RBjnxbeS73IN1rLvBeNvZGh28kdCdromfKb+2rm4UH8cDVJaUwD9/DTsYPRI36Zt/IBWN0Mel0T4ecMw9E+/agzL7m1YVtWfqakKuUUg==,
    buyer_id=2088502182688796, invoice_amount=0.01, notify_id=2024111201222154158088791455491437,
    fund_bill_list=[{"amount":"0.01","fundChannel":"ALIPAYACCOUNT"}], notify_type=trade_status_sync,

    trade_status=TRADE_SUCCESS,

    receipt_amount=0.01, buyer_pay_amount=0.01, app_id=2021001163617452,
    sign_type=RSA2, seller_id=2088831489324244, gmt_payment=2024-11-12 15:41:58, notify_time=2024-11-12 15:41:58,
    version=1.0,

    out_trade_no=e3f8542210154321b5855c04d4f45613,

    total_amount=0.01,

    trade_no=2024111222001488791435677669,
    auth_app_id=2021001163617452, buyer_logon_id=jls***@163.com, point_amount=0.00}
     */
    @RequestMapping("callback/notify")
    //@RequiresLogin
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap, HttpServletRequest request) {
        log.info("AlipayController...alipayNotify方法执行了...");
        log.info("paramMap="+paramMap);

        boolean signVerified = false; //调用SDK验证签名
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        if(signVerified){
            // 交易状态
            String trade_status = paramMap.get("trade_status");
            if("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){ //都代表付款成功
                // 正常的支付成功，我们应该更新交易记录状态
                paymentInfoService.updatePaymentStatus(paramMap, 2); //  支付类型：付款方式：1-微信 2-支付宝
                return "success" ; //商品平台收到异步通知后，执行业务逻辑后，需要给支付宝返回"success"字符串。表示支付流程结束。
            }else{
                // 签名通过但交易状态非成功状态，记录异常日志，返回 failure 让支付宝按策略重试
                log.error("[支付宝异步回调]签名验证通过,但交易状态异常, out_trade_no={}, trade_status={}",
                        paramMap.get("out_trade_no"), trade_status);
                return "failure";
            }
        }else{
            // 验签失败，记录异常日志（防止告警丢失），并返回 failure 通知支付宝
            log.error("[支付宝异步回调]验签失败, out_trade_no={}, 回调参数:{}",
                    paramMap.get("out_trade_no"), paramMap);
            return "failure"; //验签失败。
        }

    }






}