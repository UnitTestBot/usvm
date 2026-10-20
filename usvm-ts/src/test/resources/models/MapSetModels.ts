export class MapSetModels {
    static mapOverwrite(): number {
        const map = new Map<string, number>();
        const alias = map;
        const chained = map.set('key', 7);
        alias.set('key', 19);
        return chained === map && map.size === 1 && map.get('key') === 19 ? 1 : 0;
    }

    static mapMissingAndUndefined(): number {
        const map = new Map<any, any>();
        (map as any).set();
        return map.size === 1 && (map as any).has() && (map as any).get() === undefined
            && !map.has('missing') && map.get('missing') === undefined ? 1 : 0;
    }

    static mapSameValueZero(): number {
        const map = new Map<number, number>();
        map.set(NaN, 7);
        map.set(0 / 0, 19);
        map.set(-0, 23);
        map.set(0, 29);
        return map.size === 2 && map.get(NaN) === 19 && map.get(-0) === 29 ? 1 : 0;
    }

    static mapObjectIdentity(): number {
        const first = { value: 1 };
        const second = { value: 1 };
        const map = new Map<object, number>();
        map.set(first, 17);
        map.set(second, 19);
        return map.size === 2 && map.get(first) === 17 && map.get(second) === 19 ? 1 : 0;
    }

    static mapStringContents(left: string, right: string): number {
        const map = new Map<string, number>();
        map.set(left, 17);
        map.set(right, 19);
        return map.get(left) === (left === right ? 19 : 17) ? 1 : 0;
    }

    static mapSymbolicKeys(left: number, right: number): number {
        const map = new Map<number, number>();
        map.set(left, 17);
        map.set(right, 19);
        const same = left === right || (left !== left && right !== right);
        return map.get(left) === (same ? 19 : 17) ? 1 : 0;
    }

    static mapDeleteAndReinsert(): number {
        const map = new Map<number, number>();
        map.set(7, 17);
        const first = map.delete(7);
        const second = map.delete(7);
        map.set(7, 19);
        return first && !second && map.size === 1 && map.get(7) === 19 ? 1 : 0;
    }

    static mapClear(): number {
        const map = new Map<string, number>();
        const alias = map;
        map.set('key', 17);
        const result = alias.clear();
        return result === undefined && map.size === 0 && !map.has('key') ? 1 : 0;
    }

    static mapConditional(branch: boolean): number {
        const map = new Map<string, number>();
        map.set('key', 17);
        if (branch) map.delete('key');
        else map.set('key', 19);
        if (branch) return !map.has('key') && map.size === 0 ? 1 : 0;
        return map.get('key') === 19 && map.size === 1 ? 1 : 0;
    }

    static mapKindsRemainDistinct(): number {
        const map = new Map<any, number>();
        map.set(0, 17);
        map.set(false, 19);
        map.set('0', 23);
        map.set(null, 29);
        map.set(undefined, 31);
        return map.size === 5 && map.get(0) === 17 && map.get(false) === 19
            && map.get('0') === 23 && map.get(null) === 29 && map.get(undefined) === 31 ? 1 : 0;
    }

    static setSameValueZero(): number {
        const set = new Set<number>();
        const chained = set.add(NaN).add(0 / 0).add(-0).add(0);
        return chained === set && set.size === 2 && set.has(NaN) && set.has(0) ? 1 : 0;
    }

    static setMissing(): number {
        const set = new Set<any>();
        (set as any).add();
        return set.size === 1 && set.has(undefined) && !set.has(null) ? 1 : 0;
    }

    static setDeleteAndClear(): number {
        const set = new Set<string>();
        const alias = set;
        set.add('first').add('second');
        const first = alias.delete('first');
        const second = alias.delete('first');
        alias.clear();
        set.add('third');
        return first && !second && set.size === 1 && set.has('third') && !set.has('second') ? 1 : 0;
    }

    static setSymbolicKeys(left: number, right: number): number {
        const set = new Set<number>();
        set.add(left).add(right);
        const same = left === right || (left !== left && right !== right);
        return set.has(left) && set.has(right) && set.size === (same ? 1 : 2) ? 1 : 0;
    }

    static nullishConstructors(): number {
        const map = new Map(null as any);
        const set = new Set(undefined);
        return map.size === 0 && set.size === 0 ? 1 : 0;
    }
    static iterableConstructor(): number {
        const set = new Set([7, 19]);
        return set.size;
    }

    static capacityBoundary(): number {
        const set = new Set<number>();
        for (let key = 0; key < 17; key++) set.add(key);
        return set.size;
    }

    static *delegatedYield(): IterableIterator<number> {
        yield* [1];
    }

}
