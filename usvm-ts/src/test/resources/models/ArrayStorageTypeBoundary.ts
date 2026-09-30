// @ts-nocheck

export class ArrayStorageTypeBoundary {
    readUnknown(values: unknown): unknown {
        return values[0];
    }

    writeUnknown(values: unknown): number {
        values[0] = 7;
        return 1;
    }

    readUint16(values: Uint16Array): number {
        return values[0];
    }

    lengthUint8(values: Uint8Array): number {
        return values.length;
    }
}
