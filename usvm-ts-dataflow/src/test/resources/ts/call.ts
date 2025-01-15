function entrypoint() {
    f(42);
    let x= 7
    g(x);
}

function f(x: any) {
    console.log(x);
}

function g(x: any) {
    console.log(x);
}
