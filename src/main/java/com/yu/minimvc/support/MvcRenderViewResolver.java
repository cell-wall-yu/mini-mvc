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
    private static final String JSON_EXT = ".json";

    public static final String ERROR_NOT_FOUND = "resource not found";

    /**
     * content type相关
     */
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

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
     * @param result
     * @param handler
     * @param request
     * @param response
     * @param startTime
     * @throws Exception
     */
    protected void renderSuccessView(Object result, MvcUriMapperHandler.MvcUriMethod handler, HttpServletRequest request, HttpServletResponse response, long startTime) throws Exception {
        String uri = request.getRequestURI();
        uri = uri.substring(request.getContextPath().length());
        // json 格式请求
        if (uri.endsWith(JSON_EXT)) {
            if (handler.getHandleMethod().isAnnotationPresent(UserDefine.class)) {
                printAccessLog(request, null, startTime);
                return;
            }
            JsonResult jr = new JsonResult(result);
            outJson(jr, request, response, handler, startTime);
        }
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
            // write error json
            if (uri.endsWith(JSON_EXT)) {
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
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
