// @ts-nocheck
class StringRelational {
    literalLess(): number {
        return "a" < "b" && "b" > "a" ? 1 : 0;
    }

    unsignedCodeUnitOrder(): number {
        return "\u8000" > "\u7fff" && "\u8000" < "\uffff" ? 1 : 0;
    }

    lengthOrder(): number {
        return "a" < "aa" && "aa" > "a" ? 1 : 0;
    }

    mixedNumberAndString(): number {
        return "2" < 10 && 10 > "2" && !("2" < 1) ? 1 : 0;
    }

    symbolicLess(left: string, right: string): number {
        return left < right ? 1 : 2;
    }

    conditionalLeft(useString: boolean, value: string): number {
        const left: string | number = useString ? value : 1;
        return left < "b" ? 1 : 2;
    }
}
