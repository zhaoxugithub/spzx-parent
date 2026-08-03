package com.spzx.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.payment.domain.PaymentInfo;

import java.util.Map;

/**
 * 付款信息Service接口
 */
public interface IPaymentInfoService extends IService<PaymentInfo> {

    /**
     * 保存支付信息
     * @param orderNo
     * @return 支付信息
     */
    PaymentInfo savePayment(String orderNo);

    /**
     * 支付成功，更新支付信息
     * @param paramMap 支付宝异步通知返回数据
     * @param payType 支付类型：付款方式：1-微信 2-支付宝
     */
    void updatePaymentStatus(Map<String, String> paramMap, int payType);
}