package com.yu.minimvc.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/9 0009下午 4:19
 */
public class MvcUriMapperHandler {
    private static Logger log = LoggerFactory.getLogger(MvcUriMapperHandler.class);

    public class MvcUriMethod {
        private String uri;
        private Object handleObject;
        private Method handleMethod;

        public MvcUriMethod(String uri, Object handleObject, Method handleMethod) {
            super();
            if (handleMethod == null) {
                throw new RuntimeException("handleMethod is null");
            }
            this.uri = uri;
            this.handleObject = handleObject;
            this.handleMethod = handleMethod;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public Object getHandleObject() {
            return handleObject;
        }

        public void setHandleObject(Object handleObject) {
            this.handleObject = handleObject;
        }

        public Method getHandleMethod() {
            return handleMethod;
        }

        public void setHandleMethod(Method handleMethod) {
            this.handleMethod = handleMethod;
        }

    }

    private String rootPackage;
    private final String jsonSuffix = ".json";
    /**
     * 存放uri对应的MvcUriMethod对象
     */
    private static Map<String, MvcUriMethod> uriMap = new TreeMap<>();
    private boolean detectHandlersInAncestorContexts = false;

    private ApplicationContext getApplicationContext() {
        detectHandlersInAncestorContexts = true;
        return SpringContextUtil.getContext();
    }

    /**
     * 加载容器中的bean生成对应的uri
     *
     * @param beanName
     */
    private void generateUriPath4MiniMvc(String beanName) {

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
                String uriName = new StringBuilder(contextPath + "/").append(moduleName).append("/").append(pathName.replace(".", "/")).append("/").append(methodName).append(jsonSuffix).toString();
                regUrlMapper(uriName, new MvcUriMethod(uriName, bean, ms[i]));
            }
        }
    }

    private void regUrlMapper(String uri, MvcUriMethod handler) {
        if (uriMap.containsKey(uri)) {
            log.error("uri 重复：{}", uri);
            throw new RuntimeException("[mvcUriMapperUtil] uri repeat " + uri);
        }
        uriMap.put(uri, handler);
        log.info("[mini-mvc] mapped uri " + uri + " for " + handler.getHandleObject().getClass().getName());
    }

    protected void detectHandlerByServlet() {
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

    public MvcUriMethod getMappedHandler(String uri) {
        return uriMap.get(uri);
    }

    public Map<String, MvcUriMethod> getUriMap() {
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
