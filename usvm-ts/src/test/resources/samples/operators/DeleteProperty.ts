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

    deleteInput(object: { x?: number }): number {
        delete object.x;
        return 1;
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
