package org.aoju.bus.validate;

import org.aoju.bus.core.lang.exception.NoSuchException;
import org.aoju.bus.core.lang.exception.ValidateException;
import org.aoju.bus.core.utils.ObjectUtils;
import org.aoju.bus.validate.annotation.Complex;
import org.aoju.bus.validate.validators.Property;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务提供者
 *
 * @author aoju.org
 * @version 3.0.1
 * @group 839128
 * @since JDK 1.8
 */
public class Provider {

    /**
     * 被校验对象
     * <p>
     * 每次都创建一个新的对象，避免线程问题
     * 可以使用 {@link ThreadLocal} 简单优化。
     *
     * @param object 原始对象
     */
    public static <T> T on(Object object) {
        return (T) new Validated(object).access();
    }

    /**
     * 被校验对象
     * <p>
     * 每次都创建一个新的对象，避免线程问题
     * 可以使用 {@link ThreadLocal} 简单优化。
     *
     * @param object  原始对象
     * @param context 上下文信息
     */
    public static <T> T on(Object object, Context context) {
        return (T) new Validated(object, context).access();
    }

    /**
     * 被校验对象
     * <p>
     * 每次都创建一个新的对象，避免线程问题
     * 可以使用 {@link ThreadLocal} 简单优化。
     *
     * @param object      原始对象
     * @param annotations 注解信息
     */
    public static <T> T on(Object object, Annotation[] annotations) {
        return (T) new Validated(object, annotations).access();
    }

    /**
     * 被校验对象
     * <p>
     * 每次都创建一个新的对象，避免线程问题
     * 可以使用 {@link ThreadLocal} 简单优化。
     *
     * @param object      原始对象
     * @param annotations 注解信息
     * @param context     上下文信息
     */
    public static <T> T on(Object object, Annotation[] annotations, Context context) {
        return (T) new Validated(object, annotations, context).access();
    }

    /**
     * 被校验对象
     * <p>
     * 每次都创建一个新的对象，避免线程问题
     * 可以使用 {@link ThreadLocal} 简单优化。
     *
     * @param field       当前属性
     * @param object      原始对象
     * @param annotations 注解信息
     * @param context     上下文信息
     */
    public static <T> T on(Object object, Annotation[] annotations, Context context, String field) {
        return (T) new Validated(object, annotations, context, field).access();
    }

    /**
     * 是否为校验器注解
     *
     * @param annotation 注解
     */
    public static boolean isAnnotation(Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        return annotationType.getAnnotation(Complex.class) != null;
    }

    /**
     * 判断校验是否为数组
     *
     * @param object 当前校验组
     * @return true/false
     */
    public static boolean isArray(Object object) {
        return object.getClass().isArray();
    }

    /**
     * 判断校验是否为集合
     *
     * @param object 当前校验组
     * @return true/false
     */
    public static boolean isCollection(Object object) {
        return Collection.class.isAssignableFrom(object.getClass());
    }

    /**
     * 判断校验是否为Map
     *
     * @param object 当前校验组
     * @return true/false
     */
    public static boolean isMap(Object object) {
        return Map.class.isAssignableFrom(object.getClass());
    }

    /**
     * 判断校验组是否符合当前全局校验组范围
     *
     * @param group 当前校验组
     * @param list  校验环境中校验组属性
     * @return true：当前校验组中为空，或任意一个组环境存在于校验环境中
     */
    public static boolean isGroup(String[] group, List<String> list) {
        if (group == null || group.length == 0) {
            return true;
        } else {
            if (list == null || list.isEmpty()) {
                return false;
            } else {
                return Arrays.stream(group)
                        .anyMatch(neededGroup -> list.stream().anyMatch(neededGroup::equals));
            }
        }
    }

    /**
     * 解析校验异常
     *
     * @param property 校验器属性
     * @param context  校验上下文
     * @return 最终确定的错误码
     */
    public static ValidateException resolve(Property property, Context context) {
        Class<? extends ValidateException> clazz = context.getException();
        clazz = clazz == null ? property.getException() : clazz;
        String propertyEcode = property.getErrcode();
        String globalEcode = context.getErrcode();
        String ecode = Builder.DEFAULT_ERRCODE.equals(propertyEcode) ? globalEcode : propertyEcode;
        if (ObjectUtils.isEmpty(clazz)) {
            return new ValidateException(ecode, property.getFormatted());
        } else {
            try {
                Constructor<? extends ValidateException> constructor = clazz.getConstructor(String.class, int.class);
                return constructor.newInstance(property.getFormatted(), ecode);
            } catch (NoSuchMethodException e) {
                throw new NoSuchException("非法的自定义校验异常, 没有指定的构造方法: constructor(String, int)");
            } catch (IllegalAccessException e) {
                throw new NoSuchException("无法访问自定义校验异常构造方法");
            } catch (InstantiationException e) {
                throw new NoSuchException("反射构建自定义校验异常失败");
            } catch (InvocationTargetException e) {
                throw new NoSuchException("反射构建自定义校验异常失败");
            }
        }
    }

    /**
     * 获取当前对象的注解信息
     *
     * @param clazz 当前对象
     * @return list
     */
    public static List<Annotation> getAnnotation(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        return Arrays.stream(annotations).filter(Provider::isAnnotation).collect(Collectors.toList());
    }

}