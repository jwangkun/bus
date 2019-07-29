package org.aoju.bus.sensitive;

import org.aoju.bus.core.lang.exception.InstrumentException;
import org.aoju.bus.core.utils.*;
import org.aoju.bus.sensitive.annotation.Condition;
import org.aoju.bus.sensitive.annotation.Entry;
import org.aoju.bus.sensitive.annotation.Sensitive;
import org.aoju.bus.sensitive.annotation.Strategy;
import org.aoju.bus.sensitive.provider.ConditionProvider;
import org.aoju.bus.sensitive.provider.StrategyProvider;
import org.aoju.bus.sensitive.strategy.BuiltInStrategy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.ContextValueFilter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * 脱敏接口
 *
 * @param <T> 参数类型
 * @author aoju.org
 * @version 3.0.1
 * @group 839128
 * @since JDK 1.8
 */
public class Provider<T> {

    /**
     * 脱敏属性
     */
    private String[] value;

    /**
     * 深度复制
     * 1. 为了避免深拷贝要求用户实现 clone 和 序列化的相关接口
     * 2. 为了避免使用 dozer 这种比较重的工具
     * 3. 自己实现暂时工作量比较大
     * <p>
     * 暂时使用 fastJson 作为实现深度拷贝的方式
     *
     * @param object 对象
     * @param <T>    泛型
     * @return 深拷贝后的对象
     * @since 0.0.2
     */
    public static <T> T clone(T object) {
        final Class clazz = object.getClass();
        String jsonString = JSON.toJSONString(object);
        return (T) JSON.parseObject(jsonString, clazz);
    }

    /**
     * 获得真正的处理对象,可能多层代理.
     */
    public static <T> T realTarget(Object target) {
        if (Proxy.isProxyClass(target.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return realTarget(metaObject.getValue("hi.target"));
        }
        return (T) target;
    }

    /**
     * 是否已经是脱敏过的内容了
     *
     * @param object 原始数据
     * @return 是否已经脱敏了
     */
    public static boolean alreadyBeSentisived(Object object) {
        return object == null || object.toString().indexOf("*") > 0;
    }

    /**
     * 将json字符串转化为StringObject类型的map
     *
     * @param jsonStr json字符串
     * @return map
     */
    public static Map<String, Object> parseToObjectMap(String jsonStr) {
        return JSON.parseObject(jsonStr, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    /**
     * 将map转化为json字符串
     *
     * @param params 参数集合
     * @return json
     */
    public static String parseMaptoJSONString(Map<String, Object> params) {
        return JSON.toJSONString(params, SerializerFeature.WriteMapNullValue);
    }

    /**
     * 对象进行脱敏操作
     * 原始对象不变，返回脱敏后的新对象
     * 1. 为什么这么设计？
     * 不能因为脱敏，就导致代码中的对象被改变。否则代码逻辑会出现问题。
     *
     * @param object 原始对象
     * @return 脱敏后的新对象
     */
    public T on(T object, Annotation annotation) {
        if (ObjectUtils.isEmpty(object)) {
            return object;
        }

        if (ObjectUtils.isNotEmpty(annotation)) {
            Sensitive sensitive = (Sensitive) annotation;
            this.value = sensitive.value();
        }

        //1. 初始化对象
        final Class clazz = object.getClass();
        final Context context = new Context();

        //2. 深度复制对象
        final T copyObject = clone(object);

        //3. 脱敏处理
        handleClassField(context, copyObject, clazz);
        return copyObject;
    }

    /**
     * 返回脱敏后的 json
     * 1. 避免 desCopy 造成的对象新建的性能浪费
     *
     * @param object 对象
     * @return json
     * @since 0.0.6
     */
    public String json(T object, Annotation annotation) {
        if (ObjectUtils.isEmpty(object)) {
            return JSON.toJSONString(object);
        }

        if (ObjectUtils.isNotEmpty(annotation)) {
            Sensitive sensitive = (Sensitive) annotation;
            this.value = sensitive.value();
        }

        final Context context = new Context();
        ContextValueFilter filter = new Filter(context);
        return JSON.toJSONString(object, filter);
    }

    /**
     * 处理脱敏相关信息
     *
     * @param context    执行上下文
     * @param copyObject 拷贝的新对象
     * @param clazz      class 类型
     */
    private void handleClassField(final Context context,
                                  final Object copyObject,
                                  final Class clazz) {
        // 每一个实体对应的字段，只对当前 clazz 生效。
        List<Field> fieldList = ClassUtils.getAllFieldList(clazz);
        context.setAllFieldList(fieldList);
        context.setCurrentObject(copyObject);

        try {
            for (Field field : fieldList) {
                if (ArrayUtils.isNotEmpty(this.value)) {
                    if (!Arrays.asList(this.value).contains(field.getName())) {
                        continue;
                    }
                }
                // 设置当前处理的字段
                final Class fieldTypeClass = field.getType();
                context.setCurrentField(field);

                // 处理 @Entry 注解
                Entry sensitiveEntry = field.getAnnotation(Entry.class);
                if (ObjectUtils.isNotNull(sensitiveEntry)) {
                    if (TypeUtils.isJavaBean(fieldTypeClass)) {
                        // 为普通 javabean 对象
                        final Object fieldNewObject = field.get(copyObject);
                        handleClassField(context, fieldNewObject, fieldTypeClass);
                    } else if (TypeUtils.isArray(fieldTypeClass)) {
                        // 为数组类型
                        Object[] arrays = (Object[]) field.get(copyObject);
                        if (ArrayUtils.isNotEmpty(arrays)) {
                            Object firstArrayEntry = arrays[0];
                            final Class entryFieldClass = firstArrayEntry.getClass();

                            //1. 如果需要特殊处理，则循环特殊处理
                            if (needHandleEntryType(entryFieldClass)) {
                                for (Object arrayEntry : arrays) {
                                    handleClassField(context, arrayEntry, entryFieldClass);
                                }
                            } else {
                                //2, 基础值，直接循环设置即可
                                final int arrayLength = arrays.length;
                                Object newArray = Array.newInstance(entryFieldClass, arrayLength);
                                for (int i = 0; i < arrayLength; i++) {
                                    Object entry = arrays[i];
                                    Object result = handleSensitiveEntry(context, entry, field);
                                    Array.set(newArray, i, result);
                                }
                                field.set(copyObject, newArray);
                            }
                        }
                    } else if (TypeUtils.isCollection(fieldTypeClass)) {
                        // Collection 接口的子类
                        final Collection<Object> entryCollection = (Collection<Object>) field.get(copyObject);
                        if (CollUtils.isNotEmpty(entryCollection)) {
                            Object firstCollectionEntry = entryCollection.iterator().next();
                            Class collectionEntryClass = firstCollectionEntry.getClass();

                            //1. 如果需要特殊处理，则循环特殊处理
                            if (needHandleEntryType(collectionEntryClass)) {
                                for (Object collectionEntry : entryCollection) {
                                    handleClassField(context, collectionEntry, collectionEntryClass);
                                }
                            } else {
                                //2, 基础值，直接循环设置即可
                                List<Object> newResultList = new ArrayList<>(entryCollection.size());
                                for (Object entry : entryCollection) {
                                    Object result = handleSensitiveEntry(context, entry, field);
                                    newResultList.add(result);
                                }
                                field.set(copyObject, newResultList);
                            }
                        }
                    } else {
                        // 1. 常见的基本类型，不做处理
                        // 2. 如果为 map，暂时不支持处理。后期可以考虑支持 value 的脱敏，或者 key 的脱敏
                        // 3. 其他
                        // 处理单个字段脱敏信息
                        handleSensitive(context, copyObject, field);
                    }
                } else {
                    handleSensitive(context, copyObject, field);
                }
            }

        } catch (IllegalAccessException e) {
            throw new InstrumentException(e);
        }
    }

    /**
     * 处理需脱敏的单个对象
     * <p>
     * 1. 为了简化操作，所有的自定义注解使用多个，不生效。
     * 2. 生效顺序如下：
     * （1）Sensitive
     * （2）系统内置自定义注解
     * （3）用户自定义注解
     *
     * @param context 上下文
     * @param entry   明细
     * @param field   字段信息
     * @return 处理后的信息
     * @since 0.0.2
     */
    private Object handleSensitiveEntry(final Context context,
                                        final Object entry,
                                        final Field field) {
        try {
            //处理 @Field
            org.aoju.bus.sensitive.annotation.Field sensitive = field.getAnnotation(org.aoju.bus.sensitive.annotation.Field.class);
            if (ObjectUtils.isNotNull(sensitive)) {
                Class<? extends ConditionProvider> conditionClass = sensitive.condition();
                ConditionProvider condition = conditionClass.newInstance();
                if (condition.valid(context)) {
                    Class<? extends StrategyProvider> strategyClass = sensitive.strategy();
                    StrategyProvider strategy = strategyClass.newInstance();
                    return strategy.build(entry, context);
                }
            }

            // 获取所有的注解
            Annotation[] annotations = field.getAnnotations();
            if (ArrayUtils.isNotEmpty(annotations)) {
                ConditionProvider condition = getCondition(annotations);
                if (ObjectUtils.isNull(condition)
                        || condition.valid(context)) {
                    StrategyProvider strategy = getStrategy(annotations);
                    if (ObjectUtils.isNotNull(strategy)) {
                        return strategy.build(entry, context);
                    }
                }
            }
            return entry;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new InstrumentException(e);
        }
    }

    /**
     * 处理脱敏信息
     *
     * @param context    上下文
     * @param copyObject 复制的对象
     * @param field      当前字段
     * @since 0.0.2
     */
    private void handleSensitive(final Context context,
                                 final Object copyObject,
                                 final Field field) {
        try {
            //处理 @Field
            org.aoju.bus.sensitive.annotation.Field sensitive = field.getAnnotation(org.aoju.bus.sensitive.annotation.Field.class);
            if (sensitive != null) {
                Class<? extends ConditionProvider> conditionClass = sensitive.condition();
                ConditionProvider condition = conditionClass.newInstance();
                if (condition.valid(context)) {
                    Class<? extends StrategyProvider> strategyClass = sensitive.strategy();
                    StrategyProvider strategy = strategyClass.newInstance();
                    final Object originalFieldVal = field.get(copyObject);
                    final Object result = strategy.build(originalFieldVal, context);
                    field.set(copyObject, result);
                }
            }

            // 系统内置自定义注解的处理,获取所有的注解
            Annotation[] annotations = field.getAnnotations();
            if (ArrayUtils.isNotEmpty(annotations)) {
                ConditionProvider condition = getCondition(annotations);
                if (ObjectUtils.isNull(condition)
                        || condition.valid(context)) {
                    StrategyProvider strategy = getStrategy(annotations);
                    if (ObjectUtils.isNotNull(strategy)) {
                        final Object originalFieldVal = field.get(copyObject);
                        final Object result = strategy.build(originalFieldVal, context);
                        field.set(copyObject, result);
                    }
                }
            }
        } catch (InstantiationException | IllegalAccessException e) {
            throw new InstrumentException(e);
        }
    }

    /**
     * 获取策略
     *
     * @param annotations 字段对应注解
     * @return 策略
     */
    private StrategyProvider getStrategy(final Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            Strategy strategy = annotation.annotationType().getAnnotation(Strategy.class);
            if (ObjectUtils.isNotNull(strategy)) {
                Class<? extends StrategyProvider> clazz = strategy.value();
                if (BuiltInStrategy.class.equals(clazz)) {
                    return Registry.require(annotation.annotationType());
                } else {
                    return ClassUtils.newInstance(clazz);
                }
            }
        }
        return null;
    }

    /**
     * 获取用户自定义条件
     *
     * @param annotations 字段上的注解
     * @return 对应的用户自定义条件
     */
    private ConditionProvider getCondition(final Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            Condition condition = annotation.annotationType().getAnnotation(Condition.class);
            if (ObjectUtils.isNotNull(condition)) {
                Class<? extends ConditionProvider> clazz = condition.value();
                return ClassUtils.newInstance(clazz);
            }
        }
        return null;
    }

    /**
     * 需要特殊处理的列表/对象类型
     *
     * @param fieldTypeClass 字段类型
     * @return 是否
     * @since 0.0.2
     */
    private boolean needHandleEntryType(final Class fieldTypeClass) {
        if (TypeUtils.isBase(fieldTypeClass)
                || TypeUtils.isMap(fieldTypeClass)) {
            return false;
        }

        if (TypeUtils.isJavaBean(fieldTypeClass)
                || TypeUtils.isArray(fieldTypeClass)
                || TypeUtils.isCollection(fieldTypeClass)) {
            return true;
        }
        return false;
    }

}