package com.yu.minimvc;

import com.yu.minimvc.converter.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Administrator
 * @title: ycz
 * 将入参数转化对应的类型
 * @projectName mini-mvc
 * @date 2021/11/10 0010下午 5:41
 */
@Configuration
public class MiniMvcConfig {

    @Bean
    public FormattingConversionServiceFactoryBean conversionServiceFactoryBean() {
        FormattingConversionServiceFactoryBean bean = new FormattingConversionServiceFactoryBean();
        Set<Converter> converters = new HashSet<>();
        converters.add(new String2BigDecimalConverter());
        converters.add(new String2DoubleConverter());
        converters.add(new String2IntegerConverter());
        converters.add(new String2LongConverter());
        converters.add(new String2NumberConverter());
        String2DateConverter string2DateConverter = new String2DateConverter();
        string2DateConverter.setFormats(Arrays.asList("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd"));
        string2DateConverter.init();
        converters.add(string2DateConverter);
        bean.setConverters(converters);
        return bean;
    }
}
