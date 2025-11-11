// @ts-nocheck

import image from '@ohos.multimedia.image';

function branch41() {
    let imageSource: image.ImageSource | null = null;
    if (imageSource === null) {
        imageSource = image.createImageSource("");
    } else {
        console.log("no need to create imageSource");
    }

    let pixelMap: image.PixelMap = await imageSource.createPixelMap();

    console.log("Do something with resources");

    if (pixelMap !== undefined) {
        pixelMap.release();
    } else {
        console.log("no need to release");
    }

    return;
}
