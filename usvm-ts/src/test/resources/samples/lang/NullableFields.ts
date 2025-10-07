// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class NullableFields {
    useNullableArg(isVisible: boolean | null): number {
        // const options = { isVisible: isVisible };
        const options = new Options(isVisible);
        const component = new Component(options);
        return component.visible ? 1 : 2; // 1 if true/null, 2 if false
    }

    useOptions(options: Options): number {
        const component = new Component(options);
        return component.visible ? 1 : 2; // 1 if true/null, 2 if false
    }
}

class Options {
    isVisible: boolean | null;

    constructor(isVisible: boolean | null = true) {
        this.isVisible = isVisible;
    }
}

class Component {
    visible: boolean = true;

    constructor(options: Options) {
        if (options.isVisible !== null) {
            this.visible = options.isVisible;
        }
    }
}
