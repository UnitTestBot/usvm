// Case `x := y`
class CaseAssignLocalToLocalNumber {
    entrypoint() {
        let x = 52; // x: number
        let y = x; // y: number
        infer(y);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
}

// ----------------------------------------

// Case `x := y.f`
class CaseAssignFieldToLocalObject {
    entrypoint(y: any) {
        let x = y.f; // y: { f: any }
        infer(y);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "Object { f: any }"
}

// ----------------------------------------

// Case `x := y.f`
class CaseAssignFieldToLocalNumber {
    entrypoint() {
        let y = {f: 42}; // y: { f: number }
        let x = y.f; // x: number
        infer(x);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
}

// ----------------------------------------

// Case `x.f := y`
class CaseAssignLocalToFieldObject {
    entrypoint(x: any) {
        let y = 100; // y: number
        x.f = y; // x: { f: number }
        infer(x);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "Object { f: number }"
}

// ----------------------------------------

// Case `x.f := y`
class CaseAssignObjectToFieldObject {
    entrypoint(x: any) {
        let y = {t: 32}; // y: { t: number }
        x.f = y; // x: { f: { t: number } }
        infer(x);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "Object { f: { t: number } }"
}

// ----------------------------------------

// Case `x := y[i]`
class CaseAssignArrayElementToLocalArray {
    entrypoint(y: any) {
        let x = y[0]; // y: Array<any>
        infer(y);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "Array<any>"
}

// ----------------------------------------

// Case `x := y[i]`
class CaseAssignArrayElementToLocalNumber {
    entrypoint() {
        let y = [22]; // y: Array<number>
        let x = y[0]; // x: number
        infer(x);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
}

// ----------------------------------------

// Case `x[i] := y`
class CaseAssignLocalToArrayElementNumber {
    entrypoint(x: any) {
        let y = 100; // y: number
        x[0] = y; // x: Array<number>
        infer(x);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "Array<number>"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "ICustom"
}

// ----------------------------------------

// Case `x := y as T[]`
class CaseCastToArrayInterface {
    entrypoint(y: any) {
        let x = y as ICustom[]; // x: Array<ICustom>
        infer(x);
    }

    infer(a: any) {}

    EXPECTED_ARG_0 = "Array<ICustom>"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "boolean"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "boolean | string"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "string"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "string"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "string"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
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

    infer(a: any) {}

    EXPECTED_ARG_0 = "number"
}
