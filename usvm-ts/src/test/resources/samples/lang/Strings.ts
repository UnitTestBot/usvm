// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Strings {
    concatenateStrings(a: string, b: string): string {
        return a + b;
    }

    stringWithNumber(s: string, n: number): string {
        return s + n;
    }

    getStringLength(s: string): number {
        return s.length;
    }

    getCharAt(s: string, index: number): string {
        return s.charAt(index);
    }

    getSubstring(s: string, start: number, end: number): string {
        return s.substring(start, end);
    }

    findIndexOf(s: string, searchString: string): number {
        return s.indexOf(searchString);
    }

    compareStrings(a: string, b: string): number {
        if (a === b) return 1;
        if (a < b) return 2;
        if (a > b) return 3;
        return 0;
    }

    templateLiteral(name: string, age: number): string {
        return `Hello ${name}, you are ${age} years old`;
    }

    stringIncludes(s: string, searchString: string): boolean {
        return s.includes(searchString);
    }

    stringStartsWith(s: string, searchString: string): boolean {
        return s.startsWith(searchString);
    }

    stringEndsWith(s: string, searchString: string): boolean {
        return s.endsWith(searchString);
    }

    stringToUpperCase(s: string): string {
        return s.toUpperCase();
    }

    stringToLowerCase(s: string): string {
        return s.toLowerCase();
    }

    stringTrim(s: string): string {
        return s.trim();
    }

    stringSplit(s: string, separator: string): string[] {
        return s.split(separator);
    }

    stringReplace(s: string, searchValue: string, replaceValue: string): string {
        return s.replace(searchValue, replaceValue);
    }
}
