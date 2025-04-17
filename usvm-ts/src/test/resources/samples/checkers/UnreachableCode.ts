class UnreachableCode {
    simpleUnreachableBranch(value: number): number {
        if (value > 4) {
            if (value < 2) {
                return -1
            } else {
                return 1
            }
        } else {
            return 2
        }
    }

    unreachableCodeWithCallsInside(value: number): number {
        anotherValue = this.simpleUnreachableBranch(value)
        if (anotherValue > 2) {
            if (anotherValue < 1) {
                return -1 // Unreachable code
            } else {
                return 1 // Unreachable if we execute simpleUnreachableBranch call and reachable otherwise
            }
        }

        return
    }
}
