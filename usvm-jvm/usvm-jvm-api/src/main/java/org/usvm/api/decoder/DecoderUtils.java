package org.usvm.api.decoder;

import org.jacodb.api.jvm.JcClassOrInterface;
import org.jacodb.api.jvm.JcField;
import org.jacodb.api.jvm.JcMethod;

import java.util.ArrayList;
import java.util.List;

public class DecoderUtils {

    public static List<JcField> getAllFields(JcClassOrInterface clazz) {
        List<JcField> fields = new ArrayList<>();
        JcClassOrInterface current = clazz;

        do {
            fields.addAll(current.getDeclaredFields());
            current = current.getSuperClass();
        } while (current != null);

        return fields;
    }

    public static List<JcMethod> getAllMethods(JcClassOrInterface clazz) {
        List<JcMethod> methods = new ArrayList<>();
        JcClassOrInterface current = clazz;

        do {
            methods.addAll(current.getDeclaredMethods());
            current = current.getSuperClass();
        } while (current != null);

        return methods;
    }

    public static JcField findStorageField(JcClassOrInterface clazz) {
        List<JcField> fields = getAllFields(clazz);
        for (JcField field : fields) {
            if ("storage".equals(field.getName()))
                return field;
        }

        return null;
    }
}
