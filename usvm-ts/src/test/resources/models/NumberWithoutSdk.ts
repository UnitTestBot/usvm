// @ts-nocheck

class NumberWithoutSdk {
    construct(value: number): number {
        const wrapper = new Number(value);

        return wrapper.valueOf();
    }
}
