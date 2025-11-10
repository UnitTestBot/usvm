// @ts-nocheck

import image from '@ohos.multimedia.image';

function branch07(p: boolean) {
    const imageSource = image.createImageSource("");
    const pixelMap = await imageSource.createPixelMap();

    if (p) {
        let a = 1;
    }
    const condition = false;

    if (condition) {
        let a = 1;
    } else {
        pixelMap.release();
        imageSource.release();
    }
}
