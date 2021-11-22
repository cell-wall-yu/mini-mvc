package com.yu.minimvc.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yu.minimvc.exception.BizException;
import org.springframework.boot.autoconfigure.validation.ValidatorAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/22 0022下午 5:35
 */
public class MvcHandlerBinder {

    private ServletRequestDataBinder binder;

    protected WebDataBinder createBinder(Object target, String objectName) throws Exception {
        if (null == binder) {
            binder = new ServletRequestDataBinder(target, objectName);
        }
        return binder;
    }

    protected void initBinder(WebDataBinder binder) throws Exception {
        // 绑定类型转换配置类
        FormattingConversionServiceFactoryBean conversionServiceBean = SpringContextUtil.getContext().getBean(FormattingConversionServiceFactoryBean.class);
        if (null != conversionServiceBean) {
            binder.setConversionService(conversionServiceBean.getObject());
        }
        // 绑定ValidatorAdapter用注解校验参数
        ValidatorAdapter validatorAdapter = SpringContextUtil.getContext().getBean(ValidatorAdapter.class);
        if (binder.getTarget() != null && validatorAdapter.supports(binder.getTarget().getClass())) {
            binder.setValidator(validatorAdapter);
        }
        SpringValidatorAdapter springValidatorAdapter = SpringContextUtil.getContext().getBean(SpringValidatorAdapter.class);
        if (binder.getTarget() != null && springValidatorAdapter.supports(binder.getTarget().getClass())) {
            binder.setValidator(springValidatorAdapter);
        }
    }



    /**
     * 校验参数
     *
     * @param binder
     * @param methodParam
     */
    protected void validateIfApplicable(WebDataBinder binder, MethodParameter methodParam) {
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

    /**
     * 是否绑定必要的异常
     *
     * @param binder
     * @param methodParam
     * @return
     */
    protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter methodParam) {
        int i = methodParam.getParameterIndex();
        Class<?>[] paramTypes = methodParam.getMethod().getParameterTypes();
        boolean hasBindingResult = paramTypes.length > i + 1 && Errors.class.isAssignableFrom(paramTypes[i + 1]);
        return !hasBindingResult;
    }
}
