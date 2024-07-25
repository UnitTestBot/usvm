class Data {
    a: number;
    b: number;
    constructor(a: number, b: number) {
        this.a = a;
        this.b = b;
    }
}

function entrypoint() {
    // process({a: 1, b: 2})
    process(new Data(7, 42))
    // process({a: 4, b: 2 + 3} as Data)
}

function process(data: any) {
    console.log(data)
}
