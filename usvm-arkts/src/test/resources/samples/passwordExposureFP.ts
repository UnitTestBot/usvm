function getUserData() {}

function getUserPassword() {
    let password = getUserData()
    // validate password
    return password
}

function processUserData(loggingOn: number) {
    let password = getUserPassword()
    if (loggingOn == 1) {
        console.log(password)
    }
}

// case 1: FP
function usage1() {
    processUserData(0)
}

// case2: no FP
function usage2() {
    processUserData(1)
}