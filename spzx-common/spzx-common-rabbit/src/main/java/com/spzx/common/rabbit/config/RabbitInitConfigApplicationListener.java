package com.spzx.common.rabbit.config;

import com.alibaba.fastjson2.JSON;
import com.spzx.common.rabbit.entity.GuiguCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 服务器启动时，执行rabbitTemplate初始化，设置确认函数和回退函数
 * ApplicationEvent      一些子事件的父类。
 * ApplicationReadyEvent 具体子事件。表示应用程序启动好，IOC容器初始化好，存在相关bean对象了。再进行相关的初始化。
 * 也可以使用相关的注解替代： @EventListener
 */
@Slf4j
@Component
public class RabbitInitConfigApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RedisTemplate redisTemplate;

    //IOC容器初始化bean对象后，执行额外的其他初始化操作。
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        this.setUpInitRabbitTemplete();
    }

    //注册两个回调函数：
    //1.确认回调函数
    //2.回退回调函数
    private void setUpInitRabbitTemplete() {

        //注册确认回调函数
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            //发消息时如果携带了关联数据，会把关联数据退回来；
            //如果发消息没有携带关联数据,那么，就是null
            log.info("setConfirmCallback - correlationData=" + correlationData);
            if (ack) { //交换机收到
                log.info("交换机收到消息...");
            } else { //交换机没收到
                log.info("交换机没收到消息...");
                log.error("原因：" + cause);

                //可以通过关联数据，进行消息重复：
                //执行消息重发
                this.retrySendMsg(correlationData);
            }
        });

        //注册回退回调函数
        rabbitTemplate.setReturnsCallback((returnedMessage) -> {
            //returnedMessage=ReturnedMessage [message=(Body:'我是延迟消息'
            // MessageProperties [headers={spring_returned_message_correlation=mq:1391a4778f6447f89d5b4590ae2659ef},
            // contentType=text/plain, contentEncoding=UTF-8, contentLength=0, receivedDeliveryMode=PERSISTENT,
            // priority=0, receivedDelay=10000, deliveryTag=0]), replyCode=312, replyText=NO_ROUTE,
            // exchange=exchange.delay, routingKey=routing.delay]
            log.info("returnedMessage=" + returnedMessage);

            //消息重发，如何获取关联数据？ 关联数据没有回退。可以从缓存中获取。
            String correlationId = returnedMessage.getMessage()
                    .getMessageProperties().getHeader("spring_returned_message_correlation");
            String correlationDataStr = (String) redisTemplate.opsForValue().get(correlationId);
            GuiguCorrelationData correlationData = JSON.parseObject(correlationDataStr, GuiguCorrelationData.class);


            if(correlationData.isDelay()){ // true 表示延迟插件发送延迟消息，那么则不进行重发。
                return;
            }

            this.retrySendMsg(correlationData);
        });


    }

    //重发消息
    private void retrySendMsg(CorrelationData correlationData) {
        GuiguCorrelationData guiguCorrelationData = (GuiguCorrelationData) correlationData;
        int retryCount = guiguCorrelationData.getRetryCount();
        if (retryCount >= 3) { //  0  1  2   重试三次
            //超过最大重试次数
            log.error("生产者超过最大重试次数，将失败的消息存入数据库用人工处理；给管理员发送邮件；给管理员发送短信；");
            return;
        }
        retryCount ++;
        guiguCorrelationData.setRetryCount(retryCount);

        //这行代码发在发消息前存储缓存数据。
        redisTemplate.opsForValue().set(guiguCorrelationData.getId(),
                JSON.toJSONString(guiguCorrelationData),10, TimeUnit.MINUTES);

        //消息重发
        rabbitTemplate.convertAndSend(guiguCorrelationData.getExchange(),
                guiguCorrelationData.getRoutingKey(),
                guiguCorrelationData.getMessage(),
                guiguCorrelationData);
        log.info("************  重发消息  ************");
    }


}
