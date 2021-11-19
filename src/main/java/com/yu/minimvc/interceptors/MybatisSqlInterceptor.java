package com.yu.minimvc.interceptors;

import com.yu.minimvc.exception.BizException;
import com.yu.minimvc.page.PageList;
import com.yu.minimvc.page.PageParam;
import com.yu.minimvc.page.ThreadPagingUtil;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Intercepts(@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
        RowBounds.class, ResultHandler.class}))
public class MybatisSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MybatisSqlInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        PageParam param = ThreadPagingUtil.get();

        if (param != null && param.getOpenPage()) {

            try {

                int start = 0;
                Integer limit = param.getPageSize();
                if (limit == null || limit <= 0) {
                    limit = 20;
                }

                Integer currentPage = param.getTargetPage();
                if (currentPage == null) {
                    currentPage = 1;
                }

                if (currentPage > 1) {
                    start = (currentPage - 1) * limit;
                }

                // 原始sql处理
                String sql = getSqlByInvocation(invocation);
                if (StringUtils.isEmpty(sql)) {
                    return invocation.proceed();
                }
                sql = sql.trim();
                if (!sql.toLowerCase().startsWith("select")) {
                    throw new BizException("非查询语句不能开启 ThreadPagingUtil 分页");
                }

                // 查询总条数处理
                //				sql = "select SQL_CALC_FOUND_ROWS" + sql.substring(6);
                // 需拼接查询总数量SQL，直接使用SQL_CALC_FOUND_ROWS没法走索引
                MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
                Object parameter = null;
                if (invocation.getArgs().length > 1) {
                    parameter = invocation.getArgs()[1];
                }
                BoundSql boundSql = mappedStatement.getBoundSql(parameter);
                Configuration configuration = mappedStatement.getConfiguration();
                String countSql = formatSql(sql, configuration, boundSql);
                int fromIndex = countSql.toLowerCase().indexOf(" from ");
                if (fromIndex != -1) {
                    countSql = countSql.substring(fromIndex);
                    countSql = "select count(1)" + countSql;
                    int oldIndex = countSql.lastIndexOf("order by");
                    if (oldIndex != -1) {
                        countSql = countSql.substring(0, oldIndex);
                    }
                    log.info("[mybatis Sql Interceptor] count SQL format as: {}", countSql);
                }
                if (param.getOrderByColumn() != null && param.getOrderByType() != null) {
                    int oldIndex = sql.indexOf("order by");
                    if (oldIndex != -1) {
                        sql = sql.substring(0, oldIndex);
                    }
                    sql += " order by " + param.getOrderByColumn() + " " + param.getOrderByType();
                }
                sql += " limit " + start + ", " + limit;
                log.info("[mybatis Sql Interceptor] full SQL format as: {}", sql);
                resetSql2Invocation(invocation, sql);
                // 数据对象
                Object obj1 = invocation.proceed();

                if (!(obj1 instanceof List)) {
                    throw new BizException("分页查询必须返回 List 对象");
                }

                List<?> list = (List<?>) obj1;

                if (list != null && list.size() > 0) {

                    CachingExecutor ce = (CachingExecutor) invocation.getTarget();
                    Transaction transaction = ce.getTransaction();
                    ResultSet rs = transaction.getConnection().createStatement().executeQuery(countSql);
                    int count = 0;
                    while (rs.next()) {
                        count = rs.getInt(1);
                        break;
                    }
                    int pageSize = limit;
                    int left = count % pageSize;
                    int totalPage = left == 0 ? count / pageSize : count / pageSize + 1;
                    PageList<?> pageList = new PageList<>(list);
                    pageList.setTotalSize(count);
                    pageList.setCurrentPage(currentPage);
                    pageList.setHasNext(currentPage < totalPage ? true : false);
                    pageList.setHasPre(currentPage > 1 ? true : false);
                    pageList.setPageSize(pageSize);
                    pageList.setTotalPage(totalPage);
                    return pageList;
                } else {
                    PageList<?> pageList = new PageList<>(list);
                    pageList.setTotalSize(0);
                    pageList.setCurrentPage(currentPage);
                    pageList.setHasNext(false);
                    pageList.setHasPre(false);
                    pageList.setPageSize(limit);
                    pageList.setTotalPage(0);
                    return pageList;
                }
            } finally {
                ThreadPagingUtil.clear();
            }
        }

        return invocation.proceed();

    }

    @Override
    public Object plugin(Object obj) {
        return Plugin.wrap(obj, this);
    }

    @Override
    public void setProperties(Properties arg0) {
        // doSomething
    }

    /**
     * 获取sql语句
     *
     * @param invocation
     * @return
     */
    private String getSqlByInvocation(Invocation invocation) {
        final Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameterObject = args[1];
        BoundSql boundSql = ms.getBoundSql(parameterObject);
        return boundSql.getSql();
    }

    /**
     * 包装sql后，重置到invocation中
     *
     * @param invocation
     * @param sql
     * @throws SQLException
     */
    private void resetSql2Invocation(Invocation invocation, String sql) throws SQLException {
        final Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        Object parameterObject = args[1];
        BoundSql boundSql = statement.getBoundSql(parameterObject);
        MappedStatement newStatement = newMappedStatement(statement, new BoundSqlSqlSource(boundSql));
        MetaObject msObject = MetaObject.forObject(newStatement, new DefaultObjectFactory(),
                new DefaultObjectWrapperFactory(), new DefaultReflectorFactory());
        msObject.setValue("sqlSource.boundSql.sql", sql);
        args[0] = newStatement;
    }

    private MappedStatement newMappedStatement(MappedStatement ms, SqlSource newSqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource,
                ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            StringBuilder keyProperties = new StringBuilder();
            for (String keyProperty : ms.getKeyProperties()) {
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length() - 1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());

        return builder.build();
    }

    /**
     * 将占位符替换成参数值
     *
     * @param sql
     * @param configuration
     * @param boundSql
     * @return
     */
    private String formatSql(String sql, Configuration configuration, BoundSql boundSql) {
        //美化sql
        sql = beautifySql(sql);
        //填充占位符, 目前基本不用mybatis存储过程调用,故此处不做考虑
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        List<String> parameters = new ArrayList<>();
        if (parameterMappings != null) {
            MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    // 参数值
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    // 获取参数名称
                    if (boundSql.hasAdditionalParameter(propertyName)) {
                        // 获取参数值
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        // 如果是单个值则直接赋值
                        value = parameterObject;
                    } else {
                        value = metaObject == null ? null : metaObject.getValue(propertyName);
                    }
                    if (value instanceof Number) {
                        parameters.add(String.valueOf(value));
                    } else {
                        StringBuilder builder = new StringBuilder();
                        builder.append("'");
                        if (value instanceof Date) {
                            builder.append(dateTimeFormatter.get().format((Date) value));
                        } else if (value instanceof String) {
                            builder.append(value);
                        }
                        builder.append("'");
                        parameters.add(builder.toString());
                    }
                }
            }
        }
        for (String value : parameters) {
            sql = sql.replaceFirst("\\?", value);
        }
        return sql;
    }

    private static ThreadLocal<SimpleDateFormat> dateTimeFormatter = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    public static String beautifySql(String sql) {
        sql = sql.replaceAll("[\\s\n ]+", " ");
        return sql;
    }

    // 定义一个内部辅助类，作用是包装sq
    class BoundSqlSqlSource implements SqlSource {

        private BoundSql boundSql;

        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }
}
