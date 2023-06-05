package org.usvm.examples.reflection;

public class NewInstanceExample {
    @SuppressWarnings("deprecation")
    int createWithReflectionExample() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<?> cls = Class.forName("org.usvm.examples.reflection.ClassWithDefaultConstructor");
        ClassWithDefaultConstructor classWithDefaultConstructor = (ClassWithDefaultConstructor) cls.newInstance();

        return classWithDefaultConstructor.x;
    }
}

class ClassWithDefaultConstructor {

    int x;
}
