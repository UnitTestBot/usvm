// Case `x := y`
function case1() {
    let x = 42; // x: number
    let y = x; // y: number
    infer1(y);
}

// Expected:
//   a: number
// Inferred:
//   a: number
function infer1(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x := y.f`
function case2(y: any) {
    let x = y.f; // y: { f: any }
    infer2(y);
}

// Expected:
//   a: { f: any }
// Inferred:
//   a: Object {}
function infer2(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x := y.f`
function case3() {
    let y = {f: 42}; // y: { f: number }
    let x = y.f; // x: number
    infer3(x);
}

// Expected:
//   a: number
// Inferred:
//   a: number
function infer3(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x.f := y`
function case4(x: any) {
    let y = 100; // y: number
    x.f = y; // x: { f: number }
    infer4(x);
}

// Expected:
//   a: { f: number }
// Inferred:
//   a: Object {}
function infer4(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x.f := y`
function case5(x: any) {
    let y = {t: 42}; // y: { t: number }
    x.f = y; // x: { f: { t: number } }
    infer5(x);
}

// Expected:
//   a: { f: { t: number } }
// Inferred:
//   a: Object {}
function infer5(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x := y[i]`
function case6(y: any) {
    let x = y[0]; // y: Array<any>
    infer6(y);
}

// Expected:
//   a: Array<any>
// Inferred:
//   a: Object {}
function infer6(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x := y[i]`
function case7() {
    let y = [42]; // y: Array<number>
    let x = y[0]; // x: number
    infer7(x);
}

// Expected:
//   a: number
// Inferred:
//   a: number
function infer7(a: any) {
    console.log(a);
}

// ----------------------------------------

// Case `x[i] := y`
function case8(x: any) {
    let y = 100; // y: number
    x[0] = y; // x: Array<number>
    infer8(x);
}

// Expected:
//   a: Array<number>
// Inferred:
//   a: Object {}
function infer8(a: any) {
    console.log(a);
}

// NOTES:
// cases 6 and 8 contain broken array representation (though case 7 is fine)
