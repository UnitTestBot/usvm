export class SparseWitnessSource {
    static sparseResult(): number[] {
        const values = [7, 11];
        delete values[0];
        return values;
    }

    static explicitUndefined(): any[] {
        return [undefined, 11];
    }

    static scalarResult(): number {
        const values = [7, 11];
        delete values[0];
        return values[0] === undefined && values[1] === 11 ? 1 : 0;
    }
}
