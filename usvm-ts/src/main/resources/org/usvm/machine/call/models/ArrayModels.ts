export class ArrayModels {
    static pop(receiver: any[]): any {
        const length = receiver.length;
        if (length === 0) {
            return undefined;
        }

        const result = receiver[length - 1];
        receiver.length = length - 1;
        return result;
    }
}
