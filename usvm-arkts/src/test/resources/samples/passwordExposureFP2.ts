function getUserData() {}

function getUserPassword() {
    let password = getUserData()
    // validate password
    return password
}

function processUserData1(time: number) {
    console.log(time)
    let loggingOn = true
    let password = getUserPassword()
    if (loggingOn == true) {
        console.log(password)
    }
}

function processUserData2(time: number) {
    console.log(time)
    let loggingOn = false
    let password = getUserPassword()
    if (loggingOn == true) {
        console.log(password)
    }
}

// no FP
function usage1() {
    processUserData1(1)
}

// FP
function usage2() {
    processUserData2(1)
}