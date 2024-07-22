class Data {
    constructor(public a: number, public b: number) {
    }
}

function entrypoint() {
    process({a: 1, b: 2})
    process(new Data(7, 42))
    process({a: 4, b: 2 + 3} as Data)
}

function process(data: any) {
    console.log(data)
}
