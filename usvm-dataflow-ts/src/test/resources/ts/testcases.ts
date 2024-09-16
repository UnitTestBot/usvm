// Case `x := y`
class CaseAssignLocalToLocal {
    entrypoint() {
        let x = 52; // x: number
        let y = x; // y: number
        infer(y);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := y.f`
class CaseAssignFieldToLocal1 {
    entrypoint(y: any) {
        let x = y.f; // y: { f: any }
        infer(y);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: any }";
    }
}

// ----------------------------------------

// Case `x := y.f`
class CaseAssignFieldToLocal2 {
    entrypoint() {
        let y = {f: 42}; // y: { f: number }
        let x = y.f; // x: number
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := a.f`
class CaseAssignFieldToLocal3 {
    entrypoint(y: any) {
        infer(y);
    }

    infer(a: any) {
        let x = a.f; // a: { f: any }
        const EXPECTED_ARG_0 = "Object { f: any }";
    }
}

// ----------------------------------------

// Case `x.f := y`
class CaseAssignLocalNumberToField {
    entrypoint(x: any) {
        let y = 100; // y: number
        x.f = y; // x: { f: number }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: number }";
    }
}

// ----------------------------------------

// Case `x.f := y`
class CaseAssignLocalObjectToField {
    entrypoint(x: any) {
        let y = { t: 32 }; // y: { t: number }
        x.f = y; // x: { f: { t: number } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { t: number } }";
    }
}

// ----------------------------------------

// Case `y := [...]`
class CaseAssignArrayToLocal {
    entrypoint() {
        let y = [1, 2, 3]; // y: Array<number>
        infer(y);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Array<number>";
    }
}

// ----------------------------------------

// Case `x := y[i]`
class CaseAssignArrayElementToLocal1 {
    entrypoint() {
        let y = [33]; // y: Array<number>
        let x = y[0]; // y: Array<any>
        infer(y);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Array<number>";
    }
}

// ----------------------------------------

// Case `x := y[i]`
class CaseAssignArrayElementToLocal2 {
    entrypoint() {
        let y = [22]; // y: Array<number>
        let x = y[0]; // x: number
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := y[i]`
class CaseAssignArgumentArrayElementToLocal1 {
    entrypoint(y: number[]) {
        let x = y[0]; // y: Array<any>
        infer(y);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Array<any>";
    }
}

// ----------------------------------------

// Case `x := y[i]`
class CaseAssignArgumentArrayElementToLocal2 {
    entrypoint(y: number[]) {
        let x = y[0]; // x: any
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "any";
    }
}

// ----------------------------------------

// Case `x[i] := y`
class CaseAssignLocalToArrayElementNumber {
    entrypoint(x: any[]) {
        let y = 100; // y: number
        x[0] = y; // x: Array<number>
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Array<any | number>";
    }
}

// ----------------------------------------

interface ICustom {
    a: number;
    b: string;
}

// Case `x := y as T`
class CaseCastToInterface {
    entrypoint(y: any) {
        let x = y as ICustom; // x: ICustom
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "ICustom";
    }
}

// ----------------------------------------

// Case `x := y as T[]`
class CaseCastToArrayInterface {
    entrypoint(y: any) {
        let x = y as ICustom[]; // x: Array<ICustom>
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Array<ICustom>";
    }
}

// ----------------------------------------

// Case `x := y + z`
class CaseAddNumbers {
    entrypoint() {
        let y = 5; // y: number
        let z = 10; // z: number
        let x = y + z; // x: number
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := y && z`
class CaseLogicalAndBooleans {
    entrypoint() {
        let y = true; // y: boolean
        let z = false; // z: boolean
        let x = y && z; // x: boolean
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "boolean";
    }
}

// ----------------------------------------

// Case `x := y || z`
class CaseLogicalOrBooleanAndString {
    entrypoint() {
        let y = false; // y: boolean
        let z = "default"; // z: string
        let x = y || z; // x: string
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "boolean | string";
    }
}

// ----------------------------------------

// Case `x := y + z`
class CaseAddStrings {
    entrypoint() {
        let y = "Hello, "; // y: string
        let z = "World!"; // z: string
        let x = y + z; // x: string
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "string";
    }
}

// ----------------------------------------

// Case `x := number + string`
class CaseAddNumberToString {
    entrypoint() {
        let y = 12; // y: number
        let z = " is the answer"; // z: string
        let x = y + z; // x: string
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "string";
    }
}

// ----------------------------------------

// Case `x := string + number`
class CaseAddStringToNumber {
    entrypoint() {
        let y = "The answer is "; // y: string
        let z = 13; // z: number
        let x = y + z; // x: string
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "string";
    }
}

// ----------------------------------------

// Case `x := string - number`
class CaseSubtractNumberFromString {
    entrypoint() {
        let y = "73"; // y: string
        let z = 10; // z: number
        let x = y - z; // x: number (63)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := number - string`
class CaseSubtractStringFromNumber {
    entrypoint() {
        let y = 96; // y: number
        let z = "51"; // z: string
        let x = y - z; // x: number (45)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := string * number`
class CaseMultiplyStringByNumber {
    entrypoint() {
        let y = "100"; // y: string
        let z = 30; // z: number
        let x = y * z; // x: number (3000)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := number * string`
class CaseMultiplyNumberByString {
    entrypoint() {
        let y = 40; // y: number
        let z = "500"; // z: string
        let x = y * z; // x: number (20000)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := boolean + number`
class CaseAddBooleanToNumber {
    entrypoint() {
        let y = true; // y: boolean
        let z = 1; // z: number
        let x = y + z; // x: number (2, as true is coerced to 1)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := number + boolean`
class CaseAddNumberToBoolean {
    entrypoint() {
        let y = 1; // y: number
        let z = false; // z: boolean
        let x = y + z; // x: number (1, as false is coerced to 0)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := null + number`
class CaseAddNullToNumber {
    entrypoint() {
        let y = null; // y: null
        let z = 105; // z: number
        let x = y + z; // x: number (10, as null is coerced to 0)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := number + null`
class CaseAddNumberToNull {
    entrypoint() {
        let y = 115; // y: number
        let z = null; // z: null
        let x = y + z; // x: number (10, as null is coerced to 0)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := undefined + number`
class CaseAddUndefinedToNumber {
    entrypoint() {
        let y = undefined; // y: undefined
        let z = 125; // z: number
        let x = y + z; // x: number (NaN)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := number + undefined`
class CaseAddNumberToUndefined {
    entrypoint() {
        let y = 135; // y: number
        let z = undefined; // z: undefined
        let x = y + z; // x: number (NaN)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := string / number`
class CaseDivideStringByNumber {
    entrypoint() {
        let y = "185"; // y: string
        let z = 5; // z: number
        let x = y / z; // x: number (37)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `x := number / string`
class CaseDivideNumberByString {
    entrypoint() {
        let y = 195; // y: number
        let z = "5"; // z: string
        let x = y / z; // x: number (39)
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "number";
    }
}

// ----------------------------------------

// Case `return x`
class CaseReturnNumber {
    entrypoint() {
        infer();
    }

    infer(): any {
        const EXPECTED_RETURN = "number";
        let x = 93; // x: number
        return x;
    }
}

// ----------------------------------------

 // Case `return arg`
 class CaseReturnArgumentNumber {
     entrypoint() {
         let x = 94; // x: number
         infer(x);
     }

     infer(a: any): any {
         const EXPECTED_RETURN = "number";
         return a;
     }
 }

// ----------------------------------------

// Case `return obj`
class CaseReturnObject {
    entrypoint() {
         infer();
    }

    infer(): any {
        const EXPECTED_RETURN = "Object { f: number }";
        let x = { f: 95 }; // x: Object { f: number }
        return x;
    }
}

// ----------------------------------------

// Case `return obj`
class CaseReturnArgumentObject {
    entrypoint() {
        let x = { f: 96 }; // x: Object { f: number }
        infer(x);
    }

    infer(a: any): any {
        const EXPECTED_RETURN = "Object { f: number }";
        return a;
    }
}

// Case `x.f[0].g = y`
class CaseAssignToNestedObjectField {
    entrypoint(x: any) {
        let y = 134; // y: number
        x.f[0].g = y; // x: { f: Array<{ g: number }> }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Array<Object { g: number }> }"
    }
}

// ----------------------------------------

// Case `x.f.g.h = y`
class CaseAssignDeeplyNestedField {
    entrypoint(x: any) {
        let y = "abc"; // y: string
        x.f.g.h = y; // x: { f: { g: { h: string } } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { g: Object { h: string } } }"
    }
}

// ----------------------------------------

// Case `x.f[i].g.h = y`
class CaseAssignToArrayObjectField {
    entrypoint(x: any) {
        let y = false; // y: boolean
        x.f[2].g.h = y; // x: { f: Array<{ g: { h: boolean } }> }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Array<Object { g: Object { h: boolean } }> }"
    }
}

// ----------------------------------------

// Case `x[i].f.g = y`
class CaseAssignArrayFieldToNestedObject {
    entrypoint(x: any) {
        let y = 219; // y: number
        x[0].f.g = y; // x: Array<{ f: { g: number } }>
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Array<{ f: Object { g: number } }>"
    }
}

// ----------------------------------------

// Case `x.f[i][j] = y`
class CaseAssignToMultiDimensionalArray {
    entrypoint(x: any) {
        let y = "data"; // y: string
        x.f[1][2] = y; // x: { f: Array<Array<string>> }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Array<Array<string>> }"
    }
}

// ----------------------------------------

// Case `x.f[0].g[1].h = y`
class CaseAssignToComplexNestedArrayField {
    entrypoint(x: any) {
        let y = true; // y: boolean
        x.f[0].g[1].h = y; // x: { f: Array<{ g: Array<{ h: boolean }> }> }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Array<Object { g: Array<Object { h: boolean }> }> }"
    }
}

// ----------------------------------------

// Case `x.f.g.h[i] = y`
class CaseAssignToArrayInNestedObject {
    entrypoint(x: any) {
        let y = 3.14; // y: number
        x.f.g.h[2] = y; // x: { f: { g: { h: Array<number> } } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { g: Object { h: Array<number> } } }"
    }
}

// ----------------------------------------

// Case `x.f[0].g.h[i] = y`
class CaseAssignToArrayInDeeplyNestedObject {
    entrypoint(x: any) {
        let y = null; // y: null
        x.f[0].g.h[3] = y; // x: { f: Array<{ g: { h: Array<null> } }> }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Array<Object { g: Object { h: Array<null> } }> }"
    }
}

// ----------------------------------------

// Case `x.f.g[i].h.j = y`
class CaseAssignToDeeplyNestedObjectArray {
    entrypoint(x: any) {
        let y = "nested"; // y: string
        x.f.g[1].h.j = y; // x: { f: { g: Array<{ h: { j: string } }> } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { g: Array<Object { h: Object { j: string } }> } }"
    }
}

// ----------------------------------------

// Case `x.f.g.h[0][i] = y`
class CaseAssignToMultiDimensionalArrayField {
    entrypoint(x: any) {
        let y = 99; // y: number
        x.f.g.h[0][1] = y; // x: { f: { g: { h: Array<Array<number>> } } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { g: { h: Array<Array<number>> } } }"
    }
}

// ----------------------------------------

class MyType {
    f: number = 15;
}

// Case `x = new T()`
class CaseNew {
    entrypoint() {
        let y = new MyType(); // y: { f: number }
        infer(y);
    }

    infer(a: any): any {
        const EXPECTED_ARG_0 = "MyType { f: number }"
    }
}

// ----------------------------------------

class CaseUnion {
    entrypoint() {
        let x: string | number = "str"; // x: string
        x = 42; // x: number
        infer(x);
    }

    infer(a: any): any {
        const EXPECTED_ARG_0 = "number";
    }
}

class CaseArgumentUnion {
    entrypoint(x: string | number) {
        x = "kek";
        x = 42;
        infer(x);
    }

    infer(a: any): any {
        const EXPECTED_ARG_0 = "number";
    }
}

class CaseUnion2 {
    entrypoint() {
        let y = "str";
        let x: string | number = y;
        x = 42;
        infer(y);
    }

    infer(a: any): any {
        const EXPECTED_ARG_0 = "string";
    }
}

class CaseArgumentUnion2 {
    entrypoint(y: string) {
        let x: string | number = y;
        x = 42;
        infer(y);
    }

    infer(a: any): any {
        const EXPECTED_ARG_0 = "any";
    }
}

// ----------------------------------------

class CaseAliasChain1 {
    entrypoint(x: any) {
        let y = x.f; // x: { f: any }
        y.g = 42; // x: { f: { g: number } }
        infer(x);
    }

    infer(a: any): any {
        const EXPECTED_ARG_0 = "Object { f: Object { g: number } }"
    }
}

// Case `x.f := number`
class CaseAssignNumberToNestedField {
    entrypoint(x: any) {
        x.f.g = 100; // x: { f: { g: number } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { g: number } }";
    }
}

// Case `x.f := (y: number)`
class CaseAssignLocalNumberToNestedField {
    entrypoint(x: any) {
        let y = 98; // y: number
        x.f.g = y; // x: { f: { g: number } }
        infer(x);
    }

    infer(a: any) {
        const EXPECTED_ARG_0 = "Object { f: Object { g: number } }";
    }
}
