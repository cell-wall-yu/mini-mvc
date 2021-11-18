package com.yu.minimvc.support;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/9 0009下午 5:40
 */
public class SpringContextUtil {
    private static ApplicationContext context;

    /**
     * 获取 context 对象
     *
     * @return
     */
    public static ApplicationContext getContext() {
        return context;
    }


    public static void setContext(ApplicationContext context) {
        SpringContextUtil.context = context;
    }

    @SuppressWarnings("unchecked")
    public synchronized static <T> T getBean(String beanName) {
        return (T) context.getBean(beanName);
    }

    public static <T> T getBean(Class<T> class1) {
        return context.getBean(class1);
    }

    public static <T> T getProperties(String property, T defaultValue, Class<T> requiredType) {
        T result = defaultValue;
        try {
            result = getBean(Environment.class).getProperty(property, requiredType);
        } catch (Exception ignored) {
        }
        return result;
    }

    public static String getProperties(String property) {
        return getProperties(property, null, String.class);
    }

    public static Method getHandlerMethod(HttpServletRequest request) {
        return (Method) request.getAttribute(MiniMvcServletFast.SPRING_CONTROLLER_INVOKE_METHOD);
    }
}
