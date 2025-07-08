class Less {
    lessNumbers(a: number, b: number): number {
        if (a < b) {
            return a;
        }

        if (b < a) {
            return b;
        }

        return 0; // Return 0 if they are equal
    }

    lessBooleans(a: boolean, b: boolean): boolean {
        if (a < b) {
            return a;
        }

        if (b < a) {
            return b;
        }

        return false; // Return false if they are equal
    }

    lessMixed(a: number, b: boolean): number {
        if (a < b) {
            return a;
        }

        if (b < a) {
            return b ? 1 : 0; // Convert boolean to number
        }

        return 0; // Return 0 if they are equal
    }

    lessRefs(a: object, b: object): boolean {
        if (a < b) {
            return true; // Reference comparison, not value
        }

        if (b < a) {
            return false; // Reference comparison, not value
        }

        return false; // Return false if they are equal
    }

    lessUnknown(a, b): boolean {
        if (a < b) {
            return true; // Reference comparison, not value
        }

        if (b < a) {
            return false; // Reference comparison, not value
        }

        return false; // Return false if they are equal
    }
}


