package com.yu.minimvc.support;

import com.yu.minimvc.exception.BizException;
import org.springframework.beans.BeanUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebArgumentResolver;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/22 0022下午 5:29
 */
public class MvcHandlerResolver {

    private MvcHandlerBinder mvcHandlerBinder;

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
    protected Object invokeHandlerMethod(Method handlerMethod, Object handler, HttpServletRequest request, HttpServletResponse response) throws Exception {
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
    protected Object[] resolveHandlerArguments(Method handlerMethod, Object handler, HttpServletRequest request, HttpServletResponse response, ExtendedModelMap extendedModelMap) throws Exception {
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
                Object out = MvcRequestParamResolver.resolveRequestParam(methodParam, request);
                if (null == mvcHandlerBinder) {
                    mvcHandlerBinder = new MvcHandlerBinder();
                }
                WebDataBinder binder = mvcHandlerBinder.createBinder(out, null);
                mvcHandlerBinder.initBinder(binder);
                args[i] = binder.convertIfNecessary(out, methodParam.getParameterType());
            } else if (isAttr) {
                args[i] = MvcRequestParamResolver.resolveModelAttribute(methodParam, request);
                WebDataBinder binder = mvcHandlerBinder.createBinder(args[i], methodParam.getParameterName());
                mvcHandlerBinder.initBinder(binder);
                if (null != args[i]) {
                    mvcHandlerBinder.validateIfApplicable(binder, methodParam);
                }
                if (binder.getBindingResult().hasErrors() && mvcHandlerBinder.isBindExceptionRequired(binder, methodParam)) {
                    throw new BizException(binder.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
                }
            }
        }
        return args;
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
}
