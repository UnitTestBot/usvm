declare class ArrayModelPrimitives {
    static grow(receiver: any[], length: number): void;
    static allocate(length: number): any[];
    static allocateLike(receiver: any[], length: number): any[];
}

export class ArrayModels {
    static fromLength(length: number): any[] {
        return ArrayModelPrimitives.allocate(length);
    }

    static pop(receiver: any[]): any {
        const length = receiver.length;
        if (length === 0) {
            return undefined;
        }

        const result = receiver[length - 1];
        receiver.length = length - 1;
        return result;
    }

    static indexOf(receiver: any[], searchElement: any, fromIndex: number): number {
        const length = receiver.length;
        if (length === 0) {
            return -1;
        }

        const start = ArrayModels.normalizeRelativeIndex(fromIndex, length);
        if (start === Infinity || start >= length) {
            return -1;
        }

        let index = start >= 0 ? start : length + start;
        if (index < 0) {
            index = 0;
        }

        while (index < length) {
            if (receiver[index] === searchElement) {
                return index;
            }

            index++;
        }

        return -1;
    }

    static includes(receiver: any[], searchElement: any, fromIndex: number): boolean {
        const length = receiver.length;
        if (length === 0) {
            return false;
        }

        const start = ArrayModels.normalizeRelativeIndex(fromIndex, length);
        if (start === Infinity || start >= length) {
            return false;
        }

        let index = start >= 0 ? start : length + start;
        if (index < 0) {
            index = 0;
        }

        while (index < length) {
            const element = receiver[index];
            if (element === searchElement || (element !== element && searchElement !== searchElement)) {
                return true;
            }

            index++;
        }

        return false;
    }

    static lastIndexOf(receiver: any[], searchElement: any, fromIndex: number): number {
        const length = receiver.length;
        if (length === 0 || fromIndex === -Infinity) {
            return -1;
        }

        let start = fromIndex !== fromIndex ? 0 : fromIndex;
        if (start !== Infinity) {
            start = start < 0 ? -Math.floor(-start) : Math.floor(start);
            if (start === 0) {
                start = 0;
            }
        }
        if (start < -length) {
            return -1;
        }

        let index = start >= length ? length - 1 : (start >= 0 ? start : length + start);
        while (index >= 0) {
            if (receiver[index] === searchElement) {
                return index;
            }

            index--;
        }

        return -1;
    }

    static push(receiver: any[], first: any, second: any, third: any, count: number): number {
        const length = receiver.length;
        const resultLength = length + count;
        ArrayModelPrimitives.grow(receiver, resultLength);
        if (count >= 1) receiver[length] = first;
        if (count >= 2) receiver[length + 1] = second;
        if (count >= 3) receiver[length + 2] = third;
        return resultLength;
    }

    static fill(receiver: any[], value: any, start: number, end: number): any[] {
        const length = receiver.length;
        let index = ArrayModels.normalizeSliceIndex(start, length);
        const final = ArrayModels.normalizeSliceIndex(end, length);
        while (index < final) {
            receiver[index] = value;
            index++;
        }
        return receiver;
    }

    static reverse(receiver: any[]): any[] {
        const length = receiver.length;
        let lower = 0;
        while (lower < Math.floor(length / 2)) {
            const upper = length - lower - 1;
            const value = receiver[lower];
            receiver[lower] = receiver[upper];
            receiver[upper] = value;
            lower++;
        }
        return receiver;
    }

    static unshift(receiver: any[], first: any, second: any, third: any, count: number): number {
        const length = receiver.length;
        const resultLength = length + count;
        ArrayModelPrimitives.grow(receiver, resultLength);
        let index = length;
        while (index > 0) {
            index--;
            receiver[index + count] = receiver[index];
        }
        if (count >= 1) receiver[0] = first;
        if (count >= 2) receiver[1] = second;
        if (count >= 3) receiver[2] = third;
        return resultLength;
    }

    static slice(receiver: any[], start: number, end: number): any[] {
        const length = receiver.length;
        const from = ArrayModels.normalizeSliceIndex(start, length);
        const to = ArrayModels.normalizeSliceIndex(end, length);
        const count = to > from ? to - from : 0;
        const result = ArrayModelPrimitives.allocateLike(receiver, count);
        let index = 0;
        while (index < count) {
            result[index] = receiver[from + index];
            index++;
        }
        return result;
    }

    static concat(receiver: any[], other: any[]): any[] {
        const receiverLength = receiver.length;
        const otherLength = other.length;
        const result = ArrayModelPrimitives.allocateLike(receiver, receiverLength + otherLength);
        let index = 0;
        while (index < receiverLength) {
            result[index] = receiver[index];
            index++;
        }
        let otherIndex = 0;
        while (otherIndex < otherLength) {
            result[receiverLength + otherIndex] = other[otherIndex];
            otherIndex++;
        }
        return result;
    }

    private static normalizeRelativeIndex(fromIndex: number, length: number): number {
        if (fromIndex !== fromIndex) {
            return 0;
        }

        if (fromIndex === Infinity || fromIndex >= length) {
            return length;
        }

        if (fromIndex === -Infinity || fromIndex <= -length) {
            return -length;
        }

        const integer = fromIndex < 0 ? -Math.floor(-fromIndex) : Math.floor(fromIndex);
        return integer === 0 ? 0 : integer;
    }

    private static normalizeSliceIndex(index: number, length: number): number {
        if (index !== index || index === -Infinity) {
            return 0;
        }
        if (index === Infinity) {
            return length;
        }

        const integer = index < 0 ? -Math.floor(-index) : Math.floor(index);
        if (integer < 0) {
            const relative = length + integer;
            return relative < 0 ? 0 : relative;
        }
        return integer > length ? length : integer;
    }
}
