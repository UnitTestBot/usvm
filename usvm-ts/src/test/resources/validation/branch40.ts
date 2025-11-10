// @ts-nocheck

import image from '@ohos.multimedia.image';

function branch40() {
    let imageSource = image.createImageSource("");
    let pixelMap = await image.createPixelMap("");

    console.log("Do something with resources");

    if (pixelMap !== null) {
        pixelMap.release();
    } else {
        console.log("no need to release pixelMap");
    }

    if (imageSource != null) {
        imageSource.release();
    } else {
        console.log("no need to release imageSource");
    }

    return;
}
