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

    inclusiveNullAndNumber(): number {
        return null <= 0 && null >= 0 && 0 <= null && 0 >= null &&
            !(null <= -1) && !(null >= 1) ? 1 : 0;
    }

    inclusiveMixedStringAndNumber(): number {
        const two: any = "2";
        return two <= 2 && two >= 2 && 2 <= two && 2 >= two ? 1 : 0;
    }

    inclusiveUnordered(): number {
        const missing: any = undefined;
        const nan = 0 / 0;
        return !(missing <= 0) && !(missing >= 0) &&
            !(nan <= 0) && !(nan >= 0) ? 1 : 0;
    }

    symbolicLessOrEqual(left: string, right: string): number {
        return left <= right ? 1 : 2;
    }

    symbolicGreaterOrEqual(left: string, right: string): number {
        return left >= right ? 1 : 2;
    }

    symbolicEqualInclusive(left: string, right: string): number {
        if (left !== right) {
            return 0;
        }

        return left <= right && left >= right ? 1 : 2;
    }

    symbolicLess(left: string, right: string): number {
        return left < right ? 1 : 2;
    }

    conditionalLeft(useString: boolean, value: string): number {
        const left: string | number = useString ? value : 1;
        if (left < "b") {
            return 1;
        }

        if (useString) {
            return 3;
        }

        return 2;
    }
}
