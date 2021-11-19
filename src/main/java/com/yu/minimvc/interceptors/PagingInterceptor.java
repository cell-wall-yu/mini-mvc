package com.yu.minimvc.interceptors;

import com.yu.minimvc.common.CommonUtil;
import com.yu.minimvc.page.PageParam;
import com.yu.minimvc.page.ThreadPagingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static Logger log = LoggerFactory.getLogger(PagingInterceptor.class);
    private final String DESC = "DESC";
    private final String ASC = "ASC";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) throws Exception {
        String page = request.getParameter("page");
        String size = request.getParameter("size");
        String order_by = request.getParameter("order_by");

        if (page != null && size != null && !page.equals("") && !size.equals("")) {
            try {
                PageParam pageParam = new PageParam();
                pageParam.setTargetPage(Integer.valueOf(page));
                pageParam.setPageSize(Integer.valueOf(size));

                if (order_by != null && !order_by.matches("\\s*")) {
                    order_by = order_by.trim();
                    if (order_by.charAt(0) == '-' && order_by.length() >= 2) {
                        order_by = order_by.substring(1);
                        String column = CommonUtil.convertJavaField2DB(order_by);
                        pageParam.setOrderByColumn(column);
                        pageParam.setOrderByType(DESC);
                    } else {
                        String column = CommonUtil.convertJavaField2DB(order_by);
                        pageParam.setOrderByColumn(column);
                        pageParam.setOrderByType(ASC);
                    }
                }

                ThreadPagingUtil.set(pageParam);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
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
