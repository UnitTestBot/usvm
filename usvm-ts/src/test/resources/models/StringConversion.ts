class StringConversion {
    typed(value: string): string {
        return String(value);
    }

    generic(value: string): string {
        return this.convert(value);
    }

    private convert<K>(value: K): string {
        return String(value);
    }

    boolean(value: boolean): string {
        return String(value);
    }

    constants(): string {
        return String() + "|" + String(undefined) + "|" + String(null) + "|" +
            String(true) + "|" + String(false) + "|" + String(-0) + "|" + String(1.5);
    }

    codeUnits(): string {
        return this.convert("A\u0000\uD800\uDC00\uDFFF");
    }

    shadowedParameter(value: string): string {
        return this.withConverter((input?: any): string => "shadow", value);
    }

    private withConverter(String: (value?: any) => string, value: string): string {
        return String(value);
    }

    shadowedLocal(value: string): string {
        const String: (value?: any) => string = (input?: any): string => "local";
        return String(value);
    }

    unsupportedObject(): string {
        return String({ value: "a" });
    }

    symbolicNumber(value: number): string {
        return String(value);
    }
}
