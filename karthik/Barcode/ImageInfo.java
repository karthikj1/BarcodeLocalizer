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
    Mat rotation_matrix;
    Mat delta = Mat.zeros(3, 3, CvType.CV_32F);
    Mat newCornerCoord = Mat.zeros(2, 1, CvType.CV_32F);
    Mat coord = Mat.ones(3, 1, CvType.CV_32F);    
    
    List<Point> newCornerPoints = new ArrayList<Point>(4);
    List<Point> transformedPoints = new ArrayList<Point>(4);
        
    Point[] scaledCorners = new Point[4];

    // used in histogram calculation
    protected static final int BIN_WIDTH = 15;  // bin width for histogram    
    protected static final int bins = 180 / BIN_WIDTH;

    int probMatRows, probMatCols;
    Mat edgeDensity;
    List<Mat> histograms = new ArrayList<Mat>();
    List<Mat> histIntegrals = new ArrayList<Mat>();
    
    Integer[] histArray = new Integer[bins];
    
    ImageInfo(Mat src) {
       src_original = src;
       gradient_direction = new Mat();
       gradient_magnitude = new Mat();
       
       scharr_x = new Mat();
       scharr_y = new Mat();
       mask = new Mat();
       
       // create List of Point's for CandidateMatrixBarcode
       for(int r = 0; r < 4; r++){
           newCornerPoints.add(new Point());
           transformedPoints.add(new Point());
       }
    }
    
    protected void initializeMats(int rows, int cols, SearchParameters searchParams){
        probabilities = Mat.zeros((int) (rows * searchParams.scale_factor + 1), (int) (cols * searchParams.scale_factor + 1), CvType.CV_8U);
        src_grayscale = new Mat(rows, cols, CvType.CV_32F);
        probMatRows = probabilities.rows();
        probMatCols = probabilities.cols();
        edgeDensity = Mat.zeros((int) (rows/(1.0 * searchParams.tileSize)),(int) (cols/(1.0 * searchParams.tileSize)), CvType.CV_16U);
        // create Mat objects to contain integral histograms
        for(int r = 0; r < bins; r++){
            histograms.add(Mat.zeros((int) (rows/(1.0 * searchParams.tileSize) + 1), (int) (cols/(1.0 * searchParams.tileSize) + 1), CvType.CV_32F));
            histIntegrals.add(Mat.zeros((int)(rows/(1.0 * searchParams.tileSize) + 1), (int) (cols/(1.0 * searchParams.tileSize) + 1), CvType.CV_32FC1));
        }
    }
    
}
