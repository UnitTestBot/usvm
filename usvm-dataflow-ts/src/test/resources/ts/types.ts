interface A {
    aStr: string
    bObj: B
}

interface B {
    bNum: number
}

function conditional(x: A, cond: boolean): number {
    if (cond) {
        return x.aStr.length
    } else {
        return x.bObj.bNum
    }
}

function entrypoint1(arg: A) {
    console.log(conditional(arg, false))
}

interface X {
    a: string
}

interface Y {
    b: number
}

function foo(x: X | Y) {
    if ("a" in x) {
        strBar(x.a)
    } else {
        numberBar(x.b)
    }
}

function baz(x: X & Y) {
    strBar(x.a)
    numberBar(x.b)
}

function strBar(x: string) {

}

function numberBar(x: number) {

}

function entrypoint2(arg0: X, arg1: Y) {
    foo(arg0)
    foo(arg1)
    baz({...arg0, ...arg1})
}
