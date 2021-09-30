package mbr.javax.persistence.enums.annotation;

import javax.persistence.EnumType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface EnumConvertor {
    EnumType type() default EnumType.STRING;
}
