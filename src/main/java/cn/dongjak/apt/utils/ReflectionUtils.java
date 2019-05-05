package cn.dongjak.apt.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Objects;

import static org.reflections.ReflectionUtils.getAllFields;
import static org.reflections.ReflectionUtils.withName;

public class ReflectionUtils {
    @SuppressWarnings("unchecked")
    public static Field getField(Class c, String fieldName) {
        return new ArrayList<>(getAllFields(c, withName(fieldName))).get(0);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object target, String fieldName, Class<T> tClass) {
        if (Objects.isNull(target) || Objects.isNull(fieldName)) return null;
        Field field = getField(target.getClass(), fieldName);
        field.setAccessible(true);
        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String getClassSimpleName(String className) {
        return className.substring(className.lastIndexOf(".") + 1);
    }
}
