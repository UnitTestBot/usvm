class SimpleClass {
    noArguments(): Number {
        return 42
    }

    singleArgument(a) {
        return a
    }

    thisArgument(): SimpleClass {
        return this
    }
}
