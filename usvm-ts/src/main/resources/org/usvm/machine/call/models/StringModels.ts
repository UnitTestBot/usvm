declare class StringModelPrimitives {
    static length(receiver: string): number;
    static codeUnitAt(receiver: string, index: number): number;
    static fromCodeUnit(codeUnit: number): string;
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

        let integer = 0;
        if (value > 0) {
            while (integer + 1 <= value) {
                integer++;
            }
        } else {
            while (integer - 1 >= value) {
                integer--;
            }
        }

        return integer;
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

        let integer = 0;
        while (integer + 1 <= value) {
            integer++;
        }

        return integer;
    }
}
