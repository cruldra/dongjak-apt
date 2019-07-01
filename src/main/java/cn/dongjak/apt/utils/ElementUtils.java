package cn.dongjak.apt.utils;

import com.google.common.collect.Sets;
import com.squareup.javapoet.TypeName;
import lombok.Builder;
import lombok.Data;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.reflections.ReflectionUtils.getAllFields;
import static org.reflections.ReflectionUtils.withName;


/**
 * <a href="https://mvnrepository.com/artifact/org.jvnet.sorcerer/sorcerer-javac">Javac Compiler</a>
 */
public class ElementUtils {
    private static Logger logger = Logger.getLogger(ElementUtils.class.getName());


    /**
     * 获取给定路径上的字段元素
     *
     * @param typeElement 类元素
     * @param expression  字段路径表达式
     * @return 字段元素
     */
    public static Element streamingGetFieldElement(Element typeElement, String expression) {
        Element fieldElement = typeElement;
        String[] expressions = expression.split("\\.");

        for (int i = 0; i < expressions.length; i++) {
            String expstr = expressions[i];

            Element lastTargetElement = fieldElement;
            fieldElement = findFieldElement(fieldElement, expstr);

            if (fieldElement == null)
                throw new RuntimeException(String.format("在%s上执行路径表达式%s时发生错误,因为在%s上找不到字段%s", typeElement.getSimpleName(),
                        expression, lastTargetElement.getSimpleName(), expstr));

            if (expression.contains(".")) {
                if (!(fieldElement instanceof TypeElement) && i < expressions.length - 1) {
                    if (fieldElement.asType().getKind() == TypeKind.TYPEVAR) {
                        fieldElement = typeElement;
                    } else
                        fieldElement = ReflectionUtils.getFieldValue(fieldElement.asType(), "tsym", TypeElement.class);
                }
            }

        }
        return fieldElement;
    }

    private static Element findFieldElement(Element element, String fieldName) {
        if (Objects.isNull(element)) return element;
        TypeMirror elementType = element.asType();
        List<? extends Element> tsymElements = ReflectionUtils.getFieldValue(elementType, "tsym", Element.class).getEnclosedElements();

        Optional optional = tsymElements.stream().filter(o -> {
            return o.getKind().isField() && o.getSimpleName().toString().equals(fieldName);
        }).findFirst();
        if (optional.isPresent()) return (Element) optional.get();
        else return findFieldElement(getSuperClassElement(element), fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Element getSuperClassElement(Element element) {
        if (!element.getKind().isClass())
            throw new RuntimeException(element + " is not a class element!");
        Optional<Field> superFieldOptional = getAllFields(element.asType().getClass(), withName("supertype_field")).stream().findFirst();
        if (superFieldOptional.isPresent()) {
            try {
                Object superType = superFieldOptional.get().get(element.asType());
                return (Element) superType.getClass().getField("tsym").get(superType);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    public static String getReadMethodName(Element element) {
        return "get" + element.getSimpleName().toString().substring(0, 1).toUpperCase() + element.getSimpleName().toString().substring(1);
    }


    public static String getReadExpression(String expression) {
        String[] expressions = expression.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (String name : expressions) {
            builder.append(".").append("get").append(name.substring(0, 1).toUpperCase()).append(name.substring(1)).append("()");
        }
        return builder.toString();
    }


    /**
     * 获取字段元素的类型描述
     *
     * @param fieldElement 字段元素
     * @return javapoet {@link TypeName}
     */
   /* public static TypeName getFieldElementTypeName(Element fieldElement) {
        if (!fieldElement.getKind().isField()) throw new RuntimeException(fieldElement + " is not a field element!");

        TypeDesc typeDesc = getElementTypeDesc(fieldElement);
        if (typeDesc.isCollection()) {
            return ParameterizedTypeName.get(ClassName.bestGuess(typeDesc.className),
                    typeDesc.typeParams.stream().map(ClassName::bestGuess).collect(Collectors.toSet()).toArray(new TypeName[]{}));
        } else
            return ClassName.bestGuess(fieldElement.asType().toString());
    }*/
    @SuppressWarnings("all")
    public static TypeDesc getElementTypeDesc(Element element) {
        String className = ReflectionUtils.getFieldValue(element.asType(), "tsym", Element.class).toString();
        boolean isCollection;
        return TypeDesc.builder()
                .className(className)
                .isCollection(isCollection = Arrays.asList("java.util.List").contains(className))
                .collectionType(isCollection ? ReflectionUtils.getClassSimpleName(className) : null)
                .typeParams(mapElementTypeParamsToString(element))
                .build();
    }


    @SuppressWarnings("all")
    private static Set<String> mapElementTypeParamsToString(Element element) {
        List typarams = ReflectionUtils.getFieldValue(element.asType(), "typarams_field", List.class);
        return Objects.nonNull(typarams) ? (Set<String>) typarams.stream().map(o -> {
            return ReflectionUtils.getFieldValue(o, "tsym", Element.class).toString();
        }).collect(Collectors.toSet()) : Sets.newHashSet();
    }

    /*public static boolean fieldElementTypeIsCollection(Element fieldElement) {
        String className = ReflectionUtils.getFieldValue(fieldElement.asType(), "tsym", Element.class).toString();
        return Arrays.asList("java.util.List").contains(className);
    }*/

    @Data
    @Builder
    public static class TypeDesc {
        String className;
        boolean isCollection;
        String collectionType;
        Set<String> typeParams;
    }
}
