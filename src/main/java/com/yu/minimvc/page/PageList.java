package com.yu.minimvc.page;

import java.util.ArrayList;
import java.util.Collection;

public class PageList<T> extends ArrayList<T> {

	private static final long serialVersionUID = 1L;

	private Integer totalSize;

	private Boolean hasPre;

	private Boolean hasNext;

	private Integer currentPage;

	private Integer totalPage;

	private Integer pageSize;
	
	public PageList(PageList<T> list) {
		
	}
	
	public PageList() {
		super();
	}

	public PageList(int size) {
		super(size);
	}

	public PageList(Collection<? extends T> c) {
		super(c);
	}

	public Integer getTotalSize() {
		return totalSize;
	}

	public void setTotalSize(Integer totalSize) {
		this.totalSize = totalSize;
	}

	public Boolean getHasPre() {
		return hasPre;
	}

	public void setHasPre(Boolean hasPre) {
		this.hasPre = hasPre;
	}

	public Boolean getHasNext() {
		return hasNext;
	}

	public void setHasNext(Boolean hasNext) {
		this.hasNext = hasNext;
	}

	public Integer getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(Integer currentPage) {
		this.currentPage = currentPage;
	}

	public Integer getTotalPage() {
		return totalPage;
	}

	public void setTotalPage(Integer totalPage) {
		this.totalPage = totalPage;
	}

	public Integer getPageSize() {
		return pageSize;
	}

	public void setPageSize(Integer pageSize) {
		this.pageSize = pageSize;
	}

}
