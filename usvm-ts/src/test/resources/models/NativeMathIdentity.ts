export const nativeDegreesToRadians = Math.PI / 180;
const nativePi = () => Math.PI;

export class NativeMathIdentity {
    static initializedConstant(): number {
        return nativeDegreesToRadians;
    }

    static closureConstant(): number {
        return nativePi();
    }

    static aliasIdentity(): number {
        const numeric = Math;
        return numeric === Math && typeof numeric === "object" ? 1 : 0;
    }

    static constants(): number {
        return Math.E === 2.718281828459045 &&
            Math.LN10 === 2.302585092994046 && Math.LN2 === 0.6931471805599453 &&
            Math.LOG10E === 0.4342944819032518 && Math.LOG2E === 1.4426950408889634 &&
            Math.PI === 3.141592653589793 && Math.SQRT1_2 === 0.7071067811865476 &&
            Math.SQRT2 === 1.4142135623730951 ? 1 : 0;
    }

    static localShadow(): number {
        const Math = { PI: 17 };
        return Math.PI;
    }

    static parameterShadow(): number {
        return NativeMathIdentity.readParameterShadow({ PI: 2 });
    }

    static readParameterShadow(Math: { PI: number }): number {
        Math.PI = 19;
        return Math.PI;
    }

    static writeConstant(): number {
        const numeric: any = Math;
        numeric.PI = 4;
        return numeric.PI;
    }

    static deleteConstant(): boolean {
        const numeric: any = Math;
        return delete numeric.PI;
    }
}
