// @ts-nocheck

import image from '@ohos.multimedia.image';

function forOf07() {
    const arr = [0, 1, 2];
    let imageSource: image.ImageSource | null = null;
    let pixelMap: image.PixelMap | null = null;

    for (const item of arr) {
        pixelMap.release();
        imageSource.release();
        imageSource = image.createImageSource("");
        pixelMap = imageSource.createPixelMap("");
        console.log('Using resources');
    }

    if (imageSource !== null) {
        imageSource.release();
    }
}
