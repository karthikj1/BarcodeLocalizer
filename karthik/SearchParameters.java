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
package karthik;

import org.opencv.core.Size;

/**
 *
 * @author karthik Contains settings for miscellaneous parameters used in searching for barcodes instances are returned
 * by the factory methods for small and large barcode searches
 */
class SearchParameters {

    Size elem_size, large_elem_size;
    final int MAX_ROWS = 300;  //image with more rows than MAX_ROWS is scaled down to make finding barcode easier
    
    // threshold for ratio of contour area to bounding rectangle area - used to see if contour shape is roughly rectangular
    final double THRESHOLD_AREA_RATIO = 0.6;  

    double THRESHOLD_MIN_AREA_MULTIPLIER;
    double THRESHOLD_MIN_GRADIENT_EDGES_MULTIPLIER;
    double RECT_HEIGHT_MULTIPLIER;
    double RECT_WIDTH_MULTIPLIER;

    // size of rectangular window to calculate variance, probability etc around each pixel    
    protected int RECT_WIDTH, RECT_HEIGHT;
    protected double THRESHOLD_MIN_GRADIENT_EDGES; // min number of gradient edges in rectangular window to consider as non-zero
    protected double THRESHOLD_MIN_AREA; // min area for candidate region to be considered as a barcode

    int NUM_BLANKS_THRESHOLD;
    int MATRIX_NUM_BLANKS_THRESHOLD;

    private SearchParameters() {
    }

    static SearchParameters getNormalParameters() {
        // returns normal parameters used in barcode search
        SearchParameters params = new SearchParameters();

        params.THRESHOLD_MIN_AREA_MULTIPLIER = 0.02;
        params.THRESHOLD_MIN_GRADIENT_EDGES_MULTIPLIER = 0.3;

        params.NUM_BLANKS_THRESHOLD = 20;
        params.MATRIX_NUM_BLANKS_THRESHOLD = 10;

        params.RECT_HEIGHT_MULTIPLIER = 0.1;
        params.RECT_WIDTH_MULTIPLIER = 0.1;

        params.elem_size = new Size(10, 10);
        params.large_elem_size = new Size(12, 12);
        return params;
    }

    static SearchParameters getSmallParameters() {
        // returns parameters used when searching for barcodes that are small relative to image size
        // used as one of the TRY_HARDER options
        SearchParameters params = new SearchParameters();

        params.THRESHOLD_MIN_AREA_MULTIPLIER = 0.02;
        params.THRESHOLD_MIN_GRADIENT_EDGES_MULTIPLIER = 0.3;

        params.NUM_BLANKS_THRESHOLD = 10;
        params.MATRIX_NUM_BLANKS_THRESHOLD = 5;

        params.RECT_HEIGHT_MULTIPLIER = 0.02;
        params.RECT_WIDTH_MULTIPLIER = 0.02;

        params.elem_size = new Size(10, 10);
        params.large_elem_size = new Size(12, 12);
        return params;
    }

    static SearchParameters getLargeParameters() {
        // returns parameters used when searching for barcodes that are large relative to image size
        // has some success with localizing blurry barcodes also though they probably will not decode correctly
        // used with one of the TRY_HARDER options
        SearchParameters params = new SearchParameters();

        params.THRESHOLD_MIN_AREA_MULTIPLIER = 0.02;
        params.THRESHOLD_MIN_GRADIENT_EDGES_MULTIPLIER = 0.2;

        params.NUM_BLANKS_THRESHOLD = 40;
        params.MATRIX_NUM_BLANKS_THRESHOLD = 20;

        params.RECT_HEIGHT_MULTIPLIER = 0.1;
        params.RECT_WIDTH_MULTIPLIER = 0.1;

        params.elem_size = new Size(20, 20);
        params.large_elem_size = new Size(24, 24);
        return params;
    }
    
     SearchParameters setImageSpecificParameters(int rows, int cols) {
         /* sets parameters that are specific to the image being processed
          * based on the size of the image(potentially after it is preprocessed and rescaled
         */
        THRESHOLD_MIN_AREA = THRESHOLD_MIN_AREA_MULTIPLIER * cols * rows;
        RECT_HEIGHT = (int) (RECT_HEIGHT_MULTIPLIER * rows);
        RECT_WIDTH = (int) (RECT_WIDTH_MULTIPLIER * cols);
        THRESHOLD_MIN_GRADIENT_EDGES = RECT_HEIGHT * RECT_WIDTH * THRESHOLD_MIN_GRADIENT_EDGES_MULTIPLIER;
        
        return this;
    }

}
