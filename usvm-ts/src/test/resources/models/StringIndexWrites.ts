export class StringIndexWrites {
    strictCaught(value: string, index: number): number {
        const before = value.charAt(0);
        const beforeLength = value.length;
        const target: any = value;
        let effects = 0;
        try {
            target[index] = (effects++, "x");
            return -1;
        } catch {
            if (effects !== 1 || value.length !== beforeLength || value.charAt(0) !== before) {
                return -2;
            }
            return index === 0 ? 1 : 2;
        }
    }

    strictKeys(): boolean {
        let caught = 0;
        const value: any = "abc";
        try { value["0"] = "x"; } catch { caught++; }
        try { value["01"] = "x"; } catch { caught++; }
        try { value[-1] = "x"; } catch { caught++; }
        try { value[100] = "x"; } catch { caught++; }
        try { value[0.5] = "x"; } catch { caught++; }
        try { value[NaN] = "x"; } catch { caught++; }
        try { value[Infinity] = "x"; } catch { caught++; }
        try { value["length"] = 1; } catch { caught++; }
        try { value["extra"] = "x"; } catch { caught++; }
        const typedValue = "abc";
        try { typedValue.length = 1; } catch { caught++; }
        return caught === 10 && value === "abc" && value[0] === "a" && typedValue.length === 3;
    }

    rightSideThrows(): number {
        const value: any = "abc";
        try {
            value[0] = StringIndexWrites.fail();
        } catch (error) {
            return error === 77 ? 1 : -1;
        }
        return -2;
    }

    private static fail(): string {
        throw 77;
    }

    uncaught(value: string, index: number): string {
        const target: any = value;
        target[index] = "x";
        return value;
    }
}
