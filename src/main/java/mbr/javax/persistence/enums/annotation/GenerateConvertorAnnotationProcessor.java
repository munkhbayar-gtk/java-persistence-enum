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
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
    "mbr.javax.persistence.enums.annotation.GenConvertor",
    "mbr.javax.persistence.enums.annotation.GenerateEnumsMappingConvertors"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GenerateConvertorAnnotationProcessor extends AbstractProcessor {
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        node(GenerateConvertorAnnotationProcessor.class.getCanonicalName() + " IS INITIALISED");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for(TypeElement annotation : annotations) {
            String qName = annotation.getQualifiedName().toString();
            if(qName.endsWith("GenerateEnumsMappingConvertors")) {
                _processConvertors(annotation, roundEnv);
                continue;
            }
            _process(annotation, roundEnv);
        }
        return true;
    }

    private void collectEnums(Element el, Set<TypeElement> result) {
        if(ElementKind.ENUM == el.getKind()) {
            result.add((TypeElement) el);
            return;
        }
        List<? extends Element> closedElements = el.getEnclosedElements();
        for(Element closedEl : closedElements) {
            collectEnums(closedEl, result);
        }
    }
    private Set<TypeElement> getAllEnums(RoundEnvironment roundEnv) {
        Set<? extends Element> roots = roundEnv.getRootElements();
        Set<TypeElement> ret = new HashSet<>();
        roots.stream().forEach(el->collectEnums(el, ret));
        return ret;
    }
    private void _processConvertors(TypeElement annotation, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        for(Element aEl : annotatedElements) {
            node("Root: " + ((TypeElement)aEl).getQualifiedName().toString());
            GenerateEnumsMappingConvertors meta = aEl.getAnnotation(GenerateEnumsMappingConvertors.class);
            EnumType type = meta.value();
            Set<? extends Element> enums = getAllEnums(roundEnv);
            node("Enums: " + enums.size());
            for(Element elEnum : enums) {
                TypeElement el = (TypeElement)elEnum;
                String enumClassName = el.getQualifiedName().toString();

                GenConvertor convertor = el.getAnnotation(GenConvertor.class);
                if(convertor != null)continue;

                startGenerating(enumClassName, type);
            }
        }

    }

    private void _process(TypeElement annotation, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        node("@GenConvertor annotated elements: " + annotatedElements.size());
        for(Element element : annotatedElements) {
            if(ElementKind.ENUM != element.getKind()) {
                messager().printMessage(Diagnostic.Kind.ERROR, "@GenConvertor annotation must be applied to ENUMS", annotation);
                continue;
            }
            GenConvertor convertor = element.getAnnotation(GenConvertor.class);
            if(convertor == null) {
                messager().printMessage(Diagnostic.Kind.ERROR, "GenConvertor not found", annotation);
                return;
            }
            String enumClassName = ((TypeElement)element).getQualifiedName().toString();
            startGenerating(enumClassName, convertor.type());
        }
    }

    private boolean isEnum(Element el) {
        return ElementKind.ENUM == el.getKind();
    }

    private void startGenerating(String enumClassName, EnumType type) {
        try{

            writeJavaFile(enumClassName, type);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void writeJavaFile(String enumClassName, EnumType type) throws IOException {
        String className = enumClassName; //element.getQualifiedName().toString();
        node(className + " IS BEING PROCESSED ... ");
        int lastDotIndex = className.lastIndexOf(".");

        String simpleClassName = className.substring(lastDotIndex + 1);
        String convertorSimpleClassName = simpleClassName + "JpaConvertor";

        String convertorClassName = convertorSimpleClassName;
        String packageName = null;
        if(lastDotIndex > 0) {
            packageName = className.substring(0, lastDotIndex).toLowerCase();
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
        bindings.put("qualifiedClassName", qualifiedClassName);                                    
        if(pkgName == null || pkgName.length() == 0) {
            bindings.put("packageLine", "");
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

    private void error(String msg) {
        messager().printMessage(Diagnostic.Kind.ERROR, msg);
    }
    private void error(String msg, TypeElement el) {
        messager().printMessage(Diagnostic.Kind.ERROR, msg, el);
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
