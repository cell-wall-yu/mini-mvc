package com.yu.minimvc.converter;

import java.math.BigDecimal;

import org.springframework.core.convert.converter.Converter;

public class String2BigDecimalConverter implements Converter<String, BigDecimal> {

	public BigDecimal convert(String arg0) {
		try {
			return BigDecimal.valueOf(Double.valueOf(arg0));
		} catch (Exception e) {
		}
		return null;
	}

}
