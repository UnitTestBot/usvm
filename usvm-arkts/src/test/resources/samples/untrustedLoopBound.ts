function getUserData() {
    return 10
}

function forLoop() {
    let count = getUserData()
    for (let index = 0; index < count; index++) {
        console.log(index);
    }
    return
}

function whileLoop() {
    let count = getUserData()
    let index = 0
    while (index < count) {
        console.log(index);
        index++;
    }
    return
}

function verifiedForLoop() {
    let count = getUserData()
    if (count >= 10) {
        throw new Error ("Loop bound too large!")
    }
    for (let index = 0; index < count; index++) {
        console.log(index);
    }
    return
}

function verifiedWhileLoop() {
    let count = getUserData()
    if (count >= 10) {
        throw new Error ("Loop bound too large!")
    }
    let index = 0
    while (index < count) {
        console.log(index);
        index++;
    }
    return
}