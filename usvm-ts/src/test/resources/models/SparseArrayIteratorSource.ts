export class SparseArrayIteratorSource {
    static reverseAsymmetricHoles(): number {
        const values = [, 7, 11];
        const alias = values.reverse();
        const copy = [...values];
        return alias === values && copy.length === 3 && copy[0] === 11 &&
            copy[1] === 7 && copy[2] === undefined ? 1 : 0;
    }

    static unshiftHoles(): number {
        const values = [, 11];
        const length = values.unshift(7);
        const copy = [...values];
        return length === 3 && copy.length === 3 && copy[0] === 7 &&
            copy[1] === undefined && copy[2] === 11 ? 1 : 0;
    }

    static literalHoles(): number {
        const values = [7, , 11];
        const copy = [...values];
        return values[1] === undefined && copy.length === 3 && copy[0] === 7 &&
            copy[1] === undefined && copy[2] === 11 ? 1 : 0;
    }

    static newArrayHoles(): number {
        const values = new Array<number>(3);
        values[1] = 7;
        const copy = [...values];
        return copy.length === 3 && copy[0] === undefined && copy[1] === 7 && copy[2] === undefined ? 1 : 0;
    }

    static deletedSlot(): number {
        const values = [7, 11];
        const removed = delete values[1];
        const copy = [...values];
        return removed && values[1] === undefined && copy.length === 2 && copy[0] === 7 && copy[1] === undefined ? 1 : 0;
    }

    static rewriteDeletedSlot(): number {
        const values = [7, 11];
        const alias: any[] = values;
        delete values[0];
        alias[0] = true;
        const copy = [...alias];
        return copy.length === 2 && copy[0] === true && copy[1] === 11 ? 1 : 0;
    }

    static movesPreserveHoles(): number {
        const values = [1, , 3];
        values.reverse();
        values.unshift(7);
        const removed = values.shift();
        const copy = [...values];
        return removed === 7 && copy.length === 3 && copy[0] === 3 && copy[1] === undefined && copy[2] === 1 ? 1 : 0;
    }

    static shiftMissingFirstSlot(): number {
        const values = [, 3];
        const removed = values.shift();
        const copy = [...values];
        return removed === undefined && copy.length === 1 && copy[0] === 3 ? 1 : 0;
    }

    static truncateAndMoveHoles(): number {
        const values = [7, , 11];
        values.length = 2;
        values.reverse();
        values.unshift(13);
        const last = values.pop();
        const copy = [...values];
        return last === 7 && copy.length === 2 && copy[0] === 13 && copy[1] === undefined ? 1 : 0;
    }

    static destructureHoles(): number {
        const [first = 7, second, ...rest] = [, 11, ,];
        return first === 7 && second === 11 && rest.length === 1 && rest[0] === undefined ? 1 : 0;
    }

    static forOfHoles(): number {
        let count = 0;
        for (const value of [, ,]) {
            if (value !== undefined) return 0;
            count++;
        }
        return count === 2 ? 1 : 0;
    }

    static liveDelete(): number {
        const values = [7, 11];
        let count = 0;
        let holes = 0;
        for (const value of values) {
            if (value === undefined) holes++;
            count++;
            delete values[1];
        }
        return count === 2 && holes === 1 ? 1 : 0;
    }

    static sourceObjectLookalike(): number {
        const Object = { setPrototypeOf(values: any[], prototype: any) { return values; } };
        const values = [, 7];
        Object.setPrototypeOf(values, { 0: 9 });
        const copy = [...values];
        return copy[0] === undefined && copy[1] === 7 ? 1 : 0;
    }

    static symbolicDelete(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const values = [7, 11];
        delete values[index];
        const copy = [...values];
        if (index === 0) return copy[0] === undefined && copy[1] === 11 ? 1 : 3;
        return copy[0] === 7 && copy[1] === undefined ? 2 : 3;
    }

    static symbolicWriteThenDelete(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const values = new Array<number>(2);
        values[index] = 7;
        delete values[0];
        const copy = [...values];
        return copy[0] === undefined && (index === 0 ? copy[1] === undefined : copy[1] === 7) ? 1 : 2;
    }

    static mutateArrayPrototype(): any {
        Array.prototype[0] = 7;
        return [...[,]][0];
    }

    static mutateObjectPrototype(): any {
        Object.prototype[0] = 7;
        return [...[,]][0];
    }

    static mutatePrototypeThroughBracketAlias(): any {
        const NativeObject: any = Object;
        const name = "prototype";
        NativeObject[name][0] = 7;
        return [...[,]][0];
    }

    static setArrayPrototype(): any {
        const values = [,];
        Object.setPrototypeOf(values, { 0: 7 });
        return [...values][0];
    }

    static setArrayPrototypeThroughAliases(): any {
        const NativeObject = Object;
        const values: any = [,];
        const alias: any = values;
        NativeObject.setPrototypeOf(alias, { 0: 7 });
        return [...values][0];
    }

    static getAndMutateArrayPrototype(): any {
        const values = [,];
        const prototype = Object.getPrototypeOf(values);
        prototype[0] = 7;
        return [...values][0];
    }

    static getAndMutateObjectPrototype(): any {
        const prototype = Object.getPrototypeOf({});
        prototype[0] = 7;
        return [...[,]][0];
    }

    static setLegacyPrototype(): any {
        const values: any = [,];
        values.__proto__ = { 0: 7 };
        return [...values][0];
    }

    static freezeThenDelete(): any {
        const values = [7];
        Object.freeze(values);
        delete values[0];
        return [...values][0];
    }

    static defineIndexedProperty(): any {
        const values: any = [,];
        Object.defineProperty(values, "0", { value: 7 });
        return [...values][0];
    }

    static unknownCopiedStorage(): number {
        const values = [7, 11].slice();
        return [...values].length;
    }

    static fillAllHoles(): number {
        const values = new Array<number>(2);
        values.fill(7);
        const copy = [...values];
        return copy.length === 2 && copy[0] === 7 && copy[1] === 7 ? 1 : 0;
    }

    static unprovenPartialFillStorage(): number {
        const values = new Array<number>(2);
        values.fill(7, 0, 1);
        return [...values].length;
    }
}
