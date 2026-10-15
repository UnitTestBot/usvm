function rawWithSideEffect() {
    let x = 0;
    with ({}) {
        x = 1;
    }
    return x;
}
