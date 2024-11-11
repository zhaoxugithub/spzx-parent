package com.spzx.order.receiver;

import com.rabbitmq.client.Channel;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.configure.DeadLetterMqConfig;
import com.spzx.order.service.IOrderInfoService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OrderReceiver {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Autowired
    private RabbitService rabbitService;

    @SneakyThrows //等价于   /*throws Exception*/
    @RabbitListener(bindings= {
            @QueueBinding(
                    exchange=@Exchange(name = MqConst.EXCHANGE_TEST),
                    value = @Queue(name = MqConst.QUEUE_TEST,durable = "true"),
                    key = {
                            MqConst.ROUTING_TEST
                    }
            )
    })
    public void consumer(String msg, Message message, Channel channel) /*throws Exception*/ {
        try {
            //log.info("msg="+msg);
            log.info("msg={}",msg);
            log.info("message="+message);
            int i = 1/0;
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,false);
        }
    }



    /**
     * 监听确认消息
     * @param message
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TEST, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_CONFIRM, durable = "true"),
            key = MqConst.ROUTING_CONFIRM
    ))
    public void confirm(String content, Message message, Channel channel) {
        log.info("接收确认消息：{}", content);

        // false 确认一个消息，true 批量确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }




    /**
     * 监听延迟消息,监听死信队列。
     * @param msg
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(queues = {DeadLetterMqConfig.queue_dead_2})
    public void getDeadLetterMsg(String msg, Message message, Channel channel) {
        log.info("死信消费者：{}", msg);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}