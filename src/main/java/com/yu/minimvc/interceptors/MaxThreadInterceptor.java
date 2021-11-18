package com.yu.minimvc.interceptors;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.yu.minimvc.annotation.ApiMaxThread;
import com.yu.minimvc.exception.BizException;
import com.yu.minimvc.support.SpringContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * 设置接口请求最大线程数
 */
public class MaxThreadInterceptor extends HandlerInterceptorAdapter {

    private static Logger log = LoggerFactory.getLogger(MaxThreadInterceptor.class);

    private static ConcurrentHashMap<String, AtomicInteger> uriMap = new ConcurrentHashMap<>();

    ThreadLocal<Boolean> local = new ThreadLocal<>();

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Method method = SpringContextUtil.getHandlerMethod(request);

        String uri = request.getRequestURI();

        if (method != null && method.isAnnotationPresent(ApiMaxThread.class)) {
            local.set(true);
            int max = method.getAnnotation(ApiMaxThread.class).value();
            if (max <= 0) {
                return true;
            }
            AtomicInteger times = uriMap.get(uri);
            if (times == null) {
                times = new AtomicInteger(0);
            }
            times.incrementAndGet();
            uriMap.put(uri, times);
            log.info("request pre count {}: ", times.get());
            if (times.get() > max) {
                throw new BizException("系统繁忙,请稍后再试");
            }
        }

        return true;
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        if (local.get() != null && local.get()) {
            String uri = request.getRequestURI();
            AtomicInteger times = uriMap.get(uri);

            if (times != null) {
                times.decrementAndGet();
                log.info("request after count {}: ", times.get());
                uriMap.put(uri, times);
            }
        }
        local.remove();
    }

}
