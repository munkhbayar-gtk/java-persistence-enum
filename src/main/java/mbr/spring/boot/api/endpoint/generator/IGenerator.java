package mbr.spring.boot.api.endpoint.generator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

public interface IGenerator {

    void generate(TypeElement typeElement, ProcessingEnvironment procEnv, RoundEnvironment roundEnv);
}
