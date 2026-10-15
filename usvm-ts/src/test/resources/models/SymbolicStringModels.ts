export class SymbolicStringModels {
    trim(value: string): string {
        return value.trim();
    }

    trimStart(value: string): string {
        return value.trimStart();
    }

    trimEnd(value: string): string {
        return value.trimEnd();
    }

    trimSingle(value: string): number {
        if (value.length !== 1) return -1;

        return value.trim().length;
    }

    slice(value: string, start: number, end: number): string {
        return value.slice(start, end);
    }

    substring(value: string, start: number, end: number): string {
        return value.substring(start, end);
    }

    searches(value: string): number {
        const first = value.indexOf("\ud800");
        const last = value.lastIndexOf("\ud800");
        const included = value.includes("\ud800") ? 1 : 0;
        const starts = value.startsWith("\ud800") ? 1 : 0;
        const ends = value.endsWith("\ud800") ? 1 : 0;

        return first + 10 * last + 100 * included + 1000 * starts + 10000 * ends;
    }

    split(value: string): string {
        return value.split("\u0000", 3).join("|");
    }

    replaceAll(value: string): string {
        if (value.length > 2) return "";

        return value.replaceAll("\ud800", "$$$&");
    }

    boundedCopy(value: string): string {
        if (value.length !== 4) return "";

        return value.slice(1, 3) + "!";
    }
}
