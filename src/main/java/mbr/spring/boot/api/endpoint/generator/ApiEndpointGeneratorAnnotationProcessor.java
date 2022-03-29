package mbr.spring.boot.api.endpoint.generator;

import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "mbr.spring.boot.api.endpoint.generator.ApiEndpointGenerate"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ApiEndpointGeneratorAnnotationProcessor extends AbstractProcessor {
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        node(ApiEndpointGeneratorAnnotationProcessor.class.getCanonicalName() + " IS INITIALISED ----");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            String qName = annotation.getQualifiedName().toString();
            node(qName);
            _processConvertors(annotation, roundEnv);
        }
        return true;
    }

    private void _processConvertors(TypeElement annotation, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        node("Annotated Elements: " + annotatedElements.size());
        //TODO: create or load resource here
        results = new ArrayList<>();
        for (Element aEl : annotatedElements) {
            ApiEndpointGenerate genAnno = aEl.getAnnotation(ApiEndpointGenerate.class);
            Set<? extends Element> elements = roundEnv
                    .getRootElements().stream().filter(this::isController).collect(Collectors.toSet());
            jPackage = genAnno.jPackage();
            elements.forEach(e -> {
                node(e.getSimpleName().toString());
                generateEndpoints(e);
            });

            /*
            IGenerator generator = create(genAnno);
            Set<? extends Element> elements = roundEnv.getRootElements();
            elements.forEach(el->{
                generator.generate((TypeElement) el,processingEnv, roundEnv);
            });
             */
        }
        //TODO: write the prepared resource to the resource file
        try {
            generateJavaFile();
        } catch (Exception e) {
            error(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    private void generateJavaFile() throws IOException {

        InputStream vmInputStream = getClass().getResourceAsStream("/ApiEndpoints.java.vm");
        byte[] bytes = vmInputStream.readAllBytes();

        String codeTemplate = new String(bytes);

        String className = "mn.infinite.nes.middleware.ApiEndpoints";
        PrintWriter writer = null;
        try {
            JavaFileObject sourceFile = filer().createSourceFile(className);
            writer = new PrintWriter(sourceFile.openWriter());
            String sourceCode = mergeByReplacement(codeTemplate); //merge(codeTemplate);
            /*
            StringBuilder lines = new StringBuilder();
            Set<String> dup = new HashSet<>();
            results.forEach(result -> {
                if (!dup.contains(result.name)) {
                    result.toLine(lines);
                    dup.add(result.name);
                }
            });
            lines.append(";").append(System.lineSeparator());
            String sourceCode = codeTemplate.replace("${RESULT_VALUES}", lines.toString());
             */
            writer.write(sourceCode);
            writer.flush();
            //node(sourceCode);
        } catch (Exception e) {
            error(e.getMessage());
            e.printStackTrace();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        node(className + " IS GENERATED!");
    }

    private static String ROW_TEMPLATE = "APIS.add(new ApiEndpoints(\"${NAME}\", \"\", \"$ARGS[2]\", \"$ARGS[3]\"));\n";

    private String mergeRow(EnumVal vl) {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("${NAME}", vl.name);
        for (int i = 0; i < vl.args.length; i++) {
            bindings.put("$ARGS[" + i + "]", vl.args[i]);
        }
        String ret = ROW_TEMPLATE;
        for (Map.Entry<String, Object> e : bindings.entrySet()) {
            ret = ret.replace(e.getKey(), "" + e.getValue());
        }
        return ret;
    }

    private String mergeByReplacement(String codeTemplate) {

        int jPackageIdx = codeTemplate.indexOf("${packageName}");
        if(jPackageIdx !=-1){
            codeTemplate = codeTemplate.replace("${packageName}", jPackage);
        }
        int index = codeTemplate.indexOf("${RESULTS}");
        if (index != -1) {
            StringBuilder sb = new StringBuilder();
            results.forEach(result -> {
                String row = mergeRow(result);
                sb.append(row);
            });
            String replacement = sb.toString();
            return codeTemplate.replace("${RESULTS}", replacement);
        }

        throw new RuntimeException("[2] not evaluated: ");
    }


    private String trace1(Exception e) {
        StringWriter wr = new StringWriter();
        PrintWriter pwr = new PrintWriter(wr);

        e.printStackTrace(pwr);

        return wr.toString();
    }

    private boolean isController(Element el) {
        Annotation an = el.getAnnotation(RestController.class);
        RequestMapping mapping = el.getAnnotation(RequestMapping.class);
        return an != null && mapping != null;
    }

    private List<EnumVal> results;
    private String jPackage="";
    private void generateEndpoints(Element el) {
        RequestMapping mapping = el.getAnnotation(RequestMapping.class);
        String[] uris = mapping.value();
        RequestMethod[] methods = mapping.method();
        TypeElement tE = (TypeElement) el;

        Set<? extends Element> restMethods = tE.getEnclosedElements()
                .stream().filter(this::isRequestMethod).collect(Collectors.toSet());
        /*
        restMethods.forEach(e -> {
            node(el.getSimpleName().toString() + "." + e.getSimpleName().toString());
        });
         */
        for (Element rRl : restMethods) {
            for (String uri : uris) {
                generate(uri, rRl, el);
            }
            node(el.getSimpleName().toString() + "." + rRl.getSimpleName().toString() + " GENERATED!");
        }

    }

    private void generate(String uri, Element requestMethod, Element ctrlEl) {
        ReqMap[] maps = extract(requestMethod, ctrlEl);
        for (ReqMap mp : maps) {
            generate(uri, mp, ctrlEl);
        }
    }

    private List<ReqMap> extract(String[] paths, HttpMethod method, String function) {
        List<ReqMap> ret = new ArrayList<>();
        if (paths != null && paths.length > 0) {
            for (String path : paths) {
                ret.add(ReqMap.of(path, method, function));
            }
        } else {
            ret.add(ReqMap.of("", method, function));
        }
        return ret;
    }

    private ReqMap[] extract(Element requestMethod, Element ctrlEl) {
        String name = requestMethod.getSimpleName().toString();

        List<ReqMap> list = new ArrayList<>();
        GetMapping get = requestMethod.getAnnotation(GetMapping.class);
        if (get != null) {
            list.addAll(extract(get.value(), HttpMethod.GET, name));
        }
        PostMapping post = requestMethod.getAnnotation(PostMapping.class);
        if (post != null) {
            list.addAll(extract(post.value(), HttpMethod.POST, name));
        }
        PutMapping put = requestMethod.getAnnotation(PutMapping.class);
        if (put != null) {
            list.addAll(extract(put.value(), HttpMethod.PUT, name));
        }
        DeleteMapping delete = requestMethod.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            list.addAll(extract(delete.value(), HttpMethod.DELETE, name));
        }
        return list.toArray(new ReqMap[]{});
    }

    private void generate(String uri, ReqMap map, Element ctrlEl) {
        //TODO: generate to resource file
        String url = concat(uri, map.urlPattern);
        String ctrl = ctrlEl.getSimpleName().toString();
        String endpoint = map.function;
        HttpMethod method = map.method;

        String enumValFinalName = ctrl + "_" + endpoint + "_" + method.name();

        int index = sameNameIndices.getOrDefault(enumValFinalName, 0);
        if (index > 0) {
            enumValFinalName = enumValFinalName + "_" + index;
        }
        sameNameIndices.put(enumValFinalName, index + 1);

        EnumVal vl = EnumVal.of(enumValFinalName, ctrl, endpoint, url, method.name());
        results.add(vl);
    }

    private Map<String, Integer> sameNameIndices = new HashMap<>();

    private String concat(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            sb.append(value);
        }
        ;
        String ret = sb.toString();
        sb = new StringBuilder();
        sb.append(ret.charAt(0));
        for (int i = 1; i < ret.length(); i++) {
            char c = ret.charAt(i);
            if (c == '/' && ret.charAt(i - 1) == '/') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isRequestMethod(Element el) {
        RequestMapping m = el.getAnnotation(RequestMapping.class);
        GetMapping get = el.getAnnotation(GetMapping.class);
        PutMapping put = el.getAnnotation(PutMapping.class);
        PostMapping post = el.getAnnotation(PostMapping.class);
        DeleteMapping delete = el.getAnnotation(DeleteMapping.class);
        return first(m, get, put, post, delete) != null;
    }

    private Object first(Object... values) {
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static String format(String template, String variable, String value) {
        String ret = template.replace("${" + variable + "}", value);
        return ret;
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

    private Messager messager() {
        return processingEnv.getMessager();
    }

    private Filer filer() {
        return processingEnv.getFiler();
    }

    private static class ReqMap {
        String urlPattern;
        HttpMethod method;
        String function;

        static ReqMap of(String urlPattern, HttpMethod method, String function) {
            ReqMap ret = new ReqMap();
            ret.urlPattern = urlPattern;
            ret.method = method;
            ret.function = function;
            return ret;
        }
    }

    public static class EnumVal {
        private String name;
        private String[] args;

        static EnumVal of(String name, String... args) {
            EnumVal ret = new EnumVal();
            ret.name = name;
            ret.args = args;
            return ret;
        }

        public String line() {
            StringBuilder sb = new StringBuilder();
            toLine(sb);
            return sb.toString();
        }

        void toLine(StringBuilder sb) {
            sb.append(name).append("(");
            if (this.args != null) {
                for (String arg : args) {
                    sb.append('"').append(arg).append('"').append(",");
                }
            }
            int len = sb.length();
            sb.replace(len - 1, len, "),").append(System.lineSeparator());
        }

        public String getName() {
            return name;
        }

        public String[] getArgs() {
            return args;
        }
    }
}
