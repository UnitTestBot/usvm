// @ts-nocheck

import image from '@ohos.multimedia.image';

function branch41() {
    let imageSource: image.ImageSource | null = null;
    let pixelMap: image.PixelMap | null = null;

    if (!imageSource) {
        imageSource = image.createImageSource("");
    }

    console.log("Do something with resources");

    if (pixelMap != null) {
        pixelMap.release();
        imageSource.release();
        return;
    }

    console.log("imageSource is never released");
    return;
}
