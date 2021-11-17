package com.yu.minimvc.exception;

/**
 * @author Administrator
 * @title: ycz
 * @projectName mini-mvc
 * @date 2021/11/10 0010下午 3:55
 */
public class LoginTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LoginTimeoutException() {
        super();
    }

    public LoginTimeoutException(String msg) {
        super(msg);
    }

}
