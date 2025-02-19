class SimpleClass {
    x = 5;
}

class Example {
    createClassInstance() {
        let x = new SimpleClass();
        return x.x;
    }

    createClassInstanceAndWriteField() {
        let x = new SimpleClass();
        x.x = 14;

        return x;
    }
}