package example;

public class AnnotationsEx {
    @MyAnnotation
    public static int getSelfAnnotationCount() throws NoSuchMethodException {
        return AnnotationsEx.class.getMethod("getSelfAnnotationCount").getAnnotations().length;
    }

    @MyAnnotation
    public static String getAnnotationDefaultValue() throws NoSuchMethodException {
        return AnnotationsEx.class
                .getMethod("getAnnotationDefaultValue")
                .getAnnotation(MyAnnotation.class)
                .x();
    }
}
