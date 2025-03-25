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
}