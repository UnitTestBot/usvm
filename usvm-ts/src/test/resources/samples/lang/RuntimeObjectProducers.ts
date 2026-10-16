// @ts-nocheck

const runtimeModuleReceiver = this ? 17 : 31;

class RuntimeObjectProducers {
    lastValue: number = -1;

    private async fulfilled(selectZero: boolean): Promise<number> {
        this.lastValue = selectZero ? 0 : 5;
        return this.lastValue;
    }

    private async rejected(): Promise<number> {
        throw 23;
    }

    moduleReceiverIsTruthy(): number {
        return runtimeModuleReceiver;
    }

    fulfilledPromiseNegation(selectZero: boolean): number {
        const promise = this.fulfilled(selectZero);
        if (!promise) return -1;

        return this.lastValue + 101;
    }

    fulfilledPromiseCondition(selectZero: boolean): number {
        const promise = this.fulfilled(selectZero);
        if (promise) return this.lastValue + 201;

        return -1;
    }

    conditionalPromiseOrNull(usePromise: boolean): number {
        const value = usePromise ? this.fulfilled(true) : null;
        return value ? this.lastValue + 301 : 311;
    }

    conditionalPromiseOrUndefined(usePromise: boolean): number {
        const value = usePromise ? this.fulfilled(true) : undefined;
        return value ? this.lastValue + 501 : 511;
    }

    rejectedPromiseCondition(): number {
        const promise = this.rejected();
        return promise ? 401 : -1;
    }
}
