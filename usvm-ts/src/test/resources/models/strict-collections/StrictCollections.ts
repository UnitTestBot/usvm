export class StrictCollections {
    static nativeFromKeys(): number {
        const map = new Map<string, number>();
        map.set('a', 3).set('b', 5);
        const keys = Array.from(map.keys());
        return keys.length === 2 && keys[0] === 'a' && keys[1] === 'b' ? 1 : 0;
    }
}
