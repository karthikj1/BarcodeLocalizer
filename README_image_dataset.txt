README file for image_dataset.tar.gz
====================================

There are two folders in the archive called Linear and Matrix folders which contain source images for 1D and 2D barcodes respectively.

Under each folder, there are the source images as well as three sub-folders called candidates, localized and success.

Success – contains the barcode part of the images that was successfully decoded. 

Candidates – For images that didn’t decode, the program may have found zero, one or more candidates that failed to decode – those are all here. 

Localized – I manually inspected the candidates folder to see if there were images where the barcode localized properly but ZXing couldn’t decode it (probably because the barcode was blurry in the source image). Any such images are in the localized folder.

All of the image cropping and localization above was automatic except for the ones I manually selected and moved to the Localized folder.

There are also two text files that have the barcode values that ZXing found for images in the success folder.
