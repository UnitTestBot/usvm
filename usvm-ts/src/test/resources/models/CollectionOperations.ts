export class CollectionOperations {
    static iterableSet(): number {
        const set = new Set([7, 7, -0, 0, NaN, NaN]);
        const values = [...set];
        return set.size === 3 && values[0] === 7 && 1 / values[1] === Infinity && values[2] !== values[2] ? 1 : 0;
    }

    static iterableMap(): number {
        const map = new Map<any, any>([['a', 1], ['b', 2], ['a', 3]]);
        const entries = [...map];
        return map.size === 2 && entries[0][0] === 'a' && entries[0][1] === 3
            && entries[1][0] === 'b' && entries[1][1] === 2 ? 1 : 0;
    }

    static mapInvalidEntry(): number {
        try {
            new Map([1] as any);
            return 0;
        } catch (error) {
            return error.name === 'TypeError' ? 1 : 0;
        }
    }

    static copyCollections(): number {
        const source = new Map<string, number>();
        source.set('a', 3).set('b', 5);
        const copy = new Map(source);
        source.set('a', 7);
        const values = new Set(copy.values());
        return copy.get('a') === 3 && values.size === 2 && values.has(3) && values.has(5) ? 1 : 0;
    }

    static arrayFromKeys(): number {
        const map = new Map<string, number>();
        map.set('a', 3).set('b', 5);
        const keys = Array.from(map.keys());
        return keys.length === 2 && keys[0] === 'a' && keys[1] === 'b' ? 1 : 0;
    }

    static liveMapIterator(): number {
        const map = new Map<number, number>();
        map.set(1, 11).set(2, 22).set(3, 33);
        const iterator = map.entries();
        const first = iterator.next().value;
        map.delete(2);
        map.set(3, 44).set(1, 55).set(2, 66);
        const second = iterator.next().value;
        const third = iterator.next().value;
        const done = iterator.next().done;
        map.set(4, 77);
        return first[0] === 1 && first[1] === 11 && second[0] === 3 && second[1] === 44
            && third[0] === 2 && third[1] === 66 && done && iterator.next().done ? 1 : 0;
    }

    static clearDuringIteration(): number {
        const set = new Set<number>();
        set.add(1).add(2);
        const iterator = set.values();
        const first = iterator.next().value;
        set.clear();
        set.add(3);
        const second = iterator.next().value;
        return first === 1 && second === 3 && iterator.next().done ? 1 : 0;
    }

    static setEntryAndZero(): number {
        const set = new Set<number>();
        set.add(-0).add(-0);
        const iterator = set.entries();
        const entry = iterator.next().value;
        return 1 / entry[0] === Infinity && 1 / entry[1] === Infinity && iterator.next().done ? 1 : 0;
    }

    static arrayEntryLiveLength(): number {
        const array = [3, 5];
        const iterator = array.entries();
        const first = iterator.next().value;
        array[1] = 7;
        array.push(9);
        const second = iterator.next().value;
        const third = iterator.next().value;
        const done = iterator.next().done;
        array.push(11);
        return first[0] === 0 && first[1] === 3 && second[0] === 1 && second[1] === 7
            && third[0] === 2 && third[1] === 9 && done && iterator.next().done ? 1 : 0;
    }

    static arrayKeysAndHoles(): number {
        const array = [3, 5];
        delete array[0];
        const keys = [...array.keys()];
        const values = [...array.values()];
        return keys.length === 2 && keys[0] === 0 && keys[1] === 1
            && values[0] === undefined && values[1] === 5 ? 1 : 0;
    }

    static arrayMap(): number {
        const array = [3, 5];
        const result = array.map((value, index, receiver) => value + index + (receiver === array ? 1 : 100));
        return result.length === 2 && result[0] === 4 && result[1] === 7 ? 1 : 0;
    }

    static arrayMapMutation(): number {
        const array = [3, 5, 7];
        const counter = { value: 0 };
        const result = array.map((value, index) => {
            counter.value++;
            if (index === 0) {
                delete array[1];
                array.push(9);
            }
            return value * 2;
        });
        const observed = { calls: 0 };
        result.forEach(() => { observed.calls++; });
        return counter.value === 2 && result.length === 3 && result[0] === 6
            && result[2] === 14 && observed.calls === 2 ? 1 : 0;
    }

    static arrayForEachMutation(): number {
        const array = [3, 5, 7];
        const counter = { value: 0 };
        const result = array.forEach((value, index, receiver) => {
            counter.value += value + index;
            if (index === 0) receiver.length = 1;
        });
        return result === undefined && counter.value === 3 ? 1 : 0;
    }

    static callbackThrows(): number {
        const counter = { value: 0 };
        try {
            [3, 5].forEach((value) => {
                counter.value += value;
                throw 7;
            });
            return 0;
        } catch (error) {
            return error === 7 && counter.value === 3 ? 1 : 0;
        }
    }

    static iteratorFork(remove: boolean): number {
        const set = new Set<number>();
        set.add(3).add(5);
        const iterator = set.values();
        const first = iterator.next().value;
        if (remove) set.delete(5);
        const second = iterator.next();
        return first === 3 && (remove ? second.done : !second.done && second.value === 5) ? 1 : 0;
    }

    static implicitCollectionIteration(): number {
        const set = new Set<number>();
        set.add(3);
        let sum = 0;
        for (const value of set) sum += value;
        return sum === 3 ? 1 : 0;
    }

    static callbackWithoutThis(): number {
        const result = [3].map(function (this: void, value) {
            return this === undefined ? value + 1 : 100;
        });
        return result[0] === 4 ? 1 : 0;
    }

    static shadowedArrayFrom(): number {
        const Array = { from: (value: any) => 7 };
        return Array.from([]) === 7 ? 1 : 0;
    }

    static unsupportedThisArg(): number {
        [3].forEach(function () {}, {});
        return 1;
    }
}
