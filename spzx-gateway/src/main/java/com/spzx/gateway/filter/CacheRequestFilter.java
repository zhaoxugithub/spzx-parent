package com.spzx.gateway.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * 获取body请求数据（解决流不能重复读取问题）
 *
 * @author spzx
 */
@Component
public class CacheRequestFilter extends AbstractGatewayFilterFactory<CacheRequestFilter.Config> {
    public CacheRequestFilter() {
        super(Config.class);
    }

    @Override
    public String name() {
        return "CacheRequestFilter";
    }

    public GatewayFilter apply(Config config) {
        GatewayFilter gatewayFilter = (exchange, chain) -> {
            // GET DELETE 不过滤
            HttpMethod method = exchange.getRequest().getMethod();
            if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
                return chain.filter(exchange);
            }
            return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (serverHttpRequest) -> {
                // ServerHttpRequestDecorator
                if (serverHttpRequest == exchange.getRequest()) {
                    return chain.filter(exchange);
                }
                return chain.filter(exchange.mutate().request(serverHttpRequest).build());
            });
        };
        Integer order = config.getOrder();
        if (order == null) {
            return gatewayFilter;
        }
        return new OrderedGatewayFilter(gatewayFilter, order);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.singletonList("order");
    }

    @Setter
    @Getter
    static class Config {
        private Integer order;
    }
}
