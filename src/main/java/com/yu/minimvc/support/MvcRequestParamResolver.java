package com.yu.minimvc.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yu.minimvc.common.CommonUtil;
import com.yu.minimvc.common.MimeTypes;
import com.yu.minimvc.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/22 0022下午 5:39
 */
public class MvcRequestParamResolver {
    private static Logger log = LoggerFactory.getLogger(MvcRequestParamResolver.class);
    private static MultipartResolver multipartResolver;

    /**
     * 解析req中的参数
     *
     * @param req
     * @return
     */
    protected static String getParamMap(HttpServletRequest req) {
        String out = null;
        if (MimeTypes.getMimeType("json").equals(req.getContentType())) {
            StringBuffer data = new StringBuffer();
            BufferedReader reader = null;
            try {
                reader = req.getReader();
                while (null != (out = reader.readLine()))
                    data.append(out);
            } catch (IOException e) {
                log.error("获取 reader 异常");
            }
            out = data.toString();
        } else {
            Map<String, Object> params = new HashMap<String, Object>();
            Enumeration<String> names = req.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                params.put(name, req.getParameter(name));
            }
            out = CommonUtil.toJson(params);
        }
        return out;
    }


    protected static Object resolveModelAttribute(MethodParameter methodParam, HttpServletRequest webRequest) throws Exception {
        Class<?> paramType = methodParam.getParameterType();
        Object instance = null;
        if (multipartResolver == null) {
            multipartResolver = SpringContextUtil.getContext().getBean(MultipartResolver.class);
        }
        if (multipartResolver != null && multipartResolver.isMultipart(webRequest)) {
            MultipartHttpServletRequest multipartHttpServletRequest = multipartResolver.resolveMultipart(webRequest);
            List<MultipartFile> multipartFiles = multipartHttpServletRequest.getFiles(methodParam.getParameterName());
            if (multipartFiles.isEmpty()) {
                throw new BizException("multipartResolver 无法解析到上传文件,检查请求字段与接口方法参数字段是否一致");
            }
            if (paramType.isArray()) {
                MultipartFile[] files = new MultipartFile[multipartFiles.size()];
                for (int i = 0; i < files.length; i++) {
                    files[i] = multipartFiles.get(i);
                }
                return files;
            } else if (List.class.isAssignableFrom(paramType)) {
                return multipartFiles;
            } else {
                return multipartFiles.get(multipartFiles.size() - 1);
            }
        }
        String paramMap = getParamMap(webRequest);
        if (paramType.isAssignableFrom(List.class)) {
            // 获取泛型中的类型
            Type type = methodParam.getNestedGenericParameterType();
            Type actualTypeArgument = ((ParameterizedType) type).getActualTypeArguments()[0];
            instance = JSONArray.parseArray(paramMap, (Class) actualTypeArgument);
        } else {
            instance = JSONObject.parseObject(paramMap, paramType);
        }
        return instance;
    }

    /**
     * 解析请求参数（一些基本类型包含map）
     *
     * @param methodParam
     * @param request
     * @return
     * @throws Exception
     */
    protected static Object resolveRequestParam(MethodParameter methodParam, HttpServletRequest request) throws Exception {
        Class<?> paramType = methodParam.getParameterType();
        if (Map.class.isAssignableFrom(paramType)) {
            return resolveRequestParamMap((Class<? extends Map>) paramType, request);
        }
        String requiredParameterName = getRequiredParameterName(methodParam);
        Object paramValue = null;
        String[] paramValues = request.getParameterValues(requiredParameterName);
        if (paramValues != null) {
            paramValue = (paramValues.length == 1 ? paramValues[0] : paramValues);
        }
        return paramValue;
    }


    private static String getRequiredParameterName(MethodParameter methodParam) {
        String name = methodParam.getParameterName();
        if (name == null) {
            throw new IllegalStateException("No parameter name specified for argument of type [" + methodParam.getParameterType().getName() + "], and no parameter name information found in class file either.");
        }
        return name;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map resolveRequestParamMap(Class<? extends Map> mapType, HttpServletRequest webRequest) {
        Map<String, String[]> parameterMap = webRequest.getParameterMap();
        if (MultiValueMap.class.isAssignableFrom(mapType)) {
            MultiValueMap<String, String> result = new LinkedMultiValueMap<String, String>(parameterMap.size());
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                for (String value : entry.getValue()) {
                    result.add(entry.getKey(), value);
                }
            }
            return result;
        } else {
            Map<String, String> result = new LinkedHashMap<String, String>(parameterMap.size());
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                if (entry.getValue().length > 0) {
                    result.put(entry.getKey(), entry.getValue()[0]);
                }
            }
            return result;
        }
    }
}
