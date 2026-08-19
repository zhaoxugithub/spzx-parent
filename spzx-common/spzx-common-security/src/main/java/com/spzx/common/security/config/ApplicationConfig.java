package com.spzx.common.security.config;

import java.util.TimeZone;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import com.spzx.common.security.filter.TraceIdFilter;

/**
 * 系统配置
 *
 * @author spzx
 */
public class ApplicationConfig {
    /**
     * 时区配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder.timeZone(TimeZone.getDefault());
    }

    /**
     * 全链路追踪 Filter 注册
     * order = -100，在 HeaderInterceptor 之前执行
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(-300);
        return registration;
    }
}
