class Person {
}

function sumNumber(a) {
    return a + 1 + a
}

function divideNumber(a) {
    return a / 3 / a
}

function sumNumBool(a) {
    return a + true + a
}

function divideNumBool(a) {
    return a / true / a
}

function sumNumString(a) {
    return a + "true" + a
}

function divideNumString(a) {
    return a / "true" / a
}

function sumNumObj(a) {
    return a + new Person() + a
}

function divideNumObj(a) {
    return a / new Person() / a
}
