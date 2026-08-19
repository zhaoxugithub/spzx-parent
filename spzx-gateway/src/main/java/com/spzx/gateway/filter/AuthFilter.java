package com.spzx.gateway.filter;

import com.spzx.common.core.constant.CacheConstants;
import com.spzx.common.core.constant.HttpStatus;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.constant.TokenConstants;
import com.spzx.common.core.utils.JwtUtils;
import com.spzx.common.core.utils.ServletUtils;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.redis.service.RedisService;
import com.spzx.gateway.config.properties.IgnoreWhiteProperties;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关鉴权
 * <p>
 * <p>
 * GlobalFilter 所有的请求都会去执行
 *
 * @author spzx
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    // 排除过滤的 uri 地址，nacos自行添加
    @Autowired
    private IgnoreWhiteProperties ignoreWhite;

    @Autowired
    private RedisService redisService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder mutate = request.mutate();

        // http://localhost:8080/product/brand/list
        // 判断路径是否在白名单中，在的话则无需校验jwt令牌；
        String url = request.getURI().getPath();
        // 跳过不需要验证的路径
        if (StringUtils.matches(url, ignoreWhite.getWhites())) {
            return chain.filter(exchange);
        }

        //从请求头中获取jwt令牌：去掉前缀
        String token = getToken(request);
        if (StringUtils.isEmpty(token)) {
            return unauthorizedResponse(exchange, "令牌不能为空");
        }

        // {user_id:1,user_key:'5026f1e6-cfcd-4a77-b286-60d0ef5f1604',username:'admin'}
        Claims claims = JwtUtils.parseToken(token); //解析主体 Claims就是个Map集合
        if (claims == null) {
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }
        String userkey = JwtUtils.getUserKey(claims);
        boolean islogin = redisService.hasKey(getTokenKey(userkey));
        if (!islogin) {
            return unauthorizedResponse(exchange, "登录状态已过期");
        }
        String userid = JwtUtils.getUserId(claims);
        String username = JwtUtils.getUserName(claims);
        if (StringUtils.isEmpty(userid) || StringUtils.isEmpty(username)) {
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        // 设置用户信息到请求
        addHeader(mutate, SecurityConstants.USER_KEY, userkey);
        addHeader(mutate, SecurityConstants.DETAILS_USER_ID, userid);
        addHeader(mutate, SecurityConstants.DETAILS_USERNAME, username);
        // 内部请求来源参数清除
        removeHeader(mutate, SecurityConstants.FROM_SOURCE);
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value) {
        if (value == null) {
            return;
        }
        String valueStr = value.toString();
        String valueEncode = ServletUtils.urlEncode(valueStr);
        mutate.header(name, valueEncode);
    }

    private void removeHeader(ServerHttpRequest.Builder mutate, String name) {
        mutate.headers(httpHeaders -> httpHeaders.remove(name)).build();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        MDC.put("traceId", traceId != null ? traceId : "N/A");
        log.error("[鉴权异常处理]请求路径:{}, 异常信息:{}", exchange.getRequest().getPath(), msg);
        MDC.clear();
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(), msg, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 获取缓存key
     */
    private String getTokenKey(String token) {
        return CacheConstants.LOGIN_TOKEN_KEY + token;
    }

    /**
     * 获取请求token
     */
    private String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(TokenConstants.AUTHENTICATION);
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
            // 去掉前缀； "Bearer "
            token = token.replaceFirst(TokenConstants.PREFIX, StringUtils.EMPTY);
        }
        return token;
    }

    // order 值越小越是先执行
    @Override
    public int getOrder() {
        return -200;
    }
}
