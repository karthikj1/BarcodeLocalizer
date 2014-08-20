BarcodeLocalizer
================

OpenCV and Java based barcode localizer

This library localizes 1-D and 2-D barcodes from arbitrary images and is able to successfully handle noisy backgrounds as well as barcodes that are skewed or rotated out of plane. The resulting cropped and localized images can then be decoded by other software barcode decoders. 

Most software barcode decoders cannot easily handle noisy image and require the barcode to be roughly centered, isolated and horizontal within the source image.Using this package as a localizer prior to decoding significantly improves decode success rates.

Compared to ZXing, a popular open source decoding library, the success rate almost doubled for both 1-D and 2-D barcodes.

See http://karthikj1.github.io/BarcodeLocalizer/ for more details.

image_dataset.ZIP contains the image files used for testing.