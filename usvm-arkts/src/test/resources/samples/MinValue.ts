function findMinValue(arr: number[]): number {
    if (arr.length === 0) throw new Error("Array is empty!");

    let minValue = arr[0];
    for (let i = 1; i < arr.length; i++) {
        if (arr[i] < minValue) {
            minValue = arr[i];
        }
    }
    return minValue;
}

function findMinValue2(num: number): boolean {
    let acc = 0;

    for (let i = 1; i < num; i++) {
        acc += i;
    }

    if (acc > 10) {
        return true
    }

    throw new Error("Too small!");
}
