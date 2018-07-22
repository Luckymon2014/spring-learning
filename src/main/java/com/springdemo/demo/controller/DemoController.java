package com.springdemo.demo.controller;

import com.springdemo.demo.service.DemoService;
import com.springdemo.webmvc.annotation.SpAutowired;
import com.springdemo.webmvc.annotation.SpController;
import com.springdemo.webmvc.annotation.SpRequestMapping;
import com.springdemo.webmvc.annotation.SpRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by SHANXIAO on 2018/07/21.
 */

@SpController
@SpRequestMapping("/demo")
public class DemoController {

    @SpAutowired("demoService")
    private DemoService demoService;

    @SpRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,
                      @SpRequestParam("name") String name) {
        String result = demoService.get(name);
        try{
            response.getWriter().write(result);
        } catch (IOException e)  {
            e.printStackTrace();
        }
    }
}
