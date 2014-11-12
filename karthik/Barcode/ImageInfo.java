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

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;

/**
 *
 * @author karthik
 */
class ImageInfo {

    // container class for source image and various intermediate images created
    // while processing src_original to search for a barcode
    Barcode.CodeType searchType;

    Mat src_original, src_scaled, src_grayscale, probabilities;
    Mat gradient_direction, gradient_magnitude;
    Mat scharr_x, scharr_y;
    Mat mask;
    
    // matrices used in CandidateMatrixBarcode class
    Mat rotation_matrix, enlarged, rotated;
    Mat delta = Mat.zeros(3, 3, CvType.CV_32F);
    Mat newCornerCoord = Mat.zeros(2, 1, CvType.CV_32F);
    Mat coord = Mat.ones(3, 1, CvType.CV_32F);
    Point enlarged_centre;
    
    List<Point> newCornerPoints = new ArrayList<Point>(4);
    List<Point> transformedPoints = new ArrayList<Point>(4);
        
    int offsetX, offsetY;
    

    ImageInfo(Mat src) {
       src_original = src;
       gradient_direction = new Mat();
       gradient_magnitude = new Mat();
       
       scharr_x = new Mat();
       scharr_y = new Mat();
       mask = new Mat();
    
       // create matrices for CandidateMatrixBarcode class
        int orig_rows = src_original.rows();
        int orig_cols = src_original.cols();
        int diagonal = (int) Math.sqrt(orig_rows * orig_rows + orig_cols * orig_cols);

        int newWidth = diagonal + 1;
        int newHeight = diagonal + 1;

        offsetX = (newWidth - orig_cols) / 2;
        offsetY = (newHeight - orig_rows) / 2;
        enlarged = new Mat(newWidth, newHeight, src_original.type());
        rotated = Mat.zeros(enlarged.size(), enlarged.type());

        enlarged_centre = new Point(enlarged.rows() / 2.0, enlarged.cols() / 2.0);

    }       
    
}
