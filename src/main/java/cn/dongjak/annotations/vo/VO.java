package cn.dongjak.annotations.vo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface VO {
    String packageName() default "";

    String sceneName() default "";

    String[] excludes() default {};

    Field[] fields() default {};

    boolean usedExtjsGrid() default false;

    /**
     * 仅包含声明在 {@link #fields()}中的字段
     *
     * @return
     */
    boolean onlyIncludeDefinedFields() default false;

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Exclude {
        String[] in() default {};
    }


    @Target({ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.SOURCE)
    @interface Field {

        String name();

        String expression() default "";

        boolean extendFastJsonAnnotation() default true;
    }
}
