package com.yu.minimvc.support;

import com.yu.minimvc.annotation.UserDefine;
import com.yu.minimvc.common.CommonUtil;
import com.yu.minimvc.domain.JsonResult;
import com.yu.minimvc.exception.BizException;
import com.yu.minimvc.exception.LoginTimeoutException;
import com.yu.minimvc.load.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndViewDefiningException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/22 0022下午 4:23
 */
public class MvcRenderViewResolver {
    private static Logger log = LoggerFactory.getLogger(MvcRenderViewResolver.class);

    private static Map<String, String> htmlCache = new ConcurrentHashMap<>();
    /**
     * 页面路径相关
     */
    private static final String SCREEN = "screen";
    private static final String LOGIN_PAGE = "/login.html";
    private static final String N = "\n";
    private static final String SPLIT = "/";
    private static final String INDEX = "index";
    private static final String ERRORS = "errors";

    private static final String HTML_EXT = ".html";
    private static final String JSON_EXT = ".json";

    /**
     * html 的物理路径
     */
    public static String HTML_LOADER_ROOT = "views";

    public static final String ERROR_MESSAGE = "error_message";

    public static final String ERROR_NOT_FOUND = "resource not found";

    /**
     * content type相关
     */
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";

    private static final String BLANK = "";
    /**
     * 错误相关 code
     */
    public static final int ERROR_404 = 404;
    public static final int ERROR_500 = 500;
    public static final int ERROR_501 = 501;
    public static final int ERROR_601 = 601;
    private static final String LOG_HEAD = "[mini-mvc] response message:";
    /**
     * 日志打印相关
     */
    private static final String URI = "uri";
    private static final String COST_TIME = "costTime";
    private static final String REMOTE_IP = "remoteIp";
    private static final String PARAM = "param";
    private static final String RESPONSE = "response";

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
    protected void renderSuccessView(String htmlName, Object result, MvcUriMapperHandler.MvcUriMethod handler, HttpServletRequest request, HttpServletResponse response, long startTime) throws Exception {
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

    /**
     * 渲染页面
     *
     * @param htmlName  views下vm文件的路径，不包含 .vm 后缀
     * @param request
     * @param response
     * @param startTime
     */
    protected void renderHtml(String htmlName, HttpServletRequest request, HttpServletResponse response, long startTime) {
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

    protected String renderLogin(String uri, HttpServletRequest req) throws ServletException {
        String htmlName = null;
        String path = SCREEN + uri.substring(0, uri.length() - HTML_EXT.length());
        String htmlPath = new StringBuilder(MvcRenderViewResolver.HTML_LOADER_ROOT).append(SPLIT).append(path).append(HTML_EXT).toString();
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
        return htmlName;
    }

    /**
     * 打印日志
     *
     * @param request
     * @param result
     * @param startTime
     */
    protected void printAccessLog(HttpServletRequest request, String result, long startTime) {
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

    protected void outJson(JsonResult obj, final HttpServletRequest request, HttpServletResponse response, final MvcUriMapperHandler.MvcUriMethod handler, long startTime) {
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
     * 渲染错误页面
     *
     * @param request
     * @param response
     * @param ex
     * @param hasHandler
     * @param startTime
     */
    protected void renderExceptionView(HttpServletRequest request, HttpServletResponse response, Throwable ex, Boolean hasHandler, long startTime) {
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
}
