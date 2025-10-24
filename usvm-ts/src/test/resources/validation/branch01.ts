// @ts-nocheck

import image from '@ohos.multimedia.image';

function branch01(condition: boolean) {
    const imageSource = image.createImageSource("");

    if (condition) {
        imageSource.release();
    } else {
        // no release
    }
}
