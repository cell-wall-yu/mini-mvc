package com.yu.minimvc.interceptors;

import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/10 0010下午 5:00
 */
public class MiniMvcInterceptors {

    private final List<HandlerInterceptor> interceptors = new ArrayList<>();

    public List<HandlerInterceptor> getInterceptors() {
        return interceptors;
    }

    public void addInterceptor(HandlerInterceptor handlerInterceptor) {
        interceptors.add(handlerInterceptor);
    }
}
