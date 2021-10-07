package mbr.javax.persistence.enums.annotation;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.persistence.EnumType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes(
        "mbr.javax.persistence.enums.annotation.EnumConvertor")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EnumConvertorAnnotationProcessor extends AbstractProcessor {
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        node(EnumConvertorAnnotationProcessor.class.getCanonicalName() + " IS INITIALISED");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for(TypeElement annotation : annotations) {
            _process(annotation, roundEnv);
        }
        return true;
    }

    private void _process(TypeElement annotation, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        node("@EnumConvertor annotated elements: " + annotatedElements.size());
        for(Element element : annotatedElements) {
            if(ElementKind.ENUM != element.getKind()) {
                messager().printMessage(Diagnostic.Kind.ERROR, "@EnumConvertor annotation must be applied to ENUMS", annotation);
                continue;
            }
            EnumConvertor convertor = element.getAnnotation(EnumConvertor.class);
            if(convertor == null) {
                messager().printMessage(Diagnostic.Kind.ERROR, "EnumConvertor not found", annotation);
                return;
            }
            EnumType type = convertor.type();
            try{
                writeJavaFile((TypeElement) element, type);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void writeJavaFile(TypeElement element, EnumType type) throws IOException {
        String className = element.getQualifiedName().toString();
        node(className + " IS BEING PROCESSED ... ");
        int lastDotIndex = className.lastIndexOf(".");

        String simpleClassName = className.substring(lastDotIndex + 1);
        String convertorSimpleClassName = simpleClassName + "JpaConvertor";

        String convertorClassName = convertorSimpleClassName;
        String packageName = null;
        if(lastDotIndex > 0) {
            packageName = className.substring(0, lastDotIndex);
            convertorClassName = packageName + "." + convertorSimpleClassName;
        }

        JavaFileObject sourceFile = filer().createSourceFile(convertorClassName);
        try(PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
            String sourceCode = generateSourceCode(packageName, className, simpleClassName, convertorSimpleClassName, type);
            writer.write(sourceCode);
        }
        node(convertorClassName + " IS GENERATED!");
    }

    private String generateSourceCode(String pkgName, String qualifiedClassName, String simpleClassName,
                                      String convertorSimpleClassName, EnumType type) throws IOException{
        InputStream vmInputStream = getClass().getResourceAsStream("/JpaConvertor.java.vm");
        StringBuilder sb = new StringBuilder();
        BufferedReader brd = new BufferedReader(new InputStreamReader(vmInputStream));
        String line = "";
        Map<String, String> bindings= new HashMap<>();

        bindings.put("packageLine", "package " + pkgName + ";");
        bindings.put("enumClassImportLine", "import " + qualifiedClassName + ";");

        if(pkgName == null) {
            bindings.put("package", "");
            bindings.put("enumClassImportLine", "");
        }
        bindings.put("simpleClassName", simpleClassName);
        bindings.put("convertorSimpleClassName", convertorSimpleClassName);
        bindings.put("enumType", type.name());
        String NL = System.lineSeparator();
        while((line = brd.readLine()) != null) {
            line = format(line, bindings);
            sb.append(line).append(NL);
        }
        return sb.toString();
    }

    private String format(String str, Map<String, String> bindings) {
        for(Map.Entry<String, String> e : bindings.entrySet()) {
            str = format(str, e.getKey(), e.getValue());
        }
        return str;
    }
    private static String format(String template, String variable, String value) {
        String ret = template.replace("${"+variable+"}", value);
        return ret;
    }

    private Messager messager() {
        return processingEnv.getMessager();
    }
    private Filer filer() {
        return processingEnv.getFiler();
    }

    private void node(String msg) {
        System.out.println("NOTE: " + msg);
        messager().printMessage(Diagnostic.Kind.NOTE, msg);
    }
    private void node(String msg, TypeElement element) {
        System.out.println("NOTE: " + msg);
        messager().printMessage(Diagnostic.Kind.NOTE, msg, element);
    }
}
