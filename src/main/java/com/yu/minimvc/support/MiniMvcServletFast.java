package com.yu.minimvc.support;

import com.yu.minimvc.annotation.UserDefine;
import com.yu.minimvc.common.CommonUtil;
import com.yu.minimvc.common.MimeTypes;
import com.yu.minimvc.domain.JsonResult;
import com.yu.minimvc.exception.BizException;
import com.yu.minimvc.exception.LoginTimeoutException;
import com.yu.minimvc.load.ClassHelper;
import com.yu.minimvc.load.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndViewDefiningException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/9 0009下午 5:26
 */
public class MiniMvcServletFast extends HttpServlet {

    private static Logger log = LoggerFactory.getLogger(MiniMvcServletFast.class);
    private static final long serialVersionUID = 1L;

    /*----------------------------------------常量定义 ----------------------------------------*/

    // spring 上下文方法名
    public static final String SPRING_CONTROLLER_INVOKE_METHOD = "miniMvcServletFast_Spring_Controller_invoke_Method";

    // 页面路径相关
    private static final String SCREEN = "screen";
    private static final String LOGIN_PAGE = "/login.html";
    private static final String N = "\n";
    private static final String SPLIT = "/";
    private static final String INDEX = "index";
    private static final String ERRORS = "errors";

    private static final String HTML_EXT = ".html";
    private static final String JSON_EXT = ".json";

    // 错误相关
    private static final String POINT = ".";
    private static final int ERROR_404 = 404;
    private static final int ERROR_500 = 500;
    private static final int ERROR_501 = 501;
    private static final int ERROR_601 = 601;
    private static final int ERROR_602 = 602;

    private static final String ERROR_MESSAGE = "error_message";

    private static final String ERROR_NOT_FOUND = "resource not found";

    // content type相关
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    private static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";

    // 日志打印相关
    private static final String URI = "uri";
    private static final String COST_TIME = "costTime";
    private static final String REMOTE_IP = "remoteIp";
    private static final String PARAM = "param";
    private static final String RESPONSE = "response";

    private static final String BLANK = "";

    private static final String LOG_HEAD = "[mini-mvc] response message:";


    private static ApplicationContext context = null;

    // 拦截器
    private static List<HandlerInterceptor> interceptors;

    // rpc接口扫描器
    private static MvcUriMapperUtil uriHandleMappedUtil;

    // rpc扫描包根路径
    private static String webBasePackage;

    // html 的物理路径
    private static String HTML_LOADER_ROOT = "views";

    // 接口地址上下文
    private static String contextPath = SpringContextUtil.getProperties("server.servlet.context-path");

    private static Map<String, String> htmlCache = new ConcurrentHashMap<>();

    private static AtomicBoolean hasInit = new AtomicBoolean(false);

    public static void setWebBasePackage(String webBasePackage) {
        MiniMvcServletFast.webBasePackage = webBasePackage;
    }

    public static void setContextPath(String contextPath) {
        MiniMvcServletFast.contextPath = contextPath;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp);
    }

    /**
     * 处理接受get,post请求
     *
     * @param req
     * @param resp
     */
    private void dispatch(HttpServletRequest req, HttpServletResponse resp) {
        // 获取 uri
        String uri = req.getRequestURI().substring(req.getContextPath().length());
        req.getRequestDispatcher("");
        log.info("[mini-mvc] request uri:{}", uri);
        int interceptorIndex = -1;
        Object handlerObject = null;
        RuntimeException error = null;
        String htmlName = null;

        long time1 = System.currentTimeMillis();

        try {

            if (uri.equals("/favicon.ico")) {
                resp.sendError(404);
                return;
            }

            if (uri.startsWith("/statics")) {
                renderStaticFiles(uri, resp);
                return;
            }

            MvcUriMapperHandler handler = uriHandleMappedUtil.getMappedHandler(uri);

            if (handler != null) {
                handlerObject = handler.getHandleObject();
                req.setAttribute(SPRING_CONTROLLER_INVOKE_METHOD, handler.getHandleMethod());
            }
            // 如果没有找到 handler 直接渲染页面
            else {
                // (只针对 login.html 结尾的请求)
                if (uri.endsWith(HTML_EXT)) {
                    String path = SCREEN + uri.substring(0, uri.length() - HTML_EXT.length());
                    String htmlPath = new StringBuilder(HTML_LOADER_ROOT).append(SPLIT).append(path).append(HTML_EXT).toString();
                    InputStream is = ConfigLoader.loadResource(htmlPath, false, false);
                    if (is != null) {
                        htmlName = path;
                        // 便于在页面上直接使用请求参数
                        Enumeration<String> names = req.getParameterNames();
                        while (names.hasMoreElements()) {
                            String key = names.nextElement();
                            String[] values = req.getParameterValues(key);
                            if (values != null) {
                                if (values.length > 1) {
                                    req.setAttribute(key, values);
                                } else if (values.length == 1) {
                                    req.setAttribute(key, values[0]);
                                }
                            }
                        }
                    } else {
                        throw new ServletException(htmlPath + " not exists");
                    }
                }
                // 非 login.html请求 直接报错
                else {
                    resp.getWriter().print("request uri not found " + req.getRequestURI());
                    throw new ServletException(new StringBuilder(uri).append(ERROR_404).toString());
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
                result = uriHandleMappedUtil.invokeHandlerMethod(handler.getHandleMethod(), handler.getHandleObject(), req, resp);
            }

            // 执行拦截器 postHandle
            if (interceptors != null) {
                for (int i = interceptors.size() - 1; i >= 0; i--) {
                    HandlerInterceptor interceptor = interceptors.get(i);
                    interceptor.postHandle(req, resp, result, null);
                }
            }

            // 渲染页面
            renderSuccessView(htmlName, result, handler, req, resp, time1);
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
            renderExceptionView(req, resp, ex, handlerObject != null, time1);
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
     * 渲染错误页面
     *
     * @param request
     * @param response
     * @param ex
     * @param hasHandler
     * @param startTime
     */
    private void renderExceptionView(HttpServletRequest request, HttpServletResponse response, Throwable ex, Boolean hasHandler, long startTime) {
        try {
            Integer errorCode = ERROR_500;
            // for exception
            if (ex != null) {
                if (ex instanceof ServletException || ex instanceof ModelAndViewDefiningException) {
                    ex = new RuntimeException(ERROR_NOT_FOUND);
                    errorCode = ERROR_404;
                } else if (ex instanceof BizException) {
                    errorCode = ERROR_500;
                } else if (ex instanceof LoginTimeoutException) {
                    errorCode = ERROR_601;
                } else {
                    errorCode = ERROR_501;
                }
            } else {
                errorCode = ERROR_501;
                ex = new RuntimeException("接口服务异常");
            }

            String uri = request.getRequestURI();
            uri = uri.substring(request.getContextPath().length());

            // redirect to error page
            if (uri.equals(SPLIT) || uri.endsWith(HTML_EXT)) {
                if (ex instanceof LoginTimeoutException) {
                    response.sendRedirect(request.getContextPath() + LOGIN_PAGE);
                } else {
                    request.setAttribute(ERROR_MESSAGE, ex.getMessage());
                    renderHtml(new StringBuilder(ERRORS).append(SPLIT).append(errorCode).toString(), request, response, startTime);
                }
            }
            // write error json
            else if (uri.endsWith(JSON_EXT)) {
                JsonResult result = new JsonResult();
                result.setCode(errorCode);
                if (ex != null && ex.getMessage() != null && ex instanceof Exception) {
                    result.setMessage(ex.getMessage());
                }
                if (ex != null && ex instanceof BizException) {
                    result.setMessage(ex.getMessage());
                    BizException bex = (BizException) ex;
                    Object errorObject = bex.getErrorData();
                    if (errorObject != null) {
                        result.setData(errorObject);
                    }
                }
                outJson(result, request, response, null, startTime);
            }
            // 其他请求跳404
            else {
                renderHtml(new StringBuilder(ERRORS).append(SPLIT).append(ERROR_404).toString(), request, response, startTime);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 渲染页面html,或者json数据
     *
     * @param htmlName
     * @param result
     * @param handler
     * @param request
     * @param response
     * @param startTime
     * @throws Exception
     */
    private void renderSuccessView(String htmlName, Object result, MvcUriMapperHandler handler, HttpServletRequest request, HttpServletResponse response, long startTime) throws Exception {
        // htmlName不为空，说明是直接访问.htmlName 页面
        if (htmlName != null) {
            renderHtml(htmlName, request, response, startTime);
            return;
        }

        String uri = request.getRequestURI();
        uri = uri.substring(request.getContextPath().length());

        // 默认根路径请求
        if (uri.equals(SPLIT)) {
            if (handler.getHandleMethod().isAnnotationPresent(UserDefine.class)) {
                printAccessLog(request, null, startTime);
                return;
            }
            String path = new StringBuilder(SCREEN).append(SPLIT).append(INDEX).toString();
            renderHtml(path, request, response, startTime);
        }
        // json 格式请求
        else if (uri.endsWith(JSON_EXT)) {
            if (handler.getHandleMethod().isAnnotationPresent(UserDefine.class)) {
                printAccessLog(request, null, startTime);
                return;
            }
            JsonResult jr = new JsonResult(result);
            outJson(jr, request, response, handler, startTime);
        }
    }

    private void outJson(JsonResult obj, final HttpServletRequest request, HttpServletResponse response, final MvcUriMapperHandler handler, long startTime) {
        String outMsg = BLANK;
        try {
            if (handler != null && handler.getHandleMethod().isAnnotationPresent(RequestMapping.class)) {
                Object data = obj.getData();

                if (data != null) {
                    // 方法头部加了 @ResponseBody
                    if (handler.getHandleMethod().isAnnotationPresent(ResponseBody.class)) {
                        outMsg = data.toString();
                    } else {
                        outMsg = CommonUtil.toJson(data);
                    }
                } else {
                    outMsg = BLANK;
                }
            } else {
                // 方法头部加了 @ResponseBody
                if (handler != null && handler.getHandleMethod().isAnnotationPresent(ResponseBody.class)) {
                    Object data = obj.getData();
                    outMsg = data.toString();
                } else {
                    outMsg = CommonUtil.toJson(obj);
                }
            }
            if (response != null) {
                response.setContentType(CONTENT_TYPE_JSON);
                response.getWriter().write(outMsg);
            }
            printAccessLog(request, outMsg, startTime);
        } catch (Exception e) {
            JsonResult jr = new JsonResult();
            jr.setMessage(e.getMessage());
            jr.setCode(ERROR_500);
            String s = CommonUtil.toJson(jr);
            try {
                response.getWriter().write(s);
            } catch (Exception e2) {
            }
            log.error("json输出出错：", e);
            printAccessLog(request, s, startTime);
        }
    }

    /**
     * 渲染页面
     *
     * @param htmlName  views下vm文件的路径，不包含 .vm 后缀
     * @param request
     * @param response
     * @param startTime
     */
    private void renderHtml(String htmlName, HttpServletRequest request, HttpServletResponse response, long startTime) {
        try {
            response.setContentType(CONTENT_TYPE_HTML);
            String htmlData = htmlCache.get(htmlName);
            if (htmlData == null) {
                String htmlPath = new StringBuilder(HTML_LOADER_ROOT).append(SPLIT).append(htmlName).append(HTML_EXT).toString();
                InputStream is = ConfigLoader.loadResource(htmlPath, false, false);
                if (is == null) {
                    return;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[5000];
                int len = -1;
                while ((len = is.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                is.close();
                htmlData = new String(bos.toByteArray(), "utf-8");
                htmlCache.put(htmlName, htmlData);
            }
            CharArrayWriter writer = new CharArrayWriter();
            writer.write(htmlData);
            response.getOutputStream().write(writer.toString().getBytes("utf-8"));
            printAccessLog(request, null, startTime);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 打印日志
     *
     * @param request
     * @param result
     * @param startTime
     */
    public static void printAccessLog(HttpServletRequest request, String result, long startTime) {
        if (request == null) {
            return;
        }
        // 请求参数日志打印
        Enumeration<String> en = request.getParameterNames();
        Map<String, String> param = new HashMap<String, String>();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            String[] values = request.getParameterValues(key);
            if (values.length <= 1) {
                param.put(key, values[0]);
            } else {
                param.put(key, CommonUtil.toJson(values));
            }
        }
        if (result == null) {
            result = BLANK;
        }
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(URI, request.getRequestURI());
        map.put(COST_TIME, String.valueOf(System.currentTimeMillis() - startTime));
        map.put(REMOTE_IP, CommonUtil.getIpAddr(request));
        map.put(PARAM, param);
        map.put(RESPONSE, result);
        log.info(new StringBuilder(LOG_HEAD).append(CommonUtil.toJson(map)).toString());
    }

    static ClassLoader ClassLoader = ClassHelper.getClassLoader();

    /**
     * 渲染静态文件
     *
     * @param uri
     * @param response
     */
    private void renderStaticFiles(String uri, HttpServletResponse response) {

        try {
            int index = uri.lastIndexOf(".");
            String mimeType = null;
            if (index != -1) {
                String fileExt = uri.substring(index + 1);
                mimeType = MimeTypes.getMimeType(fileExt);
            } else {
                mimeType = "text/plain";
            }
            if (uri.startsWith("/")) {
                uri = uri.substring(1, uri.length());
            }
            InputStream is = ClassLoader.getResourceAsStream(uri);
            if (is == null) {
                response.getWriter().println("404");
                return;
            }
            response.setHeader("Cache-Control", "max-age=3600, s-maxage=31536000");
            response.setContentType(mimeType);
            byte[] buf = new byte[1024];
            int len = 0;
            while ((len = is.read(buf)) != -1) {
                response.getOutputStream().write(buf, 0, len);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    /**
     * 初始化 mini-mvc
     */
    public static void initCfg() {

        if (!hasInit.compareAndSet(false, true)) {
            return;
        }
        context = SpringContextUtil.getContext();
        // 初始化 uri handler映射
        if (webBasePackage != null) {
            webBasePackage = webBasePackage.trim();
        }
        uriHandleMappedUtil = context.getAutowireCapableBeanFactory().createBean(MvcUriMapperUtil.class);
        if (contextPath != null) {
            uriHandleMappedUtil.setContextPath(contextPath);
        }
        uriHandleMappedUtil.setRootPackage(webBasePackage);
        uriHandleMappedUtil.detectHandlerByServlet();

        // 初始化 interceptors
        try {
            interceptors = (List<HandlerInterceptor>) context.getBean("mvcInterceptors");
        } catch (Exception e) {
            log.info(e.getMessage(), e);
        }
    }
}
