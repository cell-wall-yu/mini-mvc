package com.yu.minimvc.exception;

import com.yu.minimvc.common.CommonUtil;
import org.slf4j.helpers.MessageFormatter;

import java.io.Serializable;
import java.util.Map;


public class BizException extends RuntimeException implements Serializable {

	private static final long serialVersionUID = 1L;

	private Object errorData;

	public BizException() {
		super();
	}

	public BizException(String errorMsg) {
		super(errorMsg);
	}

	public BizException(String format, String... argArray) {
		super(MessageFormatter.arrayFormat(format, argArray).getMessage());
	}

	public BizException(Object errorMsg) {
		super(errorMsg != null ? errorMsg.toString() : "");
	}

	public BizException(String errorMsg, Map<String, Object> errorMap) {
		super(errorMsg);
		this.errorData = errorMap;
	}

	public BizException(RuntimeException e) {
		super(e);
	}

	public BizException(Throwable e) {
		super(e);
	}

	public Object getErrorData() {
		return errorData;
	}

	@Override
	public String getMessage() {
		String message = super.getMessage();
		if (message != null) {
			message = CommonUtil.replaceAll(message, "com.haigui.framework.askja.exception.BizException:", "");
		}
		return message;
	}
}
