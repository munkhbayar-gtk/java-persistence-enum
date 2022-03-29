package mbr.spring.boot.api.endpoint.generator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

public class NullGeneratorImpl implements IGenerator{
    @Override
    public void generate(TypeElement typeElement, ProcessingEnvironment procEnv, RoundEnvironment roundEnv) {

    }
}
