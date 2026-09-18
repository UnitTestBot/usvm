// @ts-nocheck
class ReceiverObject {
    value: number = 42;
    read(): number { return this.value; }
    shift(): number { return 99; }
}

export class InstanceCallReceiver {
    wrappedShift(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const receiver = box[0];
        return receiver.shift() === 10 && values[0] === 20 && values.length === 1 ? 1 : -1;
    }

    wrappedPop(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const receiver = box[0];
        return receiver.pop() === 20 && values[0] === 10 && values.length === 1 ? 1 : -1;
    }

    wrappedPush(): number {
        const values = [10];
        const box: any[] = [values, true];
        const receiver = box[0];
        return receiver.push(20) === 2 && values[1] === 20 ? 1 : -1;
    }

    wrappedReverse(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const receiver = box[0];
        receiver.reverse();
        return values[0] === 20 && values[1] === 10 ? 1 : -1;
    }

    wrappedFill(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        box[0].fill(7);
        return values[0] === 7 && values[1] === 7 ? 1 : -1;
    }

    wrappedUnshift(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        return box[0].unshift(7) === 3 && values[0] === 7 && values[1] === 10 ? 1 : -1;
    }

    wrappedSlice(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].slice(1);
        return result.length === 1 && result[0] === 20 && values.length === 2 ? 1 : -1;
    }

    wrappedSliceReversed(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].slice(1, 0);
        return result.length === 0 && values.length === 2 ? 1 : -1;
    }

    wrappedSlicePastEnd(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].slice(1, 10);
        return result.length === 1 && result[0] === 20 && values.length === 2 ? 1 : -1;
    }

    wrappedSlicePastStart(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].slice(10);
        return result.length === 0 && values.length === 2 ? 1 : -1;
    }

    wrappedSliceNegative(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].slice(-10, -1);
        return result.length === 1 && result[0] === 10 && values.length === 2 ? 1 : -1;
    }

    wrappedSliceEmpty(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].slice(1, 1);
        return result.length === 0 && values.length === 2 ? 1 : -1;
    }

    wrappedConcat(): number {
        const values = [10, 20];
        const box: any[] = [values, true];
        const result = box[0].concat([30]);
        return result.length === 3 && result[2] === 30 && values.length === 2 ? 1 : -1;
    }

    wrappedUserMethod(): number {
        const object = new ReceiverObject();
        const box: any[] = [object, true];
        return box[0].read() === 42 ? 1 : -1;
    }

    customShift(): number {
        const object = new ReceiverObject();
        const box: any[] = [object, true];
        return box[0].shift() === 99 ? 1 : -1;
    }

    conditionalArrays(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const numbers = [10, 20];
        const booleans = [true, false];
        const box: any[] = [numbers, true];
        box[index] = booleans;
        const receiver = box[0];
        const result = receiver.shift();
        if (index === 0) return result === true && booleans[0] === false && numbers.length === 2 ? 1 : -1;
        return result === 10 && numbers[0] === 20 && booleans.length === 2 ? 1 : -1;
    }

    conditionalEmptyArray(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const values: any[] = [10, true];
        const empty: any[] = [];
        const box: any[] = [values, false];
        box[index] = empty;
        const result = box[0].shift();
        if (index === 0) return result === undefined && values.length === 2 ? 1 : -1;
        return result === 10 && values[0] === true && empty.length === 0 ? 1 : -1;
    }

    arrayOrUserMethod(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const values = [10, 20];
        const object = new ReceiverObject();
        const box: any[] = [values, true];
        box[index] = object;
        const result = box[0].shift();
        if (index === 0) return result === 99 && values.length === 2 ? 1 : -1;
        return result === 10 && values.length === 1 ? 1 : -1;
    }

    primitiveValueOf(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const box: any[] = [17, true];
        const receiver = box[index];
        return receiver.valueOf() === receiver ? 1 : -1;
    }

    primitiveToString(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const box: any[] = [17, true];
        return typeof box[index].toString() === 'string' ? 1 : -1;
    }

    constrainedFake(value: any): number {
        if (value !== 17 && value !== true) return 0;
        const result = value.valueOf();
        if (result === 17) return 1;
        if (result === true) return 2;
        return -1;
    }

    nullableReceiver(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const object = new ReceiverObject();
        const box: any[] = [object, null];
        return box[index].read() === 42 ? 1 : -1;
    }

    undefinedReceiver(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const object = new ReceiverObject();
        const box: any[] = [object, undefined];
        return box[index].read() === 42 ? 1 : -1;
    }

    nullToString(): number {
        const box: any[] = [null, 17, true];
        box[0].toString();
        return -1;
    }

    undefinedValueOf(): number {
        const box: any[] = [undefined, 17, true];
        box[0].valueOf();
        return -1;
    }

    nullShift(): number {
        const box: any[] = [null, 17, true];
        box[0].shift();
        return -1;
    }

    undefinedShift(): number {
        const box: any[] = [undefined, 17, true];
        box[0].shift();
        return -1;
    }
}
