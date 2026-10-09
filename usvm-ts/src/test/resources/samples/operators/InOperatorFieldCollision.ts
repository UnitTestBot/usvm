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

class OtherString {
    shadowedNumber: string = "unrelated";
}

class Probe {
    numericFieldWithStringCollision(): number {
        const obj = {};
        obj.shadowedNumber = 7;
        return "shadowedNumber" in obj ? obj.shadowedNumber : 0;
    }

    writtenStringField(): number {
        const obj = {};
        obj.y = "1234567";
        return "y" in obj ? obj.y.length : 0;
    }

    declaredStringField(): number {
        const obj = { y: "1234567" };
        return "y" in obj ? obj.y.length : 0;
    }

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
