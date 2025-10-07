// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class NullableFields {
    useNullableArg(isVisible: boolean | null): boolean {
        // const options = { isVisible: isVisible };
        const options = new Options(isVisible);
        const component = new Component(options);
        if (isVisible === null) {
            return component.visible; // true
        }
        if (isVisible === true) {
            return component.visible; // true
        }
        if (isVisible === false) {
            return component.visible; // false
        }
        // unreachable
    }

    useOptions(options: Options): boolean {
        const component = new Component(options);
        if (options.isVisible === null) {
            return component.visible; // true
        }
        if (options.isVisible === true) {
            return component.visible; // true
        }
        if (options.isVisible === false) {
            return component.visible; // false
        }
        // unreachable
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
