import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

const root = process.argv[2];
globalThis.StringModelPrimitives = {
    length: value => value.length,
    codeUnitAt: (value, index) => value.charCodeAt(index),
    fromCodeUnit: value => String.fromCharCode(value),
    copyRange: (value, start, end) => value.slice(start, end),
};
globalThis.ArrayModelPrimitives = {
    allocateStrings: length => new Array(length).fill(''),
    truncateDense: (array, length) => { array.length = length; return array; },
    elementString: value => value == null ? '' : String(value),
    requireDense: () => {},
};
const unsupported = new Error('outside guarded decimal domain');
globalThis.NumberParseFloatPrimitives = { unsupportedDecimal: () => { throw unsupported; } };
const { StringModels } = await import(pathToFileURL(`${root}/StringModels.ts`));
const { ArrayModels } = await import(pathToFileURL(`${root}/ArrayModels.ts`));
const { NumberParseFloatModels } = await import(pathToFileURL(`${root}/NumberParseFloatModels.ts`));

let splitCases = 0;
for (const input of ['', 'abc', ',a,,', 'aaaa', 'a\0b', '😀x\ud800', 'ab--cd--']) {
    for (const separator of [undefined, '', ',', 'a', '--', '😀', '\ud83d']) {
        for (const limit of [undefined, 0, 1, 2, 8, -1, -2.7, 2.7, NaN, Infinity, 4294967297]) {
            const actual = StringModels.split(input, separator ?? '', limit === undefined ? 4294967295 : limit >>> 0,
                separator === undefined);
            assert.deepEqual(actual, input.split(separator, limit), JSON.stringify([input, separator, limit]));
            splitCases++;
        }
    }
}
let joinCases = 0;
for (const array of [[], [''], ['a', 'b'], [null, undefined, 'x'], [true, false], ['😀', '\ud800']]) {
    for (const separator of [undefined, '', ',', '--', '😀']) {
        assert.equal(ArrayModels.join(array, separator ?? ','), array.join(separator));
        joinCases++;
    }
}
let reduceCases = 0;
for (const values of [[], [1], [1, 2, 3]]) {
    for (const hasInitial of [false, true]) {
        for (const initial of [undefined, 0, 7]) {
            const left = values.slice();
            const right = values.slice();
            const callback = (acc, value, index, array) => {
                if (index === 0 && array.length > 1) array[1] = 10;
                return (acc ?? 9) + value + index + array.length;
            };
            if (!values.length && !hasInitial) {
                assert.throws(() => ArrayModels.reduce(left, callback, initial, hasInitial), TypeError);
            } else {
                assert.equal(ArrayModels.reduce(left, callback, initial, hasInitial),
                    hasInitial ? right.reduce(callback, initial) : right.reduce(callback));
                assert.deepEqual(left, right);
            }
            reduceCases++;
        }
    }
}
let parseCases = 0;
const checkParse = input => {
    const actual = NumberParseFloatModels.parseFloat(input);
    assert.ok(Object.is(actual, Number.parseFloat(input)), `${JSON.stringify(input)}: ${actual}`);
    parseCases++;
};
for (const input of ['', ' ', '+.', '-', '.5', '1.', '12e', '12e-', '12e+foo', '0x10', '1.2.3',
    '\ufeff-1.25rem', 'Infinity!', '-Infinity', '+Infinityx', 'infinity', '-0', '-0e-99999',
    '000.00001', '0.100000000000001', '999999999999999e-22', '999999999999999e22']) checkParse(input);
for (const whitespace of [9, 10, 11, 12, 13, 32, 160, 5760, 8192, 8202, 8232, 8233, 8239, 8287, 12288, 65279]) {
    checkParse(String.fromCharCode(whitespace) + '-12.5px');
}
for (let coefficient = 1; coefficient <= 999; coefficient += 7) {
    for (let scale = -22; scale <= 22; scale++) {
        checkParse(`${coefficient}e${scale}x`);
        checkParse(`-${coefficient}e${scale}x`);
    }
}
for (const input of ['12345678901234567', '1e23', '1e-23']) {
    assert.throws(() => NumberParseFloatModels.parseFloat(input), error => error === unsupported);
}
console.log(JSON.stringify({ splitCases, joinCases, reduceCases, parseCases }));
