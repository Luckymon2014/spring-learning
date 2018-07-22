package com.springdemo.webmvc.annotation;

import java.lang.annotation.*;

/**
 * Created by SHANXIAO on 2018/07/21.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpRequestMapping {
    String value() default "";
}
