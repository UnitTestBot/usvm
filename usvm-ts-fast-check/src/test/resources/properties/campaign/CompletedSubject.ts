export class BranchingCounter {
    value(input: number): number {
        if (input > 0) {
            return 1;
        } else {
            return 2;
        }
    }
}
