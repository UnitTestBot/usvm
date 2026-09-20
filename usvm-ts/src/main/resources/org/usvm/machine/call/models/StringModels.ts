declare class StringModelPrimitives {
    static length(receiver: string): number;
    static codeUnitAt(receiver: string, index: number): number;
    static fromCodeUnit(codeUnit: number): string;
    static copyRange(receiver: string, start: number, end: number): string;
}

export class StringModels {
    static charAt(receiver: string, index: number): string {
        const length = StringModelPrimitives.length(receiver);
        const integerIndex = StringModels.normalizeCharIndex(index, length);
        if (integerIndex < 0 || integerIndex >= length) {
            return "";
        }

        const codeUnit = StringModelPrimitives.codeUnitAt(receiver, integerIndex);
        return StringModelPrimitives.fromCodeUnit(codeUnit);
    }

    static indexOf(receiver: string, searchString: string, position: number): number {
        const length = StringModelPrimitives.length(receiver);
        const searchLength = StringModelPrimitives.length(searchString);
        const start = StringModels.normalizePosition(position, length);

        if (searchLength === 0) {
            return start;
        }

        let index = start;
        while (index + searchLength <= length) {
            let searchIndex = 0;
            while (
                searchIndex < searchLength &&
                StringModelPrimitives.codeUnitAt(receiver, index + searchIndex) ===
                    StringModelPrimitives.codeUnitAt(searchString, searchIndex)
            ) {
                searchIndex++;
            }

            if (searchIndex === searchLength) {
                return index;
            }

            index++;
        }

        return -1;
    }

    static includes(receiver: string, searchString: string, position: number): boolean {
        return StringModels.indexOf(receiver, searchString, position) !== -1;
    }

    static charCodeAt(receiver: string, index: number): number {
        const length = StringModelPrimitives.length(receiver);
        const integerIndex = StringModels.normalizeCharIndex(index, length);
        if (integerIndex < 0 || integerIndex >= length) {
            return NaN;
        }

        return StringModelPrimitives.codeUnitAt(receiver, integerIndex);
    }

    static slice(receiver: string, start: number, end: number): string {
        const length = StringModelPrimitives.length(receiver);
        const from = StringModels.normalizeSliceIndex(start, length);
        const to = StringModels.normalizeSliceIndex(end, length);
        return StringModelPrimitives.copyRange(receiver, from, to < from ? from : to);
    }

    static toUpperCase(receiver: string): string {
        const length = StringModelPrimitives.length(receiver);
        let result = "";
        let index = 0;
        while (index < length) {
            let codeUnit = StringModelPrimitives.codeUnitAt(receiver, index);
            if (codeUnit >= 0x61 && codeUnit <= 0x7a) {
                codeUnit -= 0x20;
            }
            result += StringModelPrimitives.fromCodeUnit(codeUnit);
            index++;
        }
        return result;
    }

    static toLowerCase(receiver: string): string {
        const length = StringModelPrimitives.length(receiver);
        let result = "";
        let index = 0;
        while (index < length) {
            let codeUnit = StringModelPrimitives.codeUnitAt(receiver, index);
            if (codeUnit >= 0x41 && codeUnit <= 0x5a) {
                codeUnit += 0x20;
            }
            result += StringModelPrimitives.fromCodeUnit(codeUnit);
            index++;
        }
        return result;
    }

    static startsWith(receiver: string, searchString: string, position: number): boolean {
        const length = StringModelPrimitives.length(receiver);
        const searchLength = StringModelPrimitives.length(searchString);
        const start = StringModels.normalizePosition(position, length);
        if (start + searchLength > length) {
            return false;
        }

        let searchIndex = 0;
        while (searchIndex < searchLength) {
            if (
                StringModelPrimitives.codeUnitAt(receiver, start + searchIndex) !==
                StringModelPrimitives.codeUnitAt(searchString, searchIndex)
            ) {
                return false;
            }

            searchIndex++;
        }

        return true;
    }

    static endsWith(receiver: string, searchString: string, endPosition: number): boolean {
        const length = StringModelPrimitives.length(receiver);
        const searchLength = StringModelPrimitives.length(searchString);
        const end = StringModels.normalizePosition(endPosition, length);
        const start = end - searchLength;
        if (start < 0) {
            return false;
        }

        let searchIndex = 0;
        while (searchIndex < searchLength) {
            if (
                StringModelPrimitives.codeUnitAt(receiver, start + searchIndex) !==
                StringModelPrimitives.codeUnitAt(searchString, searchIndex)
            ) {
                return false;
            }

            searchIndex++;
        }

        return true;
    }

    static lastIndexOf(receiver: string, searchString: string, position: number): number {
        const length = StringModelPrimitives.length(receiver);
        const searchLength = StringModelPrimitives.length(searchString);
        let index = StringModels.normalizeLastPosition(position, length);
        if (index + searchLength > length) {
            index = length - searchLength;
        }

        while (index >= 0) {
            let searchIndex = 0;
            while (
                searchIndex < searchLength &&
                StringModelPrimitives.codeUnitAt(receiver, index + searchIndex) ===
                    StringModelPrimitives.codeUnitAt(searchString, searchIndex)
            ) {
                searchIndex++;
            }

            if (searchIndex === searchLength) {
                return index;
            }

            index--;
        }

        return -1;
    }

    private static normalizeCharIndex(value: number, length: number): number {
        if (value !== value) {
            return 0;
        }

        if (value === Infinity || value >= length) {
            return length;
        }

        if (value === -Infinity || value <= -length) {
            return -length;
        }

        return value < 0 ? -Math.floor(-value) : Math.floor(value);
    }

    private static normalizePosition(value: number, length: number): number {
        if (value !== value) {
            return 0;
        }

        if (value === Infinity || value >= length) {
            return length;
        }

        if (value === -Infinity || value <= 0) {
            return 0;
        }

        return Math.floor(value);
    }

    private static normalizeLastPosition(value: number, length: number): number {
        if (value !== value || value === Infinity || value >= length) {
            return length;
        }

        if (value === -Infinity || value <= 0) {
            return 0;
        }

        return Math.floor(value);
    }

    private static normalizeSliceIndex(value: number, length: number): number {
        if (value !== value) {
            return 0;
        }
        if (value === Infinity) {
            return length;
        }
        if (value === -Infinity) {
            return 0;
        }

        const integer = value < 0 ? -Math.floor(-value) : Math.floor(value);
        if (integer < 0) {
            return integer + length < 0 ? 0 : integer + length;
        }
        return integer > length ? length : integer;
    }
}
