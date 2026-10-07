export class StringModelBackingBoundary {
    equalDerived(value: string): boolean {
        const left = value + "0123456789";
        const right = value + "0123456789";

        return left === right;
    }

    derivedLength(value: string): number {
        return (value + "0123456789").length;
    }

    returnedDerived(value: string): string {
        return value + "0123456789";
    }
}
