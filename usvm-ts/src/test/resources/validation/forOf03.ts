// @ts-nocheck

import image from '@ohos.multimedia.image';

function forOf03() {
    const arr = [0, 1, 2];
    let imageSource = image.createImageSource("");
    let pixelMap = await imageSource.createPixelMap("");

    for (const item of arr) {
        console.log('Using resources');
        imageSource.release();
        imageSource = image.createImageSource("");
        pixelMap = await imageSource.createPixelMap("");
        pixelMap.release();
    }
    imageSource.release();
}
