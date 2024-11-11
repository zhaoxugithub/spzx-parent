package com.spzx.common.rabbit.service;

import com.alibaba.fastjson2.JSON;
import com.spzx.common.core.utils.uuid.UUID;
import com.spzx.common.rabbit.entity.GuiguCorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RabbitService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发送消息
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息
     */
    public boolean sendMessage(String exchange, String routingKey, Object message) {
        //rabbitTemplate.convertAndSend(exchange, routingKey, message); //发消息没有携带关联数据

        //发消息需要携带关联数据：
        String id = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        GuiguCorrelationData guiguCorrelationData = new GuiguCorrelationData();
        guiguCorrelationData.setId(id);
        guiguCorrelationData.setMessage(message);
        guiguCorrelationData.setExchange(exchange);
        guiguCorrelationData.setRoutingKey(routingKey);

        //是否需要缓存关联数据？ 需要    为什么，因为退回需要根据关联数据重发消息。而且必须放在发消息前缓存。
        redisTemplate.opsForValue().set(id, JSON.toJSONString(guiguCorrelationData), 10, TimeUnit.MINUTES);

        //发消息携带关联数据，消息头中会存储关联数据的id
        rabbitTemplate.convertAndSend(exchange, routingKey, message, guiguCorrelationData);
        //发消息后进行缓存数据，可能造成，发消息立即回退，回退函数无法获取缓存，无法重发消息。
        //redisTemplate.opsForValue().set(id, JSON.toJSONString(guiguCorrelationData),10, TimeUnit.MINUTES);

        return true;
    }


    /**
     * 发送延迟消息方法
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息数据
     * @param delayTime  延迟时间，单位为：秒
     */
    public boolean sendDealyMessage(String exchange, String routingKey, Object message, int delayTime) {
        //1.创建自定义相关消息对象-包含业务数据本身，交换器名称，路由键，队列类型，延迟时间,重试次数
        GuiguCorrelationData correlationData = new GuiguCorrelationData();
        String uuid = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setId(uuid);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);
        correlationData.setDelay(true);
        correlationData.setDelayTime(delayTime); //默认值是10秒；可以设置其他时间。


        //3.将相关消息存入Redis  Key：UUID  相关消息对象  10 分钟
        redisTemplate.opsForValue().set(uuid, JSON.toJSONString(correlationData), 10, TimeUnit.MINUTES);

        //2.将相关消息封装到发送消息方法中
        rabbitTemplate.convertAndSend(exchange, routingKey, message, (message1) -> {
            message1.getMessageProperties().setDelay(delayTime * 1000); //消息延迟时间10秒；     MQ中ttl单位默认毫秒。
            return message1;
        }, correlationData);

        return true;

    }

}