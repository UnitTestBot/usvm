// @ts-nocheck

import rpc from '@ohos.rpc';

function basic01(data: rpc.MessageSequence) {
    // [0]
    const sizeValue = data.readInt();
    if (sizeValue > 0 && sizeValue <= 1024 * 1024) {
        // [3]
        const safeBuffer = new ArrayBuffer(sizeValue);
    } else {
        console.log("No safe, do nothing");
    }
}
