package com.yu.minimvc.domain;


import com.yu.minimvc.page.PageList;

public class JsonResult {

    /**
     * <pre>
     * 0:		操作成功
     * 404 	接口不存在
     * 500 	后台业务异常（已捕获）
     * 501		后台系统异常（未捕获）
     * 601 	未登陆
     * 602 	权限异常
     * </pre>
     */
    private Integer code = 0;

    // 返回的数据对象
    private Object data;

    // 错误信息
    private String message;

    public JsonResult() {
    }

    private final static String PageCLassName = PageList.class.getName();

    public JsonResult(Object result) {

        if (result != null) {

            String typeName = result.getClass().getTypeName();

            if (typeName.equals(PageCLassName)) {

                PageList<?> page = (PageList<?>) result;

                PageObject obj = new PageObject();
                obj.setRows(page);
                obj.setCurrentPage(page.getCurrentPage());
                obj.setHasNext(page.getHasNext());
                obj.setHasPre(page.getHasPre());
                obj.setPageSize(page.getPageSize());
                obj.setTotalPage(page.getTotalPage());
                obj.setTotalSize(page.getTotalSize());

                this.data = obj;
            } else if (typeName.equals("java.util.ArrayList") || typeName.endsWith("[]")) {

                PageObject obj = new PageObject();
                obj.setRows(result);

                this.data = obj;
            } else {
                this.data = result;
            }
        }
    }


    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
