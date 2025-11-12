// @ts-nocheck

import image from '@ohos.multimedia.image';

async function checkImageModel(imageFd: number): Promise<void> {
    const imageSource: image.ImageSource = image.createImageSource(imageFd);
    if (!imageSource || imageSource === undefined) {
        console.log("ImageSource is null or undefined, imageFd: " + imageFd);
        return;
    }
    console.log("OK");
}
