// @ts-nocheck

import image from '@ohos.multimedia.image';

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
