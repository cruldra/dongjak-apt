package cn.dongjak.apt.vo;

import cn.dongjak.annotations.DbComment;
import cn.dongjak.annotations.VO;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static org.reflections.ReflectionUtils.getAllFields;

/**
 * 每一个注解处理器类都必须有一个空的构造函数，默认不写就行;
 */
@AutoService(VOAnnotationProcessor.class)
public class VOAnnotationProcessor extends AbstractProcessor {
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
                    VO valueObjectWrapper = element.getAnnotation(VO.class);
                    if (valueObjectWrapper != null) {
                        createFactoryClass(element, valueObjectWrapper);
                    }
                }
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "クラスファイル生成");
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private void createFactoryClass(Element element, VO vo) throws IOException {
//        ArgumentInfo argInfo = getArgumentInfo(element);

//        MethodSpec.Builder builder = MethodSpec.methodBuilder("create")
//                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
//                .returns(TypeName.get(element.asType()))
//                .addStatement("return new $T(" + argInfo.join() + ")",
//                        TypeName.get(element.asType()));
//        for (String arg : argInfo.getArgNames()) {
//            builder.addParameter(String.class, arg);
//        }
//        MethodSpec create = builder.build();

        String className = element.getSimpleName() + "VO";
        TypeSpec.Builder voBuilder = TypeSpec.classBuilder(className);

        voBuilder.addAnnotation(Data.class);
        voBuilder.addAnnotation(NoArgsConstructor.class);
        voBuilder.addAnnotation(AllArgsConstructor.class);
        voBuilder.addAnnotation(Builder.class);

        try {
            Class c = Class.forName(element.asType().toString());
            DbComment dbComment = (DbComment) c.getAnnotation(DbComment.class);
            if (Objects.nonNull(dbComment))
                voBuilder.addAnnotation(AnnotationSpec.builder(ApiModel.class).addMember("value", "$S", dbComment.value()).build());
            List<Field> fields = getAllFields(c).stream().filter(field -> {
                return !ArrayUtils.contains(vo.excludes(), field.getName());
            }).collect(Collectors.toList());

            fields.forEach(field -> {
                FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(field.getType(), field.getName(), Modifier.PRIVATE);
                DbComment fieldComment = field.getAnnotation(DbComment.class);
                if (Objects.nonNull(fieldComment))
                    fieldSpecBuilder.addAnnotation(AnnotationSpec.builder(ApiModelProperty.class).addMember("value", "$S", fieldComment.value()).build());
                voBuilder.addField(fieldSpecBuilder.build());
            });

            TypeSpec validationGroupsInterface = voBuilder.addModifiers(Modifier.PUBLIC)
                    .build();

            JavaFile javaFile = JavaFile.builder(
                    element.getEnclosingElement().toString(), validationGroupsInterface).
                    build();

            //生成したソースを確認したいので、コンソールに直接出力してみる
            System.out.println();
            System.out.println(javaFile);

            javaFile.writeTo(_filer);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
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
    public Set getSupportedAnnotationTypes() {
        Set annotataions = new LinkedHashSet();
        annotataions.add(VO.class.getCanonicalName());
        return annotataions;
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
