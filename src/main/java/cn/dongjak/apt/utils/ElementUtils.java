package cn.dongjak.apt.utils;

import javax.lang.model.element.Element;

public class ElementUtils {

    public static Element streamingGetElement(Element element, String expression) {
        Element lastElement = element;
        String[] expressions = expression.split("\\.");
        for (int i = 0; i < expressions.length; i++) {
            String name = expressions[i];
            try {
                lastElement = ((Element) lastElement.asType().getClass().getField("tsym").get(lastElement.asType())).getEnclosedElements().stream().filter(o -> {
                    return o.getKind().isField() && o.getSimpleName().toString().equals(name);
                }).findFirst().get();
            } catch (IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        return lastElement;
    }


    public static String getReadMethodName(Element element) {

        //element.asType().toString()
        return "get" + element.getSimpleName().toString().substring(0, 1).toUpperCase() + element.getSimpleName().toString().substring(1);
    }


    public static String getReadExpression(String expression) {
        String[] expressions = expression.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < expressions.length; i++) {
            String name = expressions[i];
            builder.append(".").append("get").append(name.substring(0, 1).toUpperCase()).append(name.substring(1)).append("()");
        }
        return builder.toString();
    }
}
