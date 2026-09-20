export class ArrayModels {
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

        const start = ArrayModels.normalizeRelativeIndex(fromIndex, length);
        let index = start >= length ? length - 1 : (start >= 0 ? start : length + start);
        while (index >= 0) {
            if (receiver[index] === searchElement) {
                return index;
            }

            index--;
        }

        return -1;
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

        let integer = 0;
        if (fromIndex > 0) {
            while (integer + 1 <= fromIndex) {
                integer++;
            }
        } else {
            while (integer - 1 >= fromIndex) {
                integer--;
            }
        }

        return integer;
    }
}
