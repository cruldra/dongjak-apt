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
}
