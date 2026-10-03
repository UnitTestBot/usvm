// @ts-nocheck

let result = 0;
{
    let obj = {};
    obj.x = 7;
    result = "x" in obj ? obj.x : 0;
}

class Other {
    x: boolean = false;
}

class Probe {
    run(): number {
        return result;
    }

    declaredProperty(): number {
        const obj = { x: 1 };
        obj.x = 7;
        return obj.x;
    }

    reassignedProperty(): number {
        const obj = {};
        obj.x = false;
        obj.x = 7;
        return obj.x;
    }
}
