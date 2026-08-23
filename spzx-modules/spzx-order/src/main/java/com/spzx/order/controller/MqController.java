package com.spzx.order.controller;

import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.configure.DeadLetterMqConfig;
import com.spzx.order.configure.DelayedMqConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mq接口管理")
@RestController
@RequestMapping("/mq")
public class MqController extends BaseController {
    @Autowired
    private RabbitService rabbitService; //推荐使用封装业务接口发送消息。

    //private RabbitTemplate rabbitTemplate; //不推荐使用模板直接发消息。

    @Operation(summary = "发送消息")
    @GetMapping("/sendMessage")
    public AjaxResult sendMessage() {
        rabbitService.sendMessage(MqConst.EXCHANGE_TEST, MqConst.ROUTING_TEST, "hello");
        return success();
    }

    @Operation(summary = "发送确认消息")
    @GetMapping("/sendConfirmMessage")
    public AjaxResult sendConfirmMessage() {
        //1.测试正常流程，交换机名称正确，发送路由key正确
        //rabbitService.sendMessage(MqConst.EXCHANGE_TEST, MqConst.ROUTING_CONFIRM, "hello, confirm");

        //2.测试不正常流程： 交换机名称错了
        //channel error; protocol method: #method<channel.close>(reply-code=404,
        // reply-text=NOT_FOUND - no exchange 'xxx' in vhost '/', class-id=60, method-id=40)
        //rabbitService.sendMessage("xxx", MqConst.ROUTING_CONFIRM, "hello, confirm");

        //3.测试不正常流程： 路由key名称错了。找不到任何匹配的队列,消息会退回
        //returnedMessage=ReturnedMessage [message=(Body:'hello, confirm' MessageProperties [headers={},
        // contentType=text/plain, contentEncoding=UTF-8, contentLength=0, receivedDeliveryMode=PERSISTENT,
        // priority=0, deliveryTag=0]), replyCode=312, replyText=NO_ROUTE, exchange=spzx.test, routingKey=xxx]
        rabbitService.sendMessage(MqConst.EXCHANGE_TEST, "xxx", "hello, confirm");
        return success();
    }


    /**
     * 消息发送延迟消息：基于死信实现
     */
    @Operation(summary = "发送延迟消息：基于死信实现")
    @GetMapping("/sendDeadLetterMsg")
    public AjaxResult sendDeadLetterMsg() {
        //往正常交换机发送，指定正常路由key
        rabbitService.sendMessage(DeadLetterMqConfig.exchange_dead, DeadLetterMqConfig.routing_dead_1, "我是延迟消息");
        return success();
    }

    @Operation(summary = "发送延迟消息：基于延迟插件")
    @GetMapping("/sendDelayMsg")
    public AjaxResult sendDelayMsg() {
        //调用工具方法发送延迟消息
        int delayTime = 10; //延迟10秒
        rabbitService.sendDelayMessage(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay, "我是延迟消息", delayTime);
        return success();
    }
}