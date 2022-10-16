package com.xxl.job.core.handler.impl;

import com.xxl.job.core.handler.IJobHandler;

import java.lang.reflect.Method;

/**
 *  bean  方法的处理器，就是 执行方法的处理器
 * @author jing
 */
public class MethodJobHandler extends IJobHandler {

    private final Object target;  // 类对象，也就是 bean 对象
    private final Method method; // 类里面的某一个  方法的  对象
    private Method initMethod;  // 初始化的方法对象
    private Method destroyMethod;//  销毁的方法对象

    public MethodJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;

        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }


    /**
     *  执行当前方法
     * @author jing
     */
    @Override
    public void execute() throws Exception {
//        获取 当前 方法的  参数类型
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0) {
//            方法参数不能是基本类型
            // 反射调用  被 xxlJob 标注的目标方法
            // method 是目标方法，target 是目标方法所在的bean
            method.invoke(target, new Object[paramTypes.length]);       // method-param can not be primitive-types
        } else {
            method.invoke(target);
        }
    }
    /**
     *   先执行 一个方法
     * @author jing
     */
    @Override
    public void init() throws Exception {
        if(initMethod != null) {
            initMethod.invoke(target);
        }
    }

    /**
     *   再执行后一个方法
     * @author jing
     */
    @Override
    public void destroy() throws Exception {
        if(destroyMethod != null) {
            destroyMethod.invoke(target);
        }
    }

    @Override
    public String toString() {
        return super.toString()+"["+ target.getClass() + "#" + method.getName() +"]";
    }
}
