import assert from "node:assert/strict";
import { DateModels, DateValue } from "../../../main/resources/org/usvm/machine/call/models/DateModels.ts";

function modeled(...arguments_) {
    const receiver = new DateValue();
    DateModels.construct(receiver, arguments_.length, 0, ...arguments_, 0, 0, 0, 0, 0, 0, 0);
    return receiver;
}

function nativeResult(operation, arguments_) {
    const date = new Date(...arguments_);
    const result = operation(date);
    return [result, date.getTime()];
}

function modelResult(operation, arguments_) {
    const date = modeled(...arguments_);
    const result = operation(date);
    return [result, date.timestamp];
}

const timestamps = [
    -8_640_000_000_000_000,
    -2_208_988_800_001,
    -1,
    -0,
    0,
    951_827_696_789,
    1_710_067_696_789,
    8_640_000_000_000_000,
    8_640_000_000_000_001,
    NaN,
];

const getters = [
    ["getDate", (date) => date.getUTCDate(), DateModels.getDate],
    ["getDay", (date) => date.getUTCDay(), DateModels.getDay],
    ["getFullYear", (date) => date.getUTCFullYear(), DateModels.getFullYear],
    ["getHours", (date) => date.getUTCHours(), DateModels.getHours],
    ["getMilliseconds", (date) => date.getUTCMilliseconds(), DateModels.getMilliseconds],
    ["getMinutes", (date) => date.getUTCMinutes(), DateModels.getMinutes],
    ["getMonth", (date) => date.getUTCMonth(), DateModels.getMonth],
    ["getSeconds", (date) => date.getUTCSeconds(), DateModels.getSeconds],
    ["getTime", (date) => date.getTime(), DateModels.getTime],
];

for (const timestamp of timestamps) {
    const receiver = modeled(timestamp);
    const native = new Date(timestamp);
    for (const [name, nativeGetter, modelGetter] of getters) {
        assert.deepEqual(modelGetter(receiver), nativeGetter(native), `${name}(${timestamp})`);
    }
}

const componentCases = [
    [1970, 0],
    [99, 11, 31, 23, 59, 59, 999],
    [2000, 1, 29, 12, 34, 56, 789],
    [1900, 1, 29],
    [2024, -14, 0, -2, 120, -90, 2_001],
    [-1, 0, 1],
    [275760, 8, 13],
];

const utcBoundaryCases = [
    [],
    [undefined],
    [2020],
    [2020, undefined],
];

for (const arguments_ of utcBoundaryCases) {
    assert.deepEqual(
        DateModels.utc(arguments_.length, ...arguments_, 0, 0, 0, 0, 0, 0, 0),
        Date.UTC(...arguments_),
        `UTC(${arguments_.join(",")})`,
    );
}

for (const arguments_ of componentCases) {
    assert.deepEqual(
        modeled(...arguments_).timestamp,
        new Date(Date.UTC(...arguments_)).getTime(),
        `constructor(${arguments_.join(",")})`,
    );
    assert.deepEqual(
        DateModels.utc(arguments_.length, ...arguments_, 0, 0, 0, 0, 0, 0, 0),
        Date.UTC(...arguments_),
        `UTC(${arguments_.join(",")})`,
    );
}

const setterCases = [
    ["setDate", [0], (date, args) => date.setUTCDate(...args), (date, args) => DateModels.setDate(date, ...args)],
    ["setFullYear", [2024, 13, 0], (date, args) => date.setUTCFullYear(...args), (date, args) => DateModels.setFullYear(date, args.length, ...args, 0, 0)],
    ["setHours", [-1, 70, -80, 1_500], (date, args) => date.setUTCHours(...args), (date, args) => DateModels.setHours(date, args.length, ...args, 0, 0, 0)],
    ["setMilliseconds", [-1], (date, args) => date.setUTCMilliseconds(...args), (date, args) => DateModels.setMilliseconds(date, ...args)],
    ["setMinutes", [61, -2, 1_001], (date, args) => date.setUTCMinutes(...args), (date, args) => DateModels.setMinutes(date, args.length, ...args, 0, 0)],
    ["setMonth", [-13, 40], (date, args) => date.setUTCMonth(...args), (date, args) => DateModels.setMonth(date, args.length, ...args, 0)],
    ["setSeconds", [-61, 2_000], (date, args) => date.setUTCSeconds(...args), (date, args) => DateModels.setSeconds(date, args.length, ...args, 0)],
    ["setTime", [-1.9], (date, args) => date.setTime(...args), (date, args) => DateModels.setTime(date, ...args)],
];

for (const timestamp of [-1, 0, 951_827_696_789]) {
    for (const [name, args, nativeSetter, modelSetter] of setterCases) {
        assert.deepEqual(
            modelResult((date) => modelSetter(date, args), [timestamp]),
            nativeResult((date) => nativeSetter(date, args), [timestamp]),
            `${name} from ${timestamp}`,
        );
    }
}

for (const timestamp of [-62_167_219_200_000, -1, 0, 253_402_300_799_999]) {
    const receiver = modeled(timestamp);
    assert.equal(DateModels.toISOString(receiver), new Date(timestamp).toISOString());
}

assert.deepEqual(
    DateModels.getTimezoneOffset(modeled(NaN)),
    new Date(NaN).getTimezoneOffset(),
    "getTimezoneOffset(NaN)",
);

console.log("DateModels comparison passed");
