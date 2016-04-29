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

import java.io.IOException;
import java.util.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

/**
 *
 * @author karthik
 */
public class MatrixBarcode extends Barcode {

    // used in histogram calculation
    private static final int DUMMY_ANGLE = 255;    
    private static final Scalar ZERO_SCALAR = new Scalar(0);

    private static Mat hist = new MatOfInt(ImageInfo.bins, 1);
    private static Mat histIdx = new Mat();
    private static Mat mask = new Mat(); // empty Mat to use as mask for histogram calculation
    private static Mat angles;
    
    private static final Mat hierarchy = new Mat(); // empty Mat required as parameter in contour finding. Not used anywhere else.
    private static final Map<Integer, Scalar> scalarDict = new HashMap<Integer, Scalar>();
    
    static{
        // create a hashmap with Scalar objects used during histogram calculation
        // done so that we can reuse these objects instead of creating and destroying them
        for(int r = 1; r <= 181; r += ImageInfo.BIN_WIDTH)
            scalarDict.put(r, new Scalar(r));
        
        // add objects used when trimming angles to 0-360 range
        scalarDict.put(170, new Scalar(170));
        scalarDict.put(180, new Scalar(180));
        scalarDict.put(-180, new Scalar(-180));
        scalarDict.put(360, new Scalar(360));
        scalarDict.put(DUMMY_ANGLE, new Scalar(DUMMY_ANGLE));
    }
    
    public MatrixBarcode(String filename, boolean debug, TryHarderFlags flag) throws IOException{
        super(filename, flag);
        DEBUG_IMAGES = debug;
        img_details.searchType = CodeType.MATRIX;
   }

    public MatrixBarcode(String image_name, Mat img, TryHarderFlags flag) throws IOException{
        super(img, flag);
        name = image_name;
        img_details.searchType = CodeType.MATRIX;
        DEBUG_IMAGES = false;
    }

    public List<CandidateResult> locateBarcode() throws IOException{
        
        calcGradientDirectionAndMagnitude();
        for(int tileSize = searchParams.tileSize; tileSize < rows && tileSize < cols; tileSize *= 4){            
            img_details.probabilities = calcProbabilityMatrix(tileSize);   // find areas with low variance in gradient direction

        //    connectComponents();
            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            // findContours modifies source image so probabilities pass it a clone of img_details.probabilities
            // img_details.probabilities will be used again shortly to expand the bsrcode region
            Imgproc.findContours(img_details.probabilities.clone(),
                contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            double bounding_rect_area = 0;
            RotatedRect minRect;
            CandidateResult ROI;
            int area_multiplier = (searchParams.RECT_HEIGHT * searchParams.RECT_WIDTH) / (searchParams.PROB_MAT_TILE_SIZE * searchParams.PROB_MAT_TILE_SIZE);
        // pictures were downsampled during probability calc so we multiply it by the tile size to get area in the original picture

            for (int i = 0; i < contours.size(); i++) {
                double area = Imgproc.contourArea(contours.get(i));

                if (area * area_multiplier < searchParams.THRESHOLD_MIN_AREA) // ignore contour if it is of too small a region
                    continue;

                minRect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
                bounding_rect_area = minRect.size.width * minRect.size.height;
                if (DEBUG_IMAGES) {
                    System.out.println(
                        "Area is " + area * area_multiplier + " MIN_AREA is " + searchParams.THRESHOLD_MIN_AREA);
                    System.out.println("area ratio is " + ((area / bounding_rect_area)));
                }

                if ((area / bounding_rect_area) > searchParams.THRESHOLD_AREA_RATIO) // check if contour is of a rectangular object
                {
                    CandidateMatrixBarcode cb = new CandidateMatrixBarcode(img_details, minRect, searchParams);
                    if (DEBUG_IMAGES)
                        cb.debug_drawCandidateRegion(new Scalar(0, 255, 128), img_details.src_scaled);
                    // get candidate regions to be a barcode

                    // rotates candidate region to straighten it based on the angle of the enclosing RotatedRect                
                    ROI = cb.NormalizeCandidateRegion(Barcode.USE_ROTATED_RECT_ANGLE);  
                    if(postProcessResizeBarcode)
                        ROI.ROI = scale_candidateBarcode(ROI.ROI);               

                    ROI.candidate = ImageDisplay.getBufImg(ROI.ROI);
                    candidateBarcodes.add(ROI);

                    if (DEBUG_IMAGES)
                        cb.debug_drawCandidateRegion(new Scalar(0, 0, 255), img_details.src_scaled);
                }
            }
            if (DEBUG_IMAGES)
                ImageDisplay.showImageFrameGrid(img_details.src_scaled, name + " with candidate regions");
        }
        return candidateBarcodes;
    }

 
    private void calcGradientDirectionAndMagnitude() {
        // calculates magnitudes and directions of gradients in the image
        // results are stored in appropriate matrices in img_details object
        
        Imgproc.Scharr(img_details.src_grayscale, img_details.scharr_x, CvType.CV_32F, 1, 0);
        Imgproc.Scharr(img_details.src_grayscale, img_details.scharr_y, CvType.CV_32F, 0, 1);

        // calc angle using Core.phase function - quicker than using atan2 manually
        Core.phase(img_details.scharr_x, img_details.scharr_y, img_details.gradient_direction, true);

        // convert angles from 180-360 to 0-180 range and set angles from 170-180 to 0
        Core.inRange(img_details.gradient_direction, scalarDict.get(180), scalarDict.get(360), img_details.mask);
        Core.add(img_details.gradient_direction, scalarDict.get(-180), img_details.gradient_direction, img_details.mask);
        Core.inRange(img_details.gradient_direction, scalarDict.get(170), scalarDict.get(180), img_details.mask);
        img_details.gradient_direction.setTo(ZERO_SCALAR, img_details.mask);
        
        // convert type after modifying angle so that angles above 360 don't get truncated
        img_details.gradient_direction.convertTo(img_details.gradient_direction, CvType.CV_8U);
        if(DEBUG_IMAGES)
            write_Mat("angles.csv", img_details.gradient_direction);

        // calculate magnitude of gradient, normalize and threshold
        Core.magnitude(img_details.scharr_x, img_details.scharr_y, img_details.gradient_magnitude);
        Core.normalize(img_details.gradient_magnitude, img_details.gradient_magnitude, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        
        // set angle to DUMMY_ANGLE = 255 at all points where gradient magnitude is 0 i.e. where there are no edges
        // these angles will be ignored in the histogram calculation since that counts only up to 180
        Core.inRange(img_details.gradient_magnitude, ZERO_SCALAR, ZERO_SCALAR, img_details.mask);
        img_details.gradient_direction.setTo(scalarDict.get(DUMMY_ANGLE), img_details.mask);
        // add 1 to gradient directions so that gradients of 0 can be located
        Core.add(img_details.gradient_direction, new Scalar(1), img_details.gradient_direction);

        // calculate integral image for edge density
        img_details.edgeDensity = calcEdgeDensityIntegralImage();
        
        // calculate histograms for each tile
        calcHistograms();
        
        if(DEBUG_IMAGES){
            write_Mat("magnitudes.csv", img_details.gradient_magnitude);
            write_Mat("angles_modified.csv", img_details.gradient_direction);
        }
    }

    private Mat calcProbabilityMatrix(int tileSize){
        // calculate probability of a barcode region in each tile based on HOG data for each tile
        
        // calculate probabilities for each pixel from window around it, normalize and threshold
        Mat probabilities = calcProbabilityTilings(tileSize);        
     
        double debug_prob_thresh = Imgproc.threshold(probabilities, probabilities, 128, 255, Imgproc.THRESH_BINARY);
        
        if (DEBUG_IMAGES){
            System.out.println("Probability threshold is " + debug_prob_thresh);
            write_Mat("probabilities.csv", probabilities);
            ImageDisplay.showImageFrameGrid(img_details.gradient_magnitude, "Magnitudes");
            ImageDisplay.showImageFrameGrid(probabilities, "histogram probabilities");            
        }
        return probabilities;        
    }
       
    private Mat calcProbabilityTilings(int tileSize){
    // calculates probability of each tile being in a 2D barcode region
    // tiles must be square
        assert(searchParams.RECT_HEIGHT == searchParams.RECT_WIDTH): "RECT_HEIGHT and RECT_WIDTH must be equal in searchParams imageSpecificParams";

        int probMatTileSize = (int) (tileSize * (searchParams.PROB_MAT_TILE_SIZE/(1.0 * searchParams.tileSize)));
        int threshold_min_gradient_edges = (int)(tileSize * tileSize * searchParams.THRESHOLD_MIN_GRADIENT_EDGES_MULTIPLIER);
        
        int right_col, bottom_row;
        int prob_mat_right_col, prob_mat_bottom_row;
        
        Mat prob_window; // used to hold sub-matrices into probability matrix that represent window around current point

        int num_edges;
        double prob;
        int max_angle_idx, second_highest_angle_index, max_angle_count, second_highest_angle_count, angle_diff;
        
        img_details.probabilities.setTo(ZERO_SCALAR);

        for(int i = 0, row_offset = 0; i < rows; i += tileSize, row_offset += probMatTileSize){
            // first do bounds checking for bottom right of tiles
            
            bottom_row = java.lang.Math.min((i + tileSize), rows);
            prob_mat_bottom_row = java.lang.Math.min((row_offset + probMatTileSize), img_details.probMatRows);

            for(int j = 0, col_offset = 0; j < cols; j += tileSize, col_offset += probMatTileSize){

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                
                right_col = java.lang.Math.min((j + tileSize), cols);
                prob_mat_right_col = java.lang.Math.min((col_offset + probMatTileSize), img_details.probMatCols);

                // calculate number of edges in the tile using the already calculated integral image 
                num_edges = (int) calc_rect_sum(img_details.edgeDensity, i, bottom_row, j, right_col);
                
                if (num_edges < threshold_min_gradient_edges) 
                // if gradient density is below the threshold level, prob of matrix code in this tile is 0
                    continue;
                
                for(int r = 0; r < img_details.bins; r++){
                    img_details.histArray[r] = (int) calc_rect_sum(img_details.histIntegrals.get(r), i, bottom_row, j, right_col);
                }
                
                hist = Converters.vector_int_to_Mat(Arrays.asList(img_details.histArray));
                // Mat imgWindow = img_details.gradient_direction.submat(i, bottom_row, j, right_col);
                // Imgproc.calcHist(Arrays.asList(imgWindow), mChannels, histMask, hist, mHistSize, mRanges, false);
                Core.sortIdx(hist, histIdx, Core.SORT_EVERY_COLUMN + Core.SORT_DESCENDING);

                max_angle_idx = (int) histIdx.get(0, 0)[0];
                max_angle_count = (int) hist.get(max_angle_idx, 0)[0];

                second_highest_angle_index = (int) histIdx.get(1, 0)[0];
                second_highest_angle_count = (int) hist.get(second_highest_angle_index, 0)[0];                
  
                angle_diff = Math.abs(max_angle_idx - second_highest_angle_index);
                
                // formula below is modified from Szentandrasi, Herout, Dubska paper pp. 4
                prob = 0;
                if(angle_diff != 1) // ignores tiles where there is just noise between adjacent bins in the histogram
                    prob = 2.0 * Math.min(max_angle_count, second_highest_angle_count) / (max_angle_count + second_highest_angle_count);
                
                prob_window = img_details.probabilities.submat(row_offset, prob_mat_bottom_row, col_offset, prob_mat_right_col);
                prob_window.setTo(new Scalar((int) (prob*255)));
                        
            }  // for j
        }  // for i
        
        return img_details.probabilities;
                
    }
    
    private Mat calcEdgeDensityIntegralImage(){
        // calculates number of edges in the image and returns it as an integral image
        // first set all non-zero gradient magnitude points (i.e. all edges) to 1
        // then calculate the integral image from the above
        // we can now calculate the number of edges in any tile in the matrix using the integral image
        Mat temp = new Mat();
        
        Imgproc.threshold(img_details.gradient_magnitude, temp, 1, 1, Imgproc.THRESH_BINARY);
        Imgproc.integral(temp, img_details.edgeDensity);
        
        return img_details.edgeDensity;
    }
        
    private void calcHistograms(){        
        Mat target;
        angles = img_details.gradient_direction.clone();

        for(int binRange = 1, integralIndex = 0; binRange < 181; binRange += img_details.BIN_WIDTH, integralIndex++){            
            target  = img_details.histIntegrals.get(integralIndex);
            
            img_details.gradient_direction.copyTo(angles);
            Core.inRange(img_details.gradient_direction, scalarDict.get(binRange), scalarDict.get(binRange + img_details.BIN_WIDTH), mask);
            Core.bitwise_not(mask, mask);   
            angles.setTo(ZERO_SCALAR, mask);
            Mat temp = new Mat();
            
            Imgproc.threshold(angles, temp, 0, 1, Imgproc.THRESH_BINARY);
            Imgproc.integral(temp, target);            
        }
    }
        
 }
