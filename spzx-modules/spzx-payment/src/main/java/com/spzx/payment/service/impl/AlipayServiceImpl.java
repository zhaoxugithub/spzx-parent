package com.spzx.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class AlipayServiceImpl implements IAlipayService {

    @Autowired
    AlipayClient alipayClient;

    @Autowired
    IPaymentInfoService paymentInfoService;

    /**
     * 调用支付宝支付接口：
     *      1.先保存支付信息
     *      2.调用支付宝支付接口
     * @param orderNo
     * @return
     */
    @SneakyThrows
    @Override
    public String submitAlipay(String orderNo) {
        //1.先保存支付信息
        PaymentInfo paymentInfo = paymentInfoService.savePayment(orderNo);


        //2.调用支付宝支付接口
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setReturnUrl(AlipayConfig.return_payment_url); //同步地址，用于通知用户付款成功
        request.setNotifyUrl(AlipayConfig.notify_payment_url); //异步地址,用于通知商家后端付款成功。后端进行额外业务处理：例如：修改订单状态、减库存

        // 声明一个map 集合
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",paymentInfo.getOrderNo());
        map.put("product_code","QUICK_WAP_WAY");
        //map.put("total_amount",orderInfo.getTotalAmount());
        map.put("total_amount","0.01"); //注意 注意 注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意注意
        map.put("subject",paymentInfo.getContent());
        //map.put("time_expire","2024-11-12 10:05:00"); //设置订单终止时间。

        // 设置订单绝对超时时间,关单阻止用户继续支付。
        //model.setTimeExpire("2016-12-31 10:05:00");

        request.setBizContent(JSON.toJSONString(map)); //业务内容

        return alipayClient.pageExecute(request).getBody(); //默认post请求
    }
}
