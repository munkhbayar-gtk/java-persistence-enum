package mbr.javax.persistence.enums.annotation;

import javax.persistence.EnumType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateEnumsMappingConvertors {
    //EnumType defaultType() default EnumType.STRING;

    /**
     * Default mapping type for all declared enums.
     * EnumType.STRING | EnumType.ORDINAL
     * @return
     */
    EnumType value() default EnumType.STRING;

}
