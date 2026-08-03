package com.spzx.payment.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.RemoteOrderInfoService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.mapper.PaymentInfoMapper;
import com.spzx.payment.service.IPaymentInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 * 付款信息Service业务层处理
 */
@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements IPaymentInfoService {
    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RemoteOrderInfoService remoteOrderInfoService;

    @Autowired
    private RabbitService rabbitService;


    @Override
    public PaymentInfo savePayment(String orderNo) {
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>().eq(PaymentInfo::getOrderNo, orderNo));
        if(null == paymentInfo) {
            R<OrderInfo> orderInfoResult = remoteOrderInfoService.getByOrderNo(orderNo, SecurityConstants.INNER);
            if (R.FAIL == orderInfoResult.getCode()) {
                throw new ServiceException(orderInfoResult.getMsg());
            }
            OrderInfo orderInfo = orderInfoResult.getData();

            paymentInfo = new PaymentInfo();
            paymentInfo.setUserId(orderInfo.getUserId());
            String content = "";
            for(OrderItem item : orderInfo.getOrderItemList()) {
                content += item.getSkuName() + " ";
            }
            paymentInfo.setContent(content);
            paymentInfo.setAmount(orderInfo.getTotalAmount());
            paymentInfo.setOrderNo(orderNo);
            paymentInfo.setPaymentStatus("0");
            paymentInfoMapper.insert(paymentInfo);
        }
        return paymentInfo;
    }


    @Override
    public void updatePaymentStatus(Map<String, String> paramMap, int payType) {
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>().eq(PaymentInfo::getOrderNo, paramMap.get("out_trade_no")));
        if ("1".equals(paymentInfo.getPaymentStatus())) {
            return;
        }

        //更新支付信息
        paymentInfo.setPayType(payType);
        paymentInfo.setPaymentStatus("1");
        paymentInfo.setTradeNo(paramMap.get("trade_no")); //支付宝与用户之间产生扣款的流水号
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setCallbackContent(JSON.toJSONString(paramMap));
        paymentInfoMapper.updateById(paymentInfo);

        //基于MQ通知订单系统，修改订单状态
        rabbitService.sendMessage(MqConst.EXCHANGE_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY, paymentInfo.getOrderNo());
    }
}