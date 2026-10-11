// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ShadowMath {
    floor(ignored: number): number {
        return 7;
    }
}

class CapturedMethodCalls {
    value = 3;

    read(ignored: number): number {
        return this.value;
    }

    replace(): number {
        this.read = () => 9;
        return 0;
    }

    methodSelectedBeforeArgument(): number {
        const receiver = new CapturedMethodCalls();
        receiver.value = 3;
        return receiver.read(receiver.replace());
    }

    shadowedBuiltinClassMethod(): number {
        const Math = new ShadowMath();
        return Math.floor(5);
    }

    callbackSelectedBeforeArgument(): number {
        const holder = {
            value: 4,
            read: function (ignored: number): number { return this.value; }
        };
        const replace = (): number => {
            holder.read = () => 8;
            return 0;
        };
        return holder.read(replace());
    }
}
