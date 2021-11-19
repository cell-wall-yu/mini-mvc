package com.yu.minimvc.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yu.minimvc.common.CommonUtil;
import com.yu.minimvc.common.MimeTypes;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import com.yu.minimvc.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.autoconfigure.validation.ValidatorAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.*;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.*;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebArgumentResolver;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/9 0009下午 4:16
 * 处理控制层中生成对应的uri
 */
public class MvcUriMapperUtil implements ApplicationContextAware {
    private static Logger log = LoggerFactory.getLogger(MvcUriMapperUtil.class);

    // 包根路径
    private String rootPackage;
    // 后缀为.json
    public final String jsonSuffix = ".json";
    //存放对应的MvcUriMapperHandler
    private static Map<String, MvcUriMapperHandler> uriMap = new TreeMap<>();
    private boolean detectHandlersInAncestorContexts = false;
    private ApplicationContext applicationContext;
    private MultipartResolver multipartResolver;

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 加载容器中的bean生成对应的uri
     *
     * @param beanName
     */
    public void generateUriPath4MiniMvc(String beanName) {

        ApplicationContext context = getApplicationContext();
        Class<?> cls = context.getType(beanName);
        if (null == cls) {
            return;
        }
        if (cls.isAssignableFrom(ServletRegistrationBean.class)) {
            return;
        }
        Object bean = context.getBean(beanName);
        String clsName = cls.getName();
        // 判断bean是否包含rootPackage
        if (!clsName.startsWith(rootPackage)) {
            return;
        }
        // 判断bean是否包含符合 *.web.rpc.* 规则
        if (!clsName.matches(rootPackage + ".\\S+.web.rpc.\\S+")) {
            return;
        }
        // com.yu.module.moduleName.web.rpc.UserRPC.java
        int sp1 = rootPackage.length() + 1;
        int sp2 = clsName.indexOf(".", sp1 + 1);
        int sp3 = clsName.indexOf(".", sp2 + 1);
        String moduleName = clsName.substring(sp1, sp2);
        String layerName = clsName.substring(sp2 + 1, sp3);
        if (!layerName.equals("web")) {
            return;
        }
        int sp4 = clsName.indexOf(".", sp3 + 1);
        String webType = clsName.substring(sp3 + 1, sp4);

        if (webType.equals("rpc")) {
            Controller controller = cls.getDeclaredAnnotation(Controller.class);
            RestController restController = cls.getDeclaredAnnotation(RestController.class);
            String pathName = null;
            if (!StringUtils.isEmpty(controller) && !StringUtils.isEmpty(controller.value())) {
                pathName = controller.value();
            } else if (!StringUtils.isEmpty(restController) && !StringUtils.isEmpty(restController.value())) {
                pathName = restController.value();
            } else {
                pathName = clsName.substring(sp4 + 1);
            }
            Method[] ms = cls.getDeclaredMethods();
            for (int i = 0; i < ms.length; i++) {
                // 只扫描public方法
                if (!Modifier.isPublic(ms[i].getModifiers())) {
                    continue;
                }
                String methodName = ms[i].getName();
                if (methodName.indexOf("$") != -1) {
                    continue;
                }
                String uriName = new StringBuilder("/").append(moduleName).append("/").append(pathName.replace(".", "/")).append("/").append(methodName).append(jsonSuffix).toString();
                regUrlMapper(uriName, new MvcUriMapperHandler(uriName, bean, ms[i]));
            }
        }
    }

    private void regUrlMapper(String uri, MvcUriMapperHandler handler) {
        if (uriMap.containsKey(uri)) {
            log.error("uri 重复：{}", uri);
            throw new RuntimeException("[mvcUriMapperUtil] uri repeat " + uri);
        }
        uriMap.put(uri, handler);
        log.info("[mini-mvc] mapped uri " + uri + " for " + handler.getHandleObject().getClass().getName());
    }

    public void detectHandlerByServlet() {
        log.info("Looking for URL mappings in application context: " + getApplicationContext());

        // 获取所有的bean
        String[] beanNames = (this.detectHandlersInAncestorContexts ? BeanFactoryUtils.beanNamesForTypeIncludingAncestors(getApplicationContext(), Object.class) : getApplicationContext().getBeanNamesForType(Object.class));

        // 扫描 web.rpc @Controller or @ResController
        for (String beanName : beanNames) {
            try {
                generateUriPath4MiniMvc(beanName);
            } catch (Exception e) {
                log.error("request URI 初始化失败，包路径有误，beanName:" + beanName);
                log.error(e.getMessage(), e);
            }

        }
    }

    /**
     * 调用目标方法
     *
     * @param handlerMethod
     * @param handler
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public Object invokeHandlerMethod(Method handlerMethod, Object handler, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Method handlerMethodToInvoke = BridgeMethodResolver.findBridgedMethod(handlerMethod);
        Object[] args = resolveHandlerArguments(handlerMethodToInvoke, handler, request, response, null);
        ReflectionUtils.makeAccessible(handlerMethodToInvoke);
        return handlerMethodToInvoke.invoke(handler, args);
    }

    /**
     * 调用uriMappedHandler中的方法  解析参数
     *
     * @param handlerMethod
     * @param handler
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    private Object[] resolveHandlerArguments(Method handlerMethod, Object handler, HttpServletRequest request, HttpServletResponse response, ExtendedModelMap extendedModelMap) throws Exception {
        // 获取目标方法中的参数类型
        Class[] paramTypes = handlerMethod.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < args.length; i++) {
            MethodParameter methodParam = new MethodParameter(handlerMethod, i);
            methodParam.initParameterNameDiscovery(new LocalVariableTableParameterNameDiscoverer());
            GenericTypeResolver.resolveParameterType(methodParam, handler.getClass());

            boolean isParam = false;
            boolean isAttr = false;

            Object argValue = resolveCommonArgument(methodParam, request, response);

            if (argValue != WebArgumentResolver.UNRESOLVED) {
                args[i] = argValue;
            } else {
                Class paramType = methodParam.getParameterType();
                if (BeanUtils.isSimpleProperty(paramType)) {
                    isParam = true;
                } else {
                    isAttr = true;
                }
            }

            if (isParam) {
                Object out = resolveRequestParam(methodParam, request);
                WebDataBinder binder = createBinder(out, null);
                initBinder(binder);
                args[i] = binder.convertIfNecessary(out, methodParam.getParameterType());
            } else if (isAttr) {
                args[i] = resolveModelAttribute(methodParam, request);
                WebDataBinder binder = createBinder(args[i], methodParam.getParameterName());
                initBinder(binder);
                if (null != args[i]) {
                    validateIfApplicable(binder, methodParam);
                }
                if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, methodParam)) {
                    throw new BizException(binder.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
                }
            }
        }
        return args;
    }

    /**
     * 是否绑定必要的异常
     *
     * @param binder
     * @param methodParam
     * @return
     */
    private boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter methodParam) {
        int i = methodParam.getParameterIndex();
        Class<?>[] paramTypes = methodParam.getMethod().getParameterTypes();
        boolean hasBindingResult = paramTypes.length > i + 1 && Errors.class.isAssignableFrom(paramTypes[i + 1]);
        return !hasBindingResult;
    }

    /**
     * 解析请求参数（一些基本类型包含map）
     *
     * @param methodParam
     * @param request
     * @return
     * @throws Exception
     */
    private Object resolveRequestParam(MethodParameter methodParam, HttpServletRequest request) throws Exception {
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

    /**
     * 解析参数中是否包含HttpServletRequest，HttpServletResponse，HttpServletSession
     *
     * @param methodParameter
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    protected Object resolveCommonArgument(MethodParameter methodParameter, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Class paramType = methodParameter.getParameterType();
        Object value;
        if (ServletRequest.class.isAssignableFrom(paramType)) {
            value = request;
        } else if (ServletResponse.class.isAssignableFrom(paramType)) {
            value = response;
        } else if (HttpSession.class.isAssignableFrom(paramType)) {
            value = request.getSession();
        } else {
            value = WebArgumentResolver.UNRESOLVED;
        }
        if (value != WebArgumentResolver.UNRESOLVED && !ClassUtils.isAssignableValue(paramType, value)) {
            throw new IllegalStateException("Standard argument type [" + paramType.getName() + "] resolved to incompatible value of type [" + (value != null ? value.getClass() : null) + "]. Consider declaring the argument type in a less specific fashion.");
        }
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map resolveRequestParamMap(Class<? extends Map> mapType, HttpServletRequest webRequest) {
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

    private String getRequiredParameterName(MethodParameter methodParam) {
        String name = methodParam.getParameterName();
        if (name == null) {
            throw new IllegalStateException("No parameter name specified for argument of type [" + methodParam.getParameterType().getName() + "], and no parameter name information found in class file either.");
        }
        return name;
    }

    private Object resolveModelAttribute(MethodParameter methodParam, HttpServletRequest webRequest) throws Exception {
        Class<?> paramType = methodParam.getParameterType();
        Object instance = null;
        if (multipartResolver == null) {
            multipartResolver = applicationContext.getBean(MultipartResolver.class);
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
     * 校验参数
     *
     * @param binder
     * @param methodParam
     */
    private void validateIfApplicable(WebDataBinder binder, MethodParameter methodParam) {
        Annotation[] annotations = methodParam.getParameterAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            Annotation ann = annotations[i];
            Validated validatedAnn = (Validated) AnnotationUtils.getAnnotation(ann, Validated.class);
            if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                Object hints = validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann);
                Object[] validationHints = hints instanceof Object[] ? (Object[]) ((Object[]) hints) : new Object[]{hints};
                binder.validate(validationHints);
                break;
            }
        }
    }

    protected void initBinder(WebDataBinder binder) throws Exception {
        // 绑定类型转换配置类
        FormattingConversionServiceFactoryBean conversionServiceBean = getApplicationContext().getBean(FormattingConversionServiceFactoryBean.class);
        if (null != conversionServiceBean) {
            binder.setConversionService(conversionServiceBean.getObject());
        }
        // 绑定ValidatorAdapter用注解校验参数
        ValidatorAdapter validatorAdapter = getApplicationContext().getBean(ValidatorAdapter.class);
        if (binder.getTarget() != null && validatorAdapter.supports(binder.getTarget().getClass())) {
            binder.setValidator(validatorAdapter);
        }
        SpringValidatorAdapter springValidatorAdapter = getApplicationContext().getBean(SpringValidatorAdapter.class);
        if (binder.getTarget() != null && springValidatorAdapter.supports(binder.getTarget().getClass())) {
            binder.setValidator(springValidatorAdapter);
        }

    }

    protected WebDataBinder createBinder(Object target, String objectName) throws Exception {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(target, objectName);
        return binder;
    }

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

    public MvcUriMapperHandler getMappedHandler(String uri) {
        return uriMap.get(uri);
    }

    public Map<String, MvcUriMapperHandler> getUriMap() {
        return uriMap;
    }


    public void setRootPackage(String webBasePackage) {
        this.rootPackage = webBasePackage;
    }

    private String contextPath;

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }
}
