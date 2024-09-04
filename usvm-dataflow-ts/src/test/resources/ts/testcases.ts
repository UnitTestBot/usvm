// Case `x := y`
class Case1 {
    entrypoint() {
        let x = 52; // x: number
        let y = x; // y: number
        infer(y);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "number"
}

// ----------------------------------------

// Case `x := y.f`
class Case2 {
    entrypoint(y: any) {
        let x = y.f; // y: { f: any }
        infer(y);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "Object { f: any }"
}

// ----------------------------------------

// Case `x := y.f`
class Case3 {
    entrypoint() {
        let y = {f: 42}; // y: { f: number }
        let x = y.f; // x: number
        infer(x);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "number"
}

// ----------------------------------------

// Case `x.f := y`
class Case4 {
    entrypoint(x: any) {
        let y = 100; // y: number
        x.f = y; // x: { f: number }
        infer(x);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "Object { f: number }"
}

// ----------------------------------------

// Case `x.f := y`
class Case5 {
    entrypoint(x: any) {
        let y = {t: 42}; // y: { t: number }
        x.f = y; // x: { f: { t: number } }
        infer(x);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "Object { f: { t: number } }"
}

// ----------------------------------------

// Case `x := y[i]`
class Case6 {
    entrypoint(y: any) {
        let x = y[0]; // y: Array<any>
        infer(y);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "Array<any>"
}

// ----------------------------------------

// Case `x := y[i]`
class Case7 {
    entrypoint() {
        let y = [42]; // y: Array<number>
        let x = y[0]; // x: number
        infer(x);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "number"
}

// ----------------------------------------

// Case `x[i] := y`
class Case8 {
    entrypoint(x: any) {
        let y = 100; // y: number
        x[0] = y; // x: Array<number>
        infer(x);
    }

    infer(a: any) {
        console.log(a);
    }

    EXPECTED_ARG_0 = "Array<number>"
}

// NOTES:
// cases 6 and 8 contain broken array representation (though case 7 is fine)
