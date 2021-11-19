package com.yu.minimvc.utils;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ycz
 * @version 1.0.0
 * @date 2021/6/16 0016 下午 5:06
 */
public abstract class ThreadPagingUtil {
    private static Logger log = LoggerFactory.getLogger(ThreadPagingUtil.class);

    private static ThreadLocal<Page> local = new ThreadLocal();

    /**
     * 获取当前分页对象
     *
     * @return
     */
    public static Page get() {
        return local.get();
    }

    /**
     * 开启分页，线程内有效
     */
    public static void turnOn() {
        Page page = local.get();
        if (page == null) {
            page = PageHelper.startPage(1, 10);
            local.set(page);
        }
    }

    /**
     * 设置分页参数
     *
     * @param Page
     */
    public static void set(Page Page) {
        local.set(Page);
    }

    /**
     * 清除
     */
    public static void clear() {
        local.remove();
    }
}
