package cn.dongjak.apt.vo;

import cn.dongjak.annotations.DbComment;
import cn.dongjak.annotations.vo.UseVo;
import cn.dongjak.annotations.vo.VO;
import cn.dongjak.annotations.vo.VOS;
import cn.dongjak.apt.utils.ElementUtils;
import cn.dongjak.apt.utils.ReflectionUtils;
import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.squareup.javapoet.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.persistence.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 每一个注解处理器类都必须有一个空的构造函数，默认不写就行;
 */
@AutoService(VOAnnotationProcessor.class)
public class VOAnnotationProcessor extends AbstractProcessor {

    private static Logger logger = Logger.getLogger(VOAnnotationProcessor.class.getName());
    private Filer _filer;

    /**
     * init()方法会被注解处理工具调用，并输入ProcessingEnviroment参数。
     * ProcessingEnviroment提供很多有用的工具类Elements, Types 和 Filer
     *
     * @param processingEnvironment 提供给 processor 用来访问工具框架的环境
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        _filer = processingEnvironment.getFiler();
        //messager = processingEnvironment.getMessager();
        // elements = processingEnvironment.getElementUtils();
        // activitiesWithPackage = new HashMap<>();
    }

    /**
     * 这相当于每个处理器的主函数main()，你在这里写你的扫描、评估和处理注解的代码，以及生成Java文件。
     * 输入参数RoundEnviroment，可以让你查询出包含特定注解的被注解元素
     *
     * @param annotations 请求处理的注解类型
     * @param roundEnv    有关当前和以前的信息环境
     * @return 如果返回 true，则这些注解已声明并且不要求后续 Processor 处理它们；
     * 如果返回 false，则这些注解未声明并且可能要求后续 Processor 处理它们
     */

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        try {
            for (TypeElement typeElement : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(typeElement)) {
                    VO voAnnotation = element.getAnnotation(VO.class);
                    if (Objects.nonNull(voAnnotation))
                        createSingleVo(element, voAnnotation);
                    VOS vosAnnotation = element.getAnnotation(VOS.class);
                    if (Objects.nonNull(vosAnnotation))
                        createMultiVo(element, vosAnnotation);
                }
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "クラスファイル生成");
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private void createMultiVo(Element element, VOS vosAnnotation) {
        for (VO vo : vosAnnotation.value()) {
            try {
                createSingleVo(element, vo);
            } catch (IOException e) {
                System.err.println("");
                logger.severe(String.format("创建应用于场景[%s]的VO失败!", vo.sceneName()));
            }
        }
    }


    @Data
    @Builder
    private static class FieldItem {
        private String name;

        @EqualsAndHashCode.Exclude
        private String expression;

        @Builder.Default
        private boolean extendFastJsonAnnotation = true;


        public static FieldItem formElement(Element element) {
            return FieldItem.builder().name(element.getSimpleName().toString()).build();
        }

        public static FieldItem formField(VO.Field field) {
            return FieldItem.builder()
                    .name(field.name())
                    .expression(field.expression())
                    .extendFastJsonAnnotation(field.extendFastJsonAnnotation())
                    .build();
        }
    }


    private String stringFirstUpper(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    private void createSingleVo(Element element, VO vo) throws IOException {
        String className = element.getSimpleName() + "VO";  //类名称
        if (vo.usedExtjsGrid())
            className = element.getSimpleName() + "VOForExtjsGrid";
        else if (StringUtils.isNotBlank(vo.sceneName()))
            className = element.getSimpleName() + "VOFor" + vo.sceneName();  //显示指定VO类名称

        //创建类型声明
        TypeSpec.Builder voBuilder = TypeSpec.classBuilder(className);

        /*
        添加类注释
         */
        voBuilder.addAnnotation(Data.class);
        voBuilder.addAnnotation(NoArgsConstructor.class);
        voBuilder.addAnnotation(AllArgsConstructor.class);
        voBuilder.addAnnotation(Builder.class);

        DbComment dbComment = element.getAnnotation(DbComment.class);
        if (Objects.nonNull(dbComment))
            voBuilder.addAnnotation(AnnotationSpec.builder(ApiModel.class).addMember("value", "$S", dbComment.value()).build());


        Set<FieldItem> fieldItems = Sets.newHashSet();
        if (vo.usedExtjsGrid()) {
            fieldItems.addAll(Arrays.stream(vo.fields()).map(FieldItem::formField).collect(Collectors.toSet()));
            fieldItems.addAll(element.getEnclosedElements().stream().filter(o -> {
                return o.getKind().isField()
                        && Objects.isNull(o.getAnnotation(OneToMany.class))
                        && Objects.isNull(o.getAnnotation(ManyToOne.class))
                        && Objects.isNull(o.getAnnotation(OneToOne.class))
                        && Objects.isNull(o.getAnnotation(ManyToMany.class))
                        && Objects.isNull(o.getAnnotation(Transient.class));
            }).map(FieldItem::formElement).collect(Collectors.toSet()));

        } else {
            fieldItems.addAll(Arrays.stream(vo.fields()).map(FieldItem::formField).collect(Collectors.toSet()));
            if (!vo.onlyIncludeDefinedFields()) {
                fieldItems.addAll(element.getEnclosedElements().stream().filter(o -> {
                    return o.getKind().isField()
                            && !ArrayUtils.contains(vo.excludes(), o.getSimpleName().toString()) //不包含在excludes声明中
                            && Objects.isNull(o.getAnnotation(VO.Exclude.class)) // 且该字段没有@Exclude标记
                            && Objects.isNull(o.getAnnotation(Transient.class)); // 且该字段没有@Transient标记
                }).map(FieldItem::formElement).collect(Collectors.toSet()));

            }

        }

        StringBuilder fromMethodReturnBuilder = new StringBuilder();
        fromMethodReturnBuilder.append("$T<$T> optional = Optional.ofNullable(domain);\n");
        fromMethodReturnBuilder.append("return $T.builder()");
        fieldItems.forEach(voField -> {
            String expression = StringUtils.isNotBlank(voField.getExpression()) ? voField.getExpression() : voField.getName();
            Element fieldElement = ElementUtils.streamingGetFieldElement(element, expression);
            TypeName typeName;
            ElementUtils.TypeDesc typeDesc = ElementUtils.getElementTypeDesc(fieldElement);
            UseVo useVoAnnotation = fieldElement.getAnnotation(UseVo.class);
            if (typeDesc.isCollection()) { //该字段是一个集合类型
                if (Objects.isNull(useVoAnnotation))
                    typeName = ParameterizedTypeName.get(ClassName.bestGuess(typeDesc.getClassName()),
                            typeDesc.getTypeParams().stream().map(ClassName::bestGuess).collect(Collectors.toSet()).toArray(new TypeName[]{}));
                else typeName = ParameterizedTypeName.get(ClassName.bestGuess(typeDesc.getClassName()),
                        ClassName.bestGuess(useVoAnnotation.value()));
            } else if (Objects.nonNull(useVoAnnotation)) {
                typeName = ClassName.bestGuess(useVoAnnotation.value());
            } else
                typeName = ClassName.bestGuess(fieldElement.asType().toString());
            FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(typeName, voField.getName(), Modifier.PRIVATE);
            addFieldDoc(fieldElement, fieldSpecBuilder);
            if (voField.extendFastJsonAnnotation)
                setFastJson(fieldElement, fieldSpecBuilder);
            voBuilder.addField(fieldSpecBuilder.build());


            //.status(optional.map(MCpConfig::getStatus).orElse(Defaults.defaultValue()))
            //.status(domain.getStatus())
            if (typeDesc.isCollection() && Objects.nonNull(useVoAnnotation)) {
                fromMethodReturnBuilder.append("\n").append(".").append(voField.getName()).append("(")
                        .append(ReflectionUtils.getClassSimpleName(useVoAnnotation.value())).append(".from").append(typeDesc.getCollectionType())
                        .append("(domain").append(ElementUtils.getReadExpression(expression)).append("))");
            } else if (Objects.nonNull(useVoAnnotation)) {
                fromMethodReturnBuilder.append("\n").append(".").append(voField.getName()).append("(")
                        .append(ReflectionUtils.getClassSimpleName(useVoAnnotation.value())).append(".from")
                        .append("(domain").append(ElementUtils.getReadExpression(expression)).append("))");
            } else {
                fromMethodReturnBuilder.append("\n").append(".").append(voField.getName()).append("(");//.append("domain").append(ElementUtils.getReadExpression(expression)).append(")");
                String[] expressions = expression.split("\\.");

                fromMethodReturnBuilder.append(String.format("optional.map(%s::%s)"
                        , ClassName.bestGuess(ReflectionUtils.getClassSimpleName(element.asType().toString()))
                        , "get" + stringFirstUpper(expressions[0])));
                for (int i = 1; i < expressions.length; i++)
                    fromMethodReturnBuilder.append(String.format(".map(val->{return val.%s();})", "get" + stringFirstUpper(expressions[i])));
                fromMethodReturnBuilder.append(String.format(".orElse(com.google.common.base.Defaults.defaultValue(%s.class))", typeName));
                fromMethodReturnBuilder.append(")");


            }
        });
        fromMethodReturnBuilder.append("\n.build()");
        String packageName = StringUtils.isNotBlank(vo.packageName()) ? vo.packageName() :
                element.getEnclosingElement().toString();


        voBuilder.addMethod(MethodSpec.methodBuilder("from").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(packageName, className))
                .addParameter(ClassName.bestGuess(element.asType().toString()), "domain")
                .addStatement(fromMethodReturnBuilder.toString(),
                        ClassName.bestGuess(Optional.class.getName()),
                        ClassName.bestGuess(element.asType().toString()),
                        ClassName.get(packageName, className))
                .build());


        voBuilder.addMethod(MethodSpec.methodBuilder("fromCollection").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(ClassName.bestGuess("java.util.Collection"), ClassName.get(packageName, className)))
                .addParameter(ParameterizedTypeName.get(ClassName.bestGuess("java.util.Collection"), ClassName.bestGuess(element.asType().toString())), "collection")
                .addStatement("return collection.stream().map($T::from).collect($T.toList())", ClassName.get(packageName, className), ClassName.bestGuess("java.util.stream.Collectors"))
                .build());


        voBuilder.addMethod(MethodSpec.methodBuilder("fromList").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(ClassName.bestGuess("java.util.List"), ClassName.get(packageName, className)))
                .addParameter(ParameterizedTypeName.get(ClassName.bestGuess("java.util.List"), ClassName.bestGuess(element.asType().toString())), "list")
                .addStatement("return list.stream().map($T::from).collect($T.toList())", ClassName.get(packageName, className), ClassName.bestGuess("java.util.stream.Collectors"))
                .build());

        TypeSpec validationGroupsInterface = voBuilder.addModifiers(Modifier.PUBLIC)
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, validationGroupsInterface).
                build();

        System.out.println("创建值包装对象:" + className);

        javaFile.writeTo(_filer);

    }

    private void setFastJson(Element fieldElement, FieldSpec.Builder fieldSpecBuilder) {
        fieldElement.getAnnotationMirrors().stream().filter(o -> {
            return ArrayUtils.contains(new String[]{
                    "com.alibaba.fastjson.annotation.JSONField"
            }, ((AnnotationMirror) o).getAnnotationType().toString());
        }).forEach(o -> {
            fieldSpecBuilder.addAnnotation(AnnotationSpec.get(o));
        });
    }


    private void addFieldDoc(Element fieldElement, FieldSpec.Builder fieldSpecBuilder) {
        DbComment dbCommentAnnotation = fieldElement.getAnnotation(DbComment.class);
        if (Objects.nonNull(dbCommentAnnotation)) {
            fieldSpecBuilder.addAnnotation(AnnotationSpec.builder(ApiModelProperty.class).addMember("value", "$S", dbCommentAnnotation.value()).build());
            fieldSpecBuilder.addJavadoc(dbCommentAnnotation.value());
        }
    }

    private String getPackageName(Element element) {
        List<String> packNames = new ArrayList<String>();
        Element packageElem = element.getEnclosingElement();
        while (packageElem != null) {
            String packName = packageElem.
                    getSimpleName().toString();
            packNames.add(packName);
            packageElem = packageElem.getEnclosingElement();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = packNames.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) {
                sb.append(".");
            }
            sb.append(packNames.get(i));
        }
        return sb.toString();
    }
//
//    private ArgumentInfo getArgumentInfo(Element element) {
//        ArgumentInfo argInfo = new ArgumentInfo();
//        for (Element e : element.getEnclosedElements()) {
//            if (e.getAnnotation(TargetField.class) != null) {
//                argInfo.add(e.getSimpleName().toString());
//            }
//        }
//        return argInfo;
//    }

    private class ArgumentInfo {

        private List<String> _argNames = new ArrayList<String>();

        public void add(String argName) {
            _argNames.add(argName);
        }

        public String[] getArgNames() {
            return _argNames.toArray(new String[0]);
        }

        public String join() {
            StringBuilder sb = new StringBuilder();
            for (String argName : _argNames) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(argName);
            }
            return sb.toString();
        }
    }

    /**
     * 这里必须指定，这个注解处理器是注册给哪个注解的。注意，它的返回值是一个字符串的集合，包含本处理器想要处理的注解类型的合法全称
     *
     * @return 注解器所支持的注解类型集合，如果没有这样的类型，则返回一个空集合
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Sets.newHashSet(VO.class.getCanonicalName(),
                VOS.class.getCanonicalName());
    }

    /**
     * 指定使用的Java版本，通常这里返回SourceVersion.latestSupported()，默认返回SourceVersion.RELEASE_6
     *
     * @return 使用的Java版本
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
