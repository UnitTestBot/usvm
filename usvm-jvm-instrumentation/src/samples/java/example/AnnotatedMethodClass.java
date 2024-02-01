package example;

public class AnnotatedMethodClass {
    @MyAnnotation
    public static int getSelfAnnotationCount() throws NoSuchMethodException {
        return AnnotatedMethodClass.class.getMethod("getSelfAnnotationCount").getAnnotations().length;
    }
}
