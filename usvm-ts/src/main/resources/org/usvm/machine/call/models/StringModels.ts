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

    static substring(receiver: string, start: number, end: number): string {
        const length = StringModelPrimitives.length(receiver);
        const from = StringModels.normalizePosition(start, length);
        const to = StringModels.normalizePosition(end, length);
        return from <= to
            ? StringModelPrimitives.copyRange(receiver, from, to)
            : StringModelPrimitives.copyRange(receiver, to, from);
    }

    static trim(receiver: string): string {
        const length = StringModelPrimitives.length(receiver);
        let start = 0;
        let end = length;
        while (start < end && StringModels.isWhitespace(StringModelPrimitives.codeUnitAt(receiver, start))) {
            start++;
        }
        while (end > start && StringModels.isWhitespace(StringModelPrimitives.codeUnitAt(receiver, end - 1))) {
            end--;
        }
        return StringModelPrimitives.copyRange(receiver, start, end);
    }

    static trimStart(receiver: string): string {
        const length = StringModelPrimitives.length(receiver);
        let start = 0;
        while (start < length && StringModels.isWhitespace(StringModelPrimitives.codeUnitAt(receiver, start))) {
            start++;
        }
        return StringModelPrimitives.copyRange(receiver, start, length);
    }

    static trimEnd(receiver: string): string {
        let end = StringModelPrimitives.length(receiver);
        while (end > 0 && StringModels.isWhitespace(StringModelPrimitives.codeUnitAt(receiver, end - 1))) {
            end--;
        }
        return StringModelPrimitives.copyRange(receiver, 0, end);
    }

    static replaceAll(receiver: string, search: string, replacement: string): string {
        const length = StringModelPrimitives.length(receiver);
        const searchLength = StringModelPrimitives.length(search);
        let result = "";
        let endOfLastMatch = 0;
        let position = 0;
        while (position + searchLength <= length) {
            let offset = 0;
            while (offset < searchLength &&
                StringModelPrimitives.codeUnitAt(receiver, position + offset) ===
                    StringModelPrimitives.codeUnitAt(search, offset)) {
                offset++;
            }
            if (offset === searchLength) {
                result += StringModelPrimitives.copyRange(receiver, endOfLastMatch, position);
                result += StringModels.substitution(receiver, search, replacement, position);
                endOfLastMatch = position + searchLength;
                position = endOfLastMatch + (searchLength === 0 ? 1 : 0);
            } else {
                position++;
            }
        }
        return result + StringModelPrimitives.copyRange(receiver, endOfLastMatch, length);
    }
    private static substitution(receiver: string, search: string, replacement: string, position: number): string {
        const replacementLength = StringModelPrimitives.length(replacement);
        let result = "";
        let index = 0;
        while (index < replacementLength) {
            const code = StringModelPrimitives.codeUnitAt(replacement, index);
            if (code === 36 && index + 1 < replacementLength) {
                const next = StringModelPrimitives.codeUnitAt(replacement, index + 1);
                if (next === 36) {
                    result += "$";
                    index += 2;
                    continue;
                }
                if (next === 38) {
                    result += search;
                    index += 2;
                    continue;
                }
                if (next === 96) {
                    result += StringModelPrimitives.copyRange(receiver, 0, position);
                    index += 2;
                    continue;
                }
                if (next === 39) {
                    const matchEnd = position + StringModelPrimitives.length(search);
                    result += StringModelPrimitives.copyRange(receiver, matchEnd, StringModelPrimitives.length(receiver));
                    index += 2;
                    continue;
                }
            }
            result += StringModelPrimitives.fromCodeUnit(code);
            index++;
        }
        return result;
    }

    private static isWhitespace(codeUnit: number): boolean {
        return (codeUnit >= 0x09 && codeUnit <= 0x0d)
            || codeUnit === 0x20 || codeUnit === 0xa0 || codeUnit === 0x1680
            || (codeUnit >= 0x2000 && codeUnit <= 0x200a)
            || codeUnit === 0x2028 || codeUnit === 0x2029 || codeUnit === 0x202f
            || codeUnit === 0x205f || codeUnit === 0x3000 || codeUnit === 0xfeff;
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
