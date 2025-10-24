// @ts-nocheck

import image from '@ohos.multimedia.image';

function forOf05() {
    const arr = [0, 1, 2];
    let imageSource: image.ImageSource | null = null;
    let pixelMap: image.PixelMap | null = null;

    for (const item of arr) {
        if (item > 0) {
            console.log('Using resources');
        }
        if (item >= 0) {
            imageSource.release();
        }
        imageSource = image.createImageSource("");
        pixelMap = imageSource.createPixelMap("");
        console.log('Using resources after re-initialization');
    }

    pixelMap.release();
    imageSource.release();
}
