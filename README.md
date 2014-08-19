BarcodeLocalizer
================

OpenCV and Java based barcode localizer

This library localizes 1-D and 2-D barcodes from arbitrary images and is able to successfully handle noisy backgrounds as well as barcodes that are skewed or rotated out of plane. The resulting cropped and localized images can then be decoded by other barcode decoders such as ZXing. 

Most barcode decoders, such as ZXing, cannot easily handle noisy images or images where the barcode is rotated, so using this package as a localizer prior to decoding significantly improves decode success rates.
