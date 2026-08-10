package com.spzx.common.core.interceptor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * MyBatis SQL 日志拦截器
 * <p>
 * 在日志中直接输出 MySQL 实际执行的 SQL，参数占位符 ? 被替换为真实参数值，例如：
 * SELECT id, status FROM product_sku WHERE status = 1
 * <p>
 * 通过配置项 spzx.sql-log.enabled 控制开关（默认开启），可在 Nacos 共享配置中关闭：
 * spzx:
 *   sql-log:
 *     enabled: false
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class MybatisSqlLogInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger("com.spzx.sqlLog");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${spzx.sql-log.enabled:true}")
    private boolean enabled;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!enabled) {
            return invocation.proceed();
        }
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = invocation.getArgs().length == 6
                ? (BoundSql) invocation.getArgs()[5]
                : mappedStatement.getBoundSql(parameter);

        long start = System.currentTimeMillis();
        Object result;
        try {
            result = invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                log.info("==> {} | {} ms | {}", mappedStatement.getId(), cost, formatSql(mappedStatement, boundSql));
            } catch (Exception e) {
                // 日志格式化失败不影响业务执行（防御性处理）
                log.warn("SQL 日志格式化失败: {} | {}", mappedStatement.getId(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 将 BoundSql 中的 ? 依次替换为真实参数值
     */
    private String formatSql(MappedStatement mappedStatement, BoundSql boundSql) {
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();
        if (mappings == null || mappings.isEmpty() || parameterObject == null) {
            return sql;
        }
        TypeHandlerRegistry typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();

        StringBuilder sb = new StringBuilder(sql.length() + 64);
        int index = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && index < mappings.size()) {
                ParameterMapping mapping = mappings.get(index++);
                Object value = resolveValue(mappedStatement, mapping, parameterObject, boundSql, typeHandlerRegistry);
                sb.append(formatValue(value, mapping));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 解析参数值，逻辑与 MyBatis DefaultParameterHandler 保持一致：
     * 1) 优先取 BoundSql 附加参数 —— MyBatis-Plus 分页重写 SQL 后的
     *    mybatis_plus_first / mybatis_plus_second 就存在这里，不在参数对象中；
     * 2) 简单类型直接取值；
     * 3) 复杂对象/Map 通过 MetaObject 按 propertyName 取值
     *    （可正确处理 MyBatis-Plus 的 ew.paramNameValuePairs.xxx 嵌套属性）。
     */
    private Object resolveValue(MappedStatement mappedStatement, ParameterMapping mapping,
                                Object parameterObject, BoundSql boundSql,
                                TypeHandlerRegistry typeHandlerRegistry) {
        if (parameterObject == null) {
            return null;
        }
        String propertyName = mapping.getProperty();
        // MyBatis-Plus 分页参数不在 parameterObject 中，必须从 BoundSql 附加参数获取
        if (boundSql.hasAdditionalParameter(propertyName)) {
            return boundSql.getAdditionalParameter(propertyName);
        }
        if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            return parameterObject;
        }
        MetaObject metaObject = mappedStatement.getConfiguration().newMetaObject(parameterObject);
        return metaObject.getValue(propertyName);
    }

    /**
     * 将参数值格式化为 SQL 字面量
     */
    private String formatValue(Object value, ParameterMapping mapping) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }
        if (value instanceof Character) {
            return "'" + value + "'";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Timestamp) {
            return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value) + "'";
        }
        if (value instanceof Date) {
            return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value) + "'";
        }
        if (value instanceof LocalDateTime) {
            return "'" + ((LocalDateTime) value).format(DATE_TIME_FORMATTER) + "'";
        }
        if (value instanceof LocalDate) {
            return "'" + ((LocalDate) value).format(DATE_FORMATTER) + "'";
        }
        if (value instanceof LocalTime) {
            return "'" + ((LocalTime) value).format(TIME_FORMATTER) + "'";
        }
        if (value instanceof byte[]) {
            return "X'" + bytesToHex((byte[]) value) + "'";
        }
        // 枚举、UUID 等其他类型统一按字符串输出
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    @Override
    public Object plugin(Object target) {
        return org.apache.ibatis.plugin.Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
