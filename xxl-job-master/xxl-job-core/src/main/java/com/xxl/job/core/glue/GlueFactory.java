package com.xxl.job.core.glue;

import com.xxl.job.core.glue.impl.SpringGlueFactory;
import com.xxl.job.core.handler.IJobHandler;
import groovy.lang.GroovyClassLoader;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * glue factory, product class/object by name
 *
 * 运行模式工厂，一般是通过名称  创建对象
 *
 * @author jing
 */
public class GlueFactory {


//	创建静态对象
	private static GlueFactory glueFactory = new GlueFactory();

//	通过静态  获取当前对象
	public static GlueFactory getInstance(){
		return glueFactory;
	}

//	刷新 运行模式工厂  对象
	public static void refreshInstance(int type){
		if (type == 0) {
			glueFactory = new GlueFactory();
		} else if (type == 1) {
			glueFactory = new SpringGlueFactory();
		}
	}


	/**
	 * groovy class loader
	 * GroovyClassLoader
	 * 提供了一种将字符串文本代码直接转换成Java Class对象的功能
	 */
	private GroovyClassLoader groovyClassLoader = new GroovyClassLoader();

	//	创建map  集合对象,缓存  源码的class对象
	private ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

	/**
	 * load new instance, prototype
	 * 加载新实例、原型
	 * @param codeSource  代码
	 * @return
	 * @throws Exception
	 */
	public IJobHandler loadNewInstance(String codeSource) throws Exception{
		if (codeSource!=null && codeSource.trim().length()>0) {
//			根据字符串  获取  到class对象
			Class<?> clazz = getCodeSourceClass(codeSource);
			if (clazz != null) {
//				创建class 的实例对象
				Object instance = clazz.newInstance();
				if (instance!=null) {
//					如果当前的实例对象  是  IJobHandler 类型
					if (instance instanceof IJobHandler) {

//  初始化  属性字段
						this.injectService(instance);
//						返回当前的任务处理器 对象
						return (IJobHandler) instance;
					}

					else {
						throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, "
								+ "cannot convert from instance["+ instance.getClass() +"] to IJobHandler");
					}
				}
			}
		}
		throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, instance is null");
	}

	/**
	 * 将字符串 转为class类对象
	 *
	 * @param codeSource
	 */
	private Class<?> getCodeSourceClass(String codeSource){
		try {
			// md5  MessageDigest  自带加密类
//			哈希计算字节数组
			byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
			String md5Str = new BigInteger(1, md5).toString(16);
//ConcurrentMap  从map里面获取class文件对象
			Class<?> clazz = CLASS_CACHE.get(md5Str);
			if(clazz == null){
//				使用 groovyClassLoader 类直接将字符串  转为class对象
				clazz = groovyClassLoader.parseClass(codeSource);
//				放到map缓存里面
				CLASS_CACHE.putIfAbsent(md5Str, clazz);
			}
			return clazz;
		} catch (Exception e) {
			return groovyClassLoader.parseClass(codeSource);
		}
	}

	/**
	 * inject service of bean field
	 *
	 * @param instance
	 */
	public void injectService(Object instance) {
		// do something
	}

}
