function getNumber() {
    return 10
}

function getArray() {
    let n = getNumber()
    let arr = new Array(n);
    return arr
}

function safeGetArray() {
    let n = getNumber()
    if (n >= 10) {
        throw new Error ("Size is too large")
    }
    let arr = new Array(n);
    return arr
}