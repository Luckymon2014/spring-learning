package com.springdemo.demo.service.impl;

import com.springdemo.demo.service.DemoService;
import com.springdemo.webmvc.annotation.SpService;

/**
 * Created by SHANXIAO on 2018/07/21.
 */
@SpService("demoService")
public class DemoServiceImpl implements DemoService {

    public String get(String name) {
        return "My name is " + name;
    }
}
