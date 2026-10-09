// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class DeleteMethodSubject {
    ownValue: number = 5;

    method(): number {
        return 7;
    }
}

class DeleteProperty {
    readAfterDelete(value: number): number | undefined {
        const object: { x?: number } = { x: value };
        delete object.x;
        return object.x;
    }

    deleteTypedNumber(): number {
        const object: { x: number } = { x: 5 };
        delete object.x;
        return object.x === undefined ? 1 : 0;
    }

    deleteBoolean(): number {
        const object: { flag: boolean } = { flag: true };
        delete object.flag;
        return object.flag === undefined ? 1 : 0;
    }

    deleteReference(): number {
        const object: { nested: object } = { nested: { value: 1 } };
        delete object.nested;
        return object.nested === undefined ? 1 : 0;
    }

    aliasSeesDelete(): number {
        const object: { x?: number } = { x: 2 };
        const alias = object;
        delete alias.x;

        return object.x === undefined ? 1 : 0;
    }

    restoreAfterDelete(): number {
        const object: { x?: number } = { x: 2 };
        delete object.x;

        object.x = 3;
        return object.x === 3 ? 1 : 0;
    }

    deleteMissing(): number {
        const object: { x?: number } = {};
        const result = delete object.x;
        return result === true && object.x === undefined ? 1 : 0;
    }

    readAfterConditionalDelete(shouldDelete: boolean): number {
        const object: { x?: number } = { x: 5 };
        if (shouldDelete) {
            delete object.x;
        }

        return object.x === undefined ? 1 : 2;
    }

    deleteAndRestoreString(value: string): number {
        const object: { value: string } = { value };
        delete object.value;
        if (object.value !== undefined) return 0;

        object.value = value;
        return object.value === value ? 1 : 0;
    }

    conditionalDeleteString(value: string, shouldDelete: boolean): number {
        const object: { value: string } = { value };
        if (shouldDelete) {
            delete object.value;
        }

        if (shouldDelete) return object.value === undefined ? 1 : 0;
        return object.value === value ? 2 : 0;
    }

    deleteInput(object: { x?: number }): number {
        delete object.x;
        return object.x === undefined ? 1 : 0;
    }

    untouchedInput(object: { x: boolean }): number {
        return object.x === undefined ? 0 : 1;
    }

    deleteInputAlias(object: { x?: number }): number {
        const alias = object;
        delete alias.x;

        return object.x === undefined ? 1 : 0;
    }

    restoreInput(object: { x?: number }): number {
        delete object.x;
        if (object.x !== undefined) return 0;

        object.x = 7;
        return object.x === 7 ? 1 : 0;
    }

    conditionalDeleteInput(object: { x: boolean }, shouldDelete: boolean): number {
        const original = object.x;
        if (shouldDelete) {
            delete object.x;
        }

        if (shouldDelete) return object.x === undefined ? 1 : 0;
        return object.x === original ? 2 : 0;
    }

    deletePossiblyAliasedInputs(first: { x: boolean }, second: { x: boolean }): number {
        if (first === second) {
            delete first.x;
            return second.x === undefined ? 1 : 0;
        }

        const original = second.x;
        delete first.x;
        return second.x === original ? 2 : 0;
    }

    deleteValueExpression(value: number): boolean {
        return delete (value + 1);
    }

    deleteAssignmentExpression(): number {
        const object = { value: 0 };
        const result = delete (object.value = 1);

        return result === true && object.value === 1 ? 1 : 0;
    }

    deleteCastedField(): number {
        const object = { value: 1 };
        delete (object.value as any);

        return object.value === undefined ? 1 : 0;
    }

    deleteCastedValue(): number {
        const object = { value: 0 };
        const result = delete ((object.value = 1) as number);

        return result === true && object.value === 1 ? 1 : 0;
    }

    deleteOwnToString(): number {
        const object = { toString: 1 };
        delete object.toString;
        return object.toString === undefined ? 1 : 0;
    }

    deletePrototypeMethod(): number {
        const instance = new DeleteMethodSubject();
        delete instance.method;
        return instance.method === undefined ? 1 : 0;
    }

    deleteClassOwnField(): number {
        const instance = new DeleteMethodSubject();
        delete instance.ownValue;
        return instance.ownValue === undefined ? 1 : 0;
    }

    deleteObjectLiteralMethod(): number {
        const object = { method(): number { return 7; } };
        delete object.method;
        return object.method === undefined ? 1 : 0;
    }

    deleteArrayPrototypeMethod(): number {
        const values = [1, 2];
        delete values.push;
        return values.push === undefined ? 1 : 0;
    }
}
