package com.springdemo.webmvc.annotation;

import java.lang.annotation.*;

/**
 * Created by SHANXIAO on 2018/07/21.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpService {
    String value() default "";
}
