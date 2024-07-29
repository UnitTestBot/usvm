class Data {
    a: number;
    b: number;
    constructor(a: number, b: number) {
        this.a = a;
        this.b = b;
    }
}

function entrypoint() {
    let data = new Data(7, 42);
    process(data);
}

function process(data: any) {
    console.log(data);
}
