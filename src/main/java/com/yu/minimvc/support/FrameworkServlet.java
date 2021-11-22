package com.yu.minimvc.support;

import com.yu.minimvc.common.MimeTypes;
import com.yu.minimvc.load.ClassHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HttpServletBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/22 0022下午 2:47
 */
public abstract class FrameworkServlet extends HttpServletBean {
    private static Logger log = LoggerFactory.getLogger(FrameworkServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.processRequest(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.processRequest(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.processRequest(req, resp);
    }

    protected abstract void doService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;


    protected final void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        request.getRequestDispatcher("");
        log.info("[mini-mvc] request uri:{}", uri);
        if (uri.equals("/favicon.ico")) {
            response.sendError(404);
            return;
        }

        if (uri.startsWith("/statics")) {
            renderStaticFiles(uri, response);
            return;
        }
        doService(request, response);
    }

    /**
     * 渲染静态文件
     *
     * @param uri
     * @param response
     */
    protected void renderStaticFiles(String uri, HttpServletResponse response) {
        try {
            int index = uri.lastIndexOf(".");
            String mimeType = null;
            if (index != -1) {
                String fileExt = uri.substring(index + 1);
                mimeType = MimeTypes.getMimeType(fileExt);
            } else {
                mimeType = "text/plain";
            }
            if (uri.startsWith("/")) {
                uri = uri.substring(1, uri.length());
            }
            InputStream is = ClassHelper.getClassLoader().getResourceAsStream(uri);
            if (is == null) {
                response.getWriter().println("404");
                return;
            }
            response.setHeader("Cache-Control", "max-age=3600, s-maxage=31536000");
            response.setContentType(mimeType);
            byte[] buf = new byte[1024];
            int len = 0;
            while ((len = is.read(buf)) != -1) {
                response.getOutputStream().write(buf, 0, len);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

}
