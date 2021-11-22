package com.yu.minimvc.support;

import com.yu.minimvc.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/9 0009下午 5:26
 */
public class MiniMvcServletFast extends FrameworkServlet {

    private static Logger log = LoggerFactory.getLogger(MiniMvcServletFast.class);
    private static final long serialVersionUID = 1L;

    /*----------------------------------------常量定义 ----------------------------------------*/

    /**
     * spring_handle 上下文方法名
     */
    public static final String SPRING_CONTROLLER_INVOKE_METHOD = "miniMvcServletFast_Spring_Controller_invoke_Method";

    /**
     * 页面路径相关
     */
    private static final String N = "\n";
    private static final String HTML_EXT = ".html";

    /**
     * 上下文
     */
    private static ApplicationContext applicationContext;

    /**
     * 拦截器
     */
    private List<HandlerInterceptor> interceptors;

    /**
     * rpc接口扫描器
     */
    private MvcUriMapperHandler mvcUriMapperHandler;

    /**
     * rpc扫描包根路径
     */
    private static String webBasePackage;

    /**
     * mvcRenderViewResolver
     */
    private MvcRenderViewResolver mvcRenderViewResolver;
    /**
     * mvcRenderViewResolver
     */
    private MvcHandlerResolver mvcHandlerResolver;

    /**
     * 接口地址上下文
     */
    private static String contextPath = SpringContextUtil.getProperties("server.servlet.context-path");

    private static AtomicBoolean hasInit = new AtomicBoolean(false);

    public static void setWebBasePackage(String webBasePackage) {
        MiniMvcServletFast.webBasePackage = webBasePackage;
    }


    public static void setApplicationContext(ApplicationContext applicationContext) {
        MiniMvcServletFast.applicationContext = applicationContext;
        SpringContextUtil.setContext(applicationContext);
    }

    public MiniMvcServletFast() {
        this.initCfg();
    }

    /**
     * 处理请求
     *
     * @param req
     * @param resp
     */
    @Override
    protected void doService(HttpServletRequest req, HttpServletResponse resp) {
        String uri = req.getRequestURI().substring(req.getContextPath().length());
        int interceptorIndex = -1;
        Object handlerObject = null;
        RuntimeException error = null;
        String htmlName = null;

        long time1 = System.currentTimeMillis();

        try {

            MvcUriMapperHandler.MvcUriMethod handler = mvcUriMapperHandler.getMappedHandler(uri);

            if (handler != null) {
                handlerObject = handler.getHandleObject();
                req.setAttribute(SPRING_CONTROLLER_INVOKE_METHOD, handler.getHandleMethod());
            }
            // 如果没有找到 handler 直接渲染页面
            else {
                // (只针对 login.html 结尾的请求)
                if (uri.endsWith(HTML_EXT)) {
                    htmlName = mvcRenderViewResolver.renderLogin(uri, req);
                }
                // 非 login.html请求 直接报错
                else {
                    resp.getWriter().print("request uri not found " + req.getRequestURI());
                    throw new ServletException(new StringBuilder(uri).append(MvcRenderViewResolver.ERROR_404).toString());
                }
            }

            // 执行拦截器 preHandle
            if (interceptors != null) {
                for (HandlerInterceptor interceptor : interceptors) {
                    interceptorIndex++;
                    if (!interceptor.preHandle(req, resp, handlerObject)) {
                        break;
                    }
                }
            }

            // 执行目标方法
            Object result = null;
            if (handler != null) {
                result = mvcHandlerResolver.invokeHandlerMethod(handler.getHandleMethod(), handler.getHandleObject(), req, resp);
            }

            // 执行拦截器 postHandle
            if (interceptors != null) {
                for (int i = interceptors.size() - 1; i >= 0; i--) {
                    HandlerInterceptor interceptor = interceptors.get(i);
                    interceptor.postHandle(req, resp, result, null);
                }
            }

            // 渲染页面
            mvcRenderViewResolver.renderSuccessView(htmlName, result, handler, req, resp, time1);
        } catch (Throwable ex) {
            if (ex instanceof InvocationTargetException) {
                ex = ((InvocationTargetException) ex).getTargetException();
            }
            if (ex instanceof BizException) {
                log.warn(ex.getClass().getName() + ":" + ex.getMessage(), ex);
            } else if (ex instanceof ServletException) {
                log.warn(ex.getClass().getName() + ":" + ex.getMessage());
            } else {
                log.error(ex.getMessage(), ex);
            }

            String errMsg = ex.getMessage();
            if (errMsg != null && errMsg.startsWith("com.yu.minimvc.exception.BizException:")) {
                errMsg = errMsg.substring("com.yu.minimvc.exception.BizException:".length()).trim();
                if (errMsg.indexOf(N) != -1) {
                    errMsg = errMsg.substring(0, errMsg.indexOf(N));
                }
                ex = new BizException(errMsg);
            }

            // 渲染页面
            mvcRenderViewResolver.renderExceptionView(req, resp, ex, handlerObject != null, time1);
            error = new RuntimeException(ex);
        } finally {
            // 拦截器收尾
            if (interceptors != null) {
                for (int i = interceptorIndex; i >= 0; i--) {
                    HandlerInterceptor interceptor = interceptors.get(i);
                    try {
                        interceptor.afterCompletion(req, resp, handlerObject, error);
                    } catch (Throwable ex2) {
                        log.error("HandlerInterceptor.afterCompletion threw exception", ex2);
                    }
                }
            }
        }
    }


    /**
     * 初始化 mini-mvc
     */
    public void initCfg() {
        if (!hasInit.compareAndSet(false, true)) {
            return;
        }
        // 初始化 uri handler映射
        if (webBasePackage != null) {
            webBasePackage = webBasePackage.trim();
        } else {
            throw new RuntimeException("[miniMvcServletFast] webBasePackage is null");
        }
        if (null == mvcRenderViewResolver) {
            mvcUriMapperHandler = applicationContext.getAutowireCapableBeanFactory().createBean(MvcUriMapperHandler.class);
        }
        if (contextPath != null) {
            mvcUriMapperHandler.setContextPath(contextPath);
        }
        mvcUriMapperHandler.setRootPackage(webBasePackage);
        mvcUriMapperHandler.detectHandlerByServlet();

        if (null == mvcRenderViewResolver) {
            mvcRenderViewResolver = applicationContext.getAutowireCapableBeanFactory().createBean(MvcRenderViewResolver.class);
        }
        if (null == mvcHandlerResolver) {
            mvcHandlerResolver = applicationContext.getAutowireCapableBeanFactory().createBean(MvcHandlerResolver.class);
        }
        // 初始化 interceptors
        try {
            interceptors = (List<HandlerInterceptor>) applicationContext.getBean("mvcInterceptors");
        } catch (Exception e) {
            log.info(e.getMessage(), e);
        }
    }
}
