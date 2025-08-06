class ReachabilityChecker {
    // A target for this function will be 'if(x) -> if (y > 14) -> return 1'
    simpleFunction(x: boolean, y: number): number {
        if (x) {
            if (y > 14) {
                return 1;
            } else {
                return 2;
            }
        } else {
            return 3;
        }
    }
}
