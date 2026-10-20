export class InputCollections {
    values: Map<number, number>;

    mapPresence(map: Map<number, number>): number {
        const before = map.size;
        if (before === 0) {
            if (map.has(7) || map.get(7) !== undefined) return -1;
            return 1;
        }
        if (before === 2) return 2;
        return 3;
    }

    initialLookup(map: Map<number, number>): number {
        const has = map.has(7);
        const first = map.get(7);
        const second = map.get(7);
        if (!has) return first === undefined && second === undefined ? 1 : -1;
        if (first !== second && !(first !== first && second !== second)) return -2;
        return first === 19 ? 2 : 3;
    }

    erasedAlias(map: Map<string, number>): number {
        const alias: any = map;
        alias.set('key', 19);
        return alias.get('key') === 19 && alias.size === map.size ? 1 : -1;
    }

    invalidSizeWrite(map: Map<number, number>): void {
        (map as any).size = 7;
    }

    invalidSizeDelete(map: Map<number, number>): void {
        delete (map as any).size;
    }

    mapMutation(map: Map<number, number>): number {
        const before = map.size;
        const had = map.has(7);
        if (map.set(7, 19) !== map) return -1;
        if (!map.has(7) || map.get(7) !== 19) return -2;
        if (map.size !== before + (had ? 0 : 1)) return -3;
        if (!map.delete(7) || map.has(7) || map.get(7) !== undefined) return -4;
        map.clear();
        if (map.size !== 0) return -5;
        return had ? 1 : 2;
    }

    aliases(left: Map<number, number>, right: Map<number, number>): number {
        left.clear();
        right.clear();
        left.set(7, 19);
        if (left === right) {
            if (!right.has(7) || right.get(7) !== 19 || right.size !== 1) return -1;
            right.delete(7);
            return left.size === 0 ? 1 : -2;
        }
        return right.has(7) ? -3 : 2;
    }

    field(): number {
        const alias = this.values;
        alias.clear();
        this.values.set(7, 19);
        return alias.get(7) === 19 && alias.size === 1 ? 1 : -1;
    }

    setPresence(set: Set<number>): number {
        const before = set.size;
        const had = set.has(7);
        if (set.add(7) !== set || !set.has(7)) return -1;
        if (set.size !== before + (had ? 0 : 1)) return -2;
        if (!set.delete(7) || set.has(7)) return -3;
        set.clear();
        return set.size === 0 ? (had ? 1 : 2) : -4;
    }

    iteration(map: Map<number, number>): number {
        let count = 0;
        for (const key of map.keys()) {
            if (!map.has(key)) return -1;
            count++;
        }
        return count === map.size ? (count === 0 ? 1 : 2) : -2;
    }

    liveIteration(set: Set<number>): number {
        const iterator = set.values();
        set.clear();
        set.add(19);
        const first = iterator.next();
        if (first.done || first.value !== 19) return -1;
        if (!iterator.next().done) return -2;
        set.add(23);
        return iterator.next().done ? 1 : -3;
    }

    stringKeys(map: Map<string, number>): number {
        map.set('key', 19);
        return map.get('k' + 'ey') === 19 && map.has('key') ? 1 : -1;
    }

    stateFork(map: Map<number, number>, clear: boolean): number {
        map.set(7, 19);
        if (clear) map.clear();
        if (clear) return map.has(7) ? -1 : 1;
        return map.get(7) === 19 ? 2 : -2;
    }
}
