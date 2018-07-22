package com.springdemo.webmvc.servlet;

import com.springdemo.webmvc.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by SHANXIAO on 2018/07/21.
 */
public class SpDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

//    private Map<String, Method> handlerMapping = new HashMap<>();
    private List<Handler> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 6. 运行阶段
        // 调用入参为request/response的方法
        // 从HandlerMapping找到URL对应的Method
        // 通过反射机制invoker调用方法
        // 将方法的结果通过controller输出到浏览器
        try {
            doDispatcher(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception: <br/>" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {

//        // 获取url
//        String url = req.getRequestURI();
//        String contextPath = req.getContextPath();
//        url = url.replaceAll(contextPath, "").replaceAll("/+", "");
//
//        if (!handlerMapping.containsKey(url)) {
//            // 没有匹配上，返回404
//            resp.getWriter().write("404 Not Found");
//            return;
//        }
//
//        Method method = this.handlerMapping.get(url);
//        Map<String, String[]> params = req.getParameterMap();
//        String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
////        method.invoke(ioc.get(beanName), );
//        System.out.println(method);

        try {
            Handler handler = getHandler(req);

            if (handler == null) {
                // 没有匹配上，返回404
                resp.getWriter().write("404 Not Found");
                return;
            }

            // 获取方法的参数列表
            Class<?>[] paramTypes = handler.method.getParameterTypes();

            // 保存所有需要自动赋值的参数值
            Object[] paramValues = new Object[paramTypes.length];

            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                // 如果找到匹配的对象，则开始填充参数值
                if (!handler.paramIndexMapping.containsKey(param.getKey()))
                    continue;
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index], value);
            }

            // 设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[reqIndex] = req;
            paramValues[respIndex] = resp;

            handler.method.invoke(handler.controller, paramValues);

        } catch (Exception e) {
            throw e;
        }
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty())
            return null;

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            try {
                Matcher matcher = handler.pattern.matcher(url);
                if (!matcher.matches())
                    continue;
                return  handler;
            } catch (Exception e) {
                throw e;
            }
        }

        return null;
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type)
            return Integer.valueOf(value);
        return value;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1. 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2. 扫描到所有相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        // 3. 初始化刚刚扫描到的类，并将其存入到IOC容器中
        doInstance();
        // 4. DI
        doAutowired();
        // 5. 初始化HandlerMapping
        initHandlerMapping();

        System.out.println("SpringMVC is init!");
    }

    /**
     * URL-Method
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty())
            return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            // 只需读取controller中用到的RequestMapping
            if (clazz.isAnnotationPresent(SpController.class)) {
                // RequestMapping的url
                String baseUrl = "";
                if (clazz.isAnnotationPresent(SpRequestMapping.class)) {
                    SpRequestMapping requestMapping = clazz.getAnnotation(SpRequestMapping.class);
                    baseUrl = requestMapping.value();
                }
                // Spring只认public方法
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    //
                    if (method.isAnnotationPresent(SpRequestMapping.class)) {
                        SpRequestMapping requestMapping = method.getAnnotation(SpRequestMapping.class);
//                        String url = baseUrl + "/" + requestMapping.value();
//                        // 替换多个斜杠
//                        url = url.replaceAll("/+", "/");
//                        // 存入handlerMapping
//                        handlerMapping.put(url, method);
                        String regex = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                        Pattern pattern = Pattern.compile(regex);
                        handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                    } else
                        continue;
                }
            } else
                // 其他属性（非controller）直接忽略
                continue;
        }
    }

    /**
     * 依赖注入
     */
    private void doAutowired() {
        if (ioc.isEmpty())
            return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 注入：自动给属性赋值
            // 只需要注入加了Autowired注解的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(SpAutowired.class)) {
                    // 1. 如果是自定义beanName，优先使用
                    SpAutowired autowired = field.getAnnotation(SpAutowired.class);
                    String beanName = autowired.value();
                    if ("".equals(beanName.trim()))
                        // 2. 没有自定义beanName，默认的属性名首字母小写
                        beanName = field.getType().getName();
                    // 赋值
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        continue;
                    }
                } else
                    // 其他属性（非Autowired）直接忽略
                    continue;
            }
        }
    }

    /**
     * IOC容器初始化
     */
    private void doInstance() {
        if (classNames.isEmpty())
            return;
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                // 利用反射机制，把这个对象给造出来
                // 只需要实例化加了注解的类
                if (clazz.isAnnotationPresent(SpController.class)) {
                    Object instance = clazz.newInstance();
                    // 类名的首字母小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    // 存入ioc容器
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(SpService.class)) {
                    // 1. 如果是自定义beanName，优先使用
                    SpService service = clazz.getAnnotation(SpService.class);
                    String beanName = service.value();
                    if("".equals(beanName.trim()))
                        // 2. 没有自定义beanName，默认的类名首字母小写
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    // 3. 如果写的是接口，无法实例化，自动注入它的实现类
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), instance);
                    }
                } else
                    // 其他类（非controller或service）直接忽略
                    continue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描相关的类
     * @param scanPackage 配置文件中读取到的需要扫描的类
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPathDir = new File(url.getFile());
        for (File file : classPathDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * 加载配置文件
     * @param contextConfigLocation web.xml中规定的配置文件名
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is)
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 记录controller中的RequestMapping和Method的对应关系
     * 内部类
     */
    private class Handler {
        protected Object controller;    // 保存方法对应的实例
        protected Method method;        // 保存映射的方法
        protected Pattern pattern;      // 正则解析后的url
        protected Map<String, Integer> paramIndexMapping;   // 参数顺序

        /**
         * 构造一个Handler的基本参数
         * @param pattern
         * @param controller
         * @param method
         */
        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            // 提取方法中加了注解的参数
            Annotation[][] annotation = method.getParameterAnnotations();
            for (int i = 0; i < annotation.length; i++) {
                for (Annotation a : annotation[i]) {
                    if (a instanceof SpRequestParam) {
                        String paramName = ((SpRequestParam) a).value();
                        if (!"".equals(paramName.trim()))
                            paramIndexMapping.put(paramName, i);
                    }
                }
            }
            // 提取方法中的request和response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class)
                    paramIndexMapping.put(type.getName(), i);
            }
        }
    }

}
