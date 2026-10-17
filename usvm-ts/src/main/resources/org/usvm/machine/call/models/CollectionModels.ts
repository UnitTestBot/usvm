declare class CollectionModelPrimitives {
    static initializeMap(receiver: any): void;
    static initializeSet(receiver: any): void;
    static populateMap(receiver: any, input: any): void;
    static populateSet(receiver: any, input: any): void;
}

export class CollectionModels {
    static mapConstructor(receiver: any, input: any): any {
        if (input === undefined || input === null) CollectionModelPrimitives.initializeMap(receiver);
        else CollectionModelPrimitives.populateMap(receiver, input);
        return receiver;
    }

    static setConstructor(receiver: any, input: any): any {
        if (input === undefined || input === null) CollectionModelPrimitives.initializeSet(receiver);
        else CollectionModelPrimitives.populateSet(receiver, input);
        return receiver;
    }

    static mapFromIterable(receiver: any, input: any): any {
        CollectionModelPrimitives.initializeMap(receiver);
        for (const entry of input) {
            if (entry === null || (typeof entry !== 'object' && typeof entry !== 'function')) {
                throw new TypeError('Iterator value is not an entry object');
            }
            receiver.set(entry[0], entry[1]);
        }
        return receiver;
    }

    static setFromIterable(receiver: any, input: any): any {
        CollectionModelPrimitives.initializeSet(receiver);
        for (const value of input) receiver.add(value);
        return receiver;
    }

    static arrayFrom(input: any): any[] {
        const result: any[] = [];
        for (const value of input) result.push(value);
        return result;
    }

}
