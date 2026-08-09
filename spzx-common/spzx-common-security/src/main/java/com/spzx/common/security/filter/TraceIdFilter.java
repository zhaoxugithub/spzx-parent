package com.spzx.common.security.filter;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.utils.StringUtils;

/**
 * 全链路追踪 Filter
 *
 * 从请求头中提取 X-Trace-Id（由 Gateway TraceIdFilter 生成），
 * 写入 MDC，使后续所有日志自动带上 traceId 实现跨服务关联。
 * 请求完成后清除 MDC，防止内存泄漏。
 *
 * @author spzx
 */
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String traceId = httpRequest.getHeader(SecurityConstants.TRACE_ID);
            if (StringUtils.isNotEmpty(traceId)) {
                MDC.put("traceId", traceId);
            }
            log.debug("traceId: {} , url: {}", traceId, httpRequest.getRequestURI());
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void destroy() {
    }
}
