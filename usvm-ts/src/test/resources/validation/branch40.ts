// @ts-nocheck

import image from '@ohos.multimedia.image';

function branch40() {
    let imageSource = image.createImageSource("");
    let pixelMap = /*await*/ image.createPixelMap("");

    console.log("Do something with resources");

    if (pixelMap != null) {
        pixelMap.release();
    }

    if (imageSource != null) {
        imageSource.release();
    }

    return;
}
