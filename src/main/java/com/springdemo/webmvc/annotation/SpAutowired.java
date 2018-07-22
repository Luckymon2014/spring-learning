package com.springdemo.webmvc.annotation;

import java.lang.annotation.*;

/**
 * Created by SHANXIAO on 2018/07/21.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpAutowired {
    String value() default "";
}
