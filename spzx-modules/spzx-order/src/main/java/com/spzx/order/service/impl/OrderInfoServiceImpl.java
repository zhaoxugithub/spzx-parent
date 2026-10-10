package com.spzx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.core.utils.bean.BeanUtils;
import com.spzx.common.core.utils.uuid.UUID;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.OrderLog;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.user.api.RemoteUserAddressService;
import com.spzx.user.domain.UserAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private RemoteCartService remoteCartService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RemoteUserAddressService remoteUserAddressService;


    @Autowired
    RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单
     */
    @Override
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo) {
        return orderInfoMapper.selectOrderInfoList(orderInfo);
    }

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    @Override
    public OrderInfo selectOrderInfoById(Long id) {
        OrderInfo orderInfo = orderInfoMapper.selectById(id);
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }


    //=======================================================


    @Override
    public TradeVo getOrderTrade() {
        //1.远程调用购物车接口，获取所有勾选的商品 List<CartInfo>
        Long userId = SecurityContextHolder.getUserId();
        R<List<CartInfo>> cartCheckedListResult = remoteCartService.getCartCheckedList(userId, SecurityConstants.INNER);
        if (cartCheckedListResult.getCode() == R.FAIL) {
            throw new ServiceException(cartCheckedListResult.getMsg());
        }
        List<CartInfo> cartInfoList = cartCheckedListResult.getData();
        if (CollectionUtils.isEmpty(cartInfoList)) {
            throw new ServiceException("购物车选中的商品为空");
        }


        //2.类型转换  List<CartInfo> -> List<OrderItem>
        List<OrderItem> orderItemList = cartInfoList.stream().map((cartInfo -> {
            OrderItem orderItem = new OrderItem();
            BeanUtils.copyProperties(cartInfo, orderItem);
            orderItem.setSkuPrice(cartInfo.getSkuPrice()); //skuPrice可能还是旧价格
            return orderItem;
        })).toList();

        //3.计算商品总金额
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItemList) {
            totalAmount = totalAmount.add(orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())));
        }

        //4.生成订单流水号。下单是用于去重。也是订单表的订单号。
        String tradeNo = generateTradeNo(userId);

        TradeVo tradeVo = new TradeVo();
        tradeVo.setTotalAmount(totalAmount);
        tradeVo.setOrderItemList(orderItemList);
        tradeVo.setTradeNo(tradeNo);
        return tradeVo;
    }

    //专业生产订单流水号
    private String generateTradeNo(Long userId) {
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        String key = "user:tradeNo:" + userId;
        redisTemplate.opsForValue().set(key, tradeNo, 5, TimeUnit.MINUTES);
        return tradeNo;
    }

    /**
     * 下订单业务逻辑：
     * 1.验证用户重复提交表单
     * 2.验证表单参数是否空
     * 3.校验价格变化
     * 4.校验库存锁定库存
     * 5.保存订单（订单表、订单项表、订单日志表）
     * 6.删除购物车选中商品
     */
    @Override
    public Long submitOrder(OrderForm orderForm) {
        Long userId = SecurityContextHolder.getUserId();
        // 下订单业务逻辑：
        // 1.验证用户重复提交表单
        /* 判断和删除不是原子性的。无法实现去重处理。
        String tradeNo = orderForm.getTradeNo(); //结算页面生成订单流水号，用于去重处理
        String tradeNoKey = "user:tradeNo:" + userId;
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey); //5分钟有效
        if(tradeNo!=null && !tradeNo.equals(redisTradeNo)){
            throw new ServiceException("重复提交");
        }
        redisTemplate.delete(tradeNoKey);*/

        //需要使用Lua脚本去重判断
        String tradeNo = orderForm.getTradeNo();
        String tradeNoKey = "user:tradeNo:" + userId;
        String script = "if redis.call('get',KEYS[1]) == ARGV[1] then\n" +
                "\treturn redis.call('del',KEYS[1])\n" +
                "else\n" +
                "\treturn 0\n" +
                "end";
        DefaultRedisScript redisScript = new DefaultRedisScript(script);
        redisScript.setResultType(Long.class);
        Long result = (Long) redisTemplate.execute(redisScript, Arrays.asList(tradeNoKey), tradeNo);
        if (result != 1) {
            throw new ServiceException("重复提交");
        }

        // 2.验证表单参数是否空
        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        if (CollectionUtils.isEmpty(orderItemList)) {
            throw new ServiceException("数据为空");
        }
        // 3.校验价格变化
        //批量获取商品最新价格
        List<Long> skuIdList = orderItemList.stream().map(orderItem -> orderItem.getSkuId()).toList();
        R<List<SkuPrice>> skuPriceListResult = remoteProductService.getSkuPriceList(skuIdList, SecurityConstants.INNER);
        if (skuPriceListResult.getCode() == R.FAIL) {
            throw new ServiceException(skuPriceListResult.getMsg());
        }
        List<SkuPrice> skuPriceList = skuPriceListResult.getData();
        Map<Long, BigDecimal> skuIdToSalePriceMap =
                skuPriceList.stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));

        StringBuilder builder = new StringBuilder("");
        for (OrderItem orderItem : orderItemList) {
            BigDecimal bigDecimalDB = skuIdToSalePriceMap.get(orderItem.getSkuId());
            if (orderItem.getSkuPrice().compareTo(bigDecimalDB) != 0) {
                builder.append("商品【" + orderItem.getSkuName() + "】价格有变动;");
            }
        }
        if (builder.toString().length() > 0) {
            //更新购物车价格
            remoteCartService.updateCartPrice(userId,SecurityConstants.INNER);
            throw new ServiceException(builder.toString());
        }

        // 4.校验库存并锁定库存（远程调用 spzx-product 的 checkAndLock）
        //    失败时返回非空字符串，提示具体哪个商品库存不足；成功时库存已锁定，等待支付后扣减或超时解锁
        List<SkuLockVo> skuLockVoList = orderItemList.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setSkuNum(item.getSkuNum());
            return skuLockVo;
        }).collect(Collectors.toList());
        String checkAndLockResult = remoteProductService.checkAndLock(orderForm.getTradeNo(), skuLockVoList, SecurityConstants.INNER).getData();
        if(StringUtils.isNotEmpty(checkAndLockResult)) {
            throw new ServiceException(checkAndLockResult);
        }

        // 5.保存订单（订单表、订单项表、订单日志表）
        Long orderId = null;
        try {
            orderId = this.saveOrder(orderForm);
        } catch (Exception e) {
            // 解锁库存 - 发消息异步解锁
            rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT,MqConst.ROUTING_UNLOCK,orderForm.getTradeNo());
            throw new RuntimeException(e);
        }

        // 6.删除购物车选中商品
        remoteCartService.deleteCartCheckedList(userId,SecurityConstants.INNER);

        //7.发送延迟消息,取消订单 (15分钟未支付，消费者就会进行关闭订单->解锁库存。)
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_CANCEL_ORDER,
                MqConst.ROUTING_CANCEL_ORDER,
                String.valueOf(orderId), MqConst.CANCEL_ORDER_DELAY_TIME);

        return orderId ;
    }

    //保存订单到三张表  order_info   order_item   order_log
    @Transactional
    protected Long saveOrder(OrderForm orderForm) {
        // 获取当前登录用户的id
        Long userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUserName();


        //1.保存订单数据
        OrderInfo orderInfo = new OrderInfo();

        orderInfo.setOrderNo(orderForm.getTradeNo());
        orderInfo.setUserId(userId);
        orderInfo.setNickName(userName);
        orderInfo.setRemark(orderForm.getRemark());
        UserAddress userAddress = remoteUserAddressService.getUserAddress(orderForm.getUserAddressId(), SecurityConstants.INNER).getData();
        orderInfo.setReceiverName(userAddress.getName());
        orderInfo.setReceiverPhone(userAddress.getPhone());
        orderInfo.setReceiverTagName(userAddress.getTagName());
        orderInfo.setReceiverProvince(userAddress.getProvinceCode());
        orderInfo.setReceiverCity(userAddress.getCityCode());
        orderInfo.setReceiverDistrict(userAddress.getDistrictCode());
        orderInfo.setReceiverAddress(userAddress.getFullAddress());

        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItemList) {
            totalAmount = totalAmount.add(orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())));
        }
        orderInfo.setTotalAmount(totalAmount); //实付总价格(含优惠卷和运费)
        orderInfo.setCouponAmount(new BigDecimal(0)); //优惠卷
        orderInfo.setOriginalTotalAmount(totalAmount); //商品总价格
        orderInfo.setFeightFee(orderForm.getFeightFee()); //运费
        //OrderInfo类的orderStatus属性的类型改为Integer
        orderInfo.setOrderStatus(0);

        orderInfoMapper.insert(orderInfo); //主键回填

        //2.保存订单项数据
        for (OrderItem orderItem : orderItemList) {

            orderItem.setOrderId(orderInfo.getId()); //外键

            orderItemMapper.insert(orderItem);
        }

        //3.保存订单日志数据
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setProcessStatus(0);
        orderLog.setNote("提交订单");
        orderLog.setOperateUser("用户");
        orderLogMapper.insert(orderLog);
        return orderInfo.getId();
    }





    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processCloseOrder(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(null != orderInfo && orderInfo.getOrderStatus().intValue() == 0) { //  订单状态orderStatus=0 说明15分钟未支付。
            orderInfo.setOrderStatus(-1); //  -1 取消订单
            orderInfo.setCancelTime(new Date());
            orderInfo.setCancelReason("未支付自动取消");
            orderInfoMapper.updateById(orderInfo);

            //记录日志
            OrderLog orderLog = new OrderLog();
            orderLog.setOrderId(orderInfo.getId());
            orderLog.setProcessStatus(-1);
            orderLog.setNote("系统取消订单");
            orderLogMapper.insert(orderLog);


            //发送MQ消息通知商品系统解锁库存
            rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT, MqConst.ROUTING_UNLOCK, orderInfo.getOrderNo());
        }
    }

    @Override
    public OrderInfo getByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderInfo.getId()));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    @Override
    public void processPaySucess(String orderNo) {
        //获取订单信息
        OrderInfo orderInfo = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getOrderNo, orderNo)
                .select(OrderInfo::getId, OrderInfo::getOrderStatus));
        //未支付
        if(orderInfo.getOrderStatus().intValue() == 0) {
            orderInfo.setOrderStatus(1); //已支付
            orderInfo.setPaymentTime(new Date()); //支付时间
            orderInfoMapper.updateById(orderInfo);
        }
    }
}