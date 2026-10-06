package com.spzx.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全链路追踪过滤器
 * <p>
 * 在请求进入网关后最先执行（order = -300，早于 AuthFilter 的 -200），
 * 为每个请求生成唯一 traceId 并注入请求头 X-Trace-Id，同时写入 MDC。
 *
 * @author spzx
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();

        MDC.put("traceId", traceId);
        log.info("traceId: {} , url: {}", traceId, request.getURI().getPath());

        ServerWebExchange webExchange = exchange.mutate()
                .request(request)
                .build();

        return chain.filter(webExchange)
                .doFinally(signalType -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
