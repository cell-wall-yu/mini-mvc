package com.yu.minimvc.support;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/9 0009下午 4:19
 */
public class MvcUriMapperHandler {
    private String uri;

    private Boolean isRewrite = false;

    private Object handleObject;

    private Method handleMethod;

    private List<String> fieldNames;

    public MvcUriMapperHandler(String uri, Object handleObject, Method handleMethod) {
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

    public Boolean getRewrite() {
        return isRewrite;
    }

    public void setRewrite(Boolean rewrite) {
        isRewrite = rewrite;
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

    public List<String> getFieldNames() {
        return fieldNames;
    }

    public void setFieldNames(List<String> fieldNames) {
        this.fieldNames = fieldNames;
    }
}
