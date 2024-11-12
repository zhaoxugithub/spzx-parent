package com.spzx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;

import java.util.List;

public interface IOrderInfoService extends IService<OrderInfo> {
    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单集合
     */
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo);

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    public OrderInfo selectOrderInfoById(Long id);
    
    
    
    
    
    










    //=======================================================================

    /**
     * 去结算
     * @return
     */
    TradeVo getOrderTrade();

    /**
     * 下单
     * @param orderForm 提交订单表单数据
     * @return 订单id
     */
    Long submitOrder(OrderForm orderForm);

    /**
     * 关闭订单
     * @param orderId
     */
    void processCloseOrder(Long orderId);

    /**
     * 根据订单号查询订单对象
     * @param orderNo
     * @return
     */
    OrderInfo getByOrderNo(String orderNo);

    /**
     * 支付成功，修改订单状态
     * @param orderNo
     */
    void processPaySucess(String orderNo);
}