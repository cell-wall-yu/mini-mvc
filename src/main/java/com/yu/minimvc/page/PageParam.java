package com.yu.minimvc.page;

public class PageParam {

    private Boolean openPage = false;

    /**
     * 每页数据
     */
    private Integer pageSize;

    /**
     * 目标页码
     */
    private Integer targetPage;

    /**
     * 排序类型 asc desc
     */
    private String orderByType;

    /**
     * 排序字段
     */
    private String orderByColumn;

    /**
     * 自定义排序sql， 一般是需要根据多个字段排序时使用
     */
    private String orderByClause;

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getTargetPage() {
        return targetPage;
    }

    public void setTargetPage(Integer targetPage) {
        this.targetPage = targetPage;
    }

    public Boolean getOpenPage() {
        return openPage;
    }

    public void setOpenPage(Boolean openPage) {
        this.openPage = openPage;
    }

    public String getOrderByType() {
        return orderByType;
    }

    public void setOrderByType(String orderByType) {
        this.orderByType = orderByType;
    }

    public String getOrderByColumn() {
        return orderByColumn;
    }

    public void setOrderByColumn(String orderByColumn) {
        this.orderByColumn = orderByColumn;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

}
