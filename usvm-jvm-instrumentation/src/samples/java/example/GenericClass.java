package example;

public class GenericClass<T extends Number> {
    GenericClass(T a, T b){
        this.a = a;
        this.b = b;
    }

    T a;
    T b;
}
