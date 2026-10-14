// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class VoidMutation {
    value: number = 0;

    change(): void {
        this.value = 7;
    }
}

class VoidAssignment {
    assignmentResult(): number {
        const instance = new VoidMutation();
        const result = instance.change();
        const observation = { result, value: instance.value };
        if (observation.result === undefined && observation.value === 7) {
            return 1;
        }
        return 0;
    }
}
