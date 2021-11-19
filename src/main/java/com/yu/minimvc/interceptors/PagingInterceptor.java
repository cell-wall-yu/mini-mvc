package com.yu.minimvc.interceptors;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.yu.minimvc.utils.ThreadPagingUtil;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author ycz
 * @version 1.0.0
 * @date 2021/5/21 0021 下午 4:49
 */
public class PagingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) throws Exception {
        /**
         * 每页大小
         */
        String pageSize = request.getParameter("page_size");
        /**
         * 第几页
         */
        String targetPage = request.getParameter("target_page");
        if (null != targetPage && null != pageSize) {
            Page<Object> page = PageHelper.startPage(Integer.parseInt(targetPage), Integer.parseInt(pageSize));
            ThreadPagingUtil.set(page);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
        ThreadPagingUtil.clear();
    }
}
