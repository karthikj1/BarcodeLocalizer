/*
 * Copyright (C) 2014 karthik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package karthik.Barcode;

import org.opencv.core.Mat;

/**
 *
 * @author karthik
 */
class ImageInfo {

    // container class for source image and various intermediate images created
    // while processing src_original to search for a barcode
    Mat src_original, src_scaled, src_grayscale, src_processed;
    Mat gradient_direction, gradient_magnitude;
    Mat adjusted_variance;
    
    Barcode.CodeType searchType;

    ImageInfo(Mat src) {
       src_original = src;
       src_scaled = src_original.clone();
    }       
    
}
