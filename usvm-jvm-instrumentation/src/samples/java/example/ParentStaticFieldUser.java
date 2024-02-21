package example;

public class ParentStaticFieldUser extends ClassWithStaticField {
    public static String getParentStaticField() {
        return ParentStaticFieldUser.STATIC_FIELD;
    }
}
