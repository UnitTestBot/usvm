// @ts-nocheck
// noinspection PointlessBooleanExpressionJS

import image from '@ohos.multimedia.image';

function branch01(condition: boolean) {
    const imageSource = image.createImageSource("");

    if (condition) {
        imageSource.release();
    } else {
        // no release
    }
}

function branch02(condition: boolean) {
    const imageSource = image.createImageSource("");
    const pixelMap = imageSource.createPixelMap("");

    if (condition) {
        pixelMap.reverse();
        imageSource.release();
    } else {
        imageSource.release();
    }
    return;
}

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
