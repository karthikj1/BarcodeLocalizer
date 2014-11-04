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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author karthik
 */
public class MatrixBarcode extends Barcode {

    // used in histogram calculation
    private static final int DUMMY_ANGLE = 255;
    private static final int BIN_WIDTH = 15;  // bin width for histogram    
    private static final int bins = 180 / BIN_WIDTH;
    private static final Scalar DUMMY_ANGLE_SCALAR = new Scalar(DUMMY_ANGLE);
    private static final Scalar ZERO_SCALAR = new Scalar(0);

    private static final MatOfInt mHistSize = new MatOfInt(bins);
    private static final MatOfFloat mRanges = new MatOfFloat(0, 179);
    private static final MatOfInt mChannels = new MatOfInt(0);
    private static MatOfInt hist = new MatOfInt();
    private static Mat histIdx = new Mat();
    
  
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
        
        img_details.probabilities = findCandidates();   // find areas with low variance in gradient direction

    //    connectComponents();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        // findContours modifies source image so probabilities pass it a clone of img_details.probabilities
        // img_details.probabilities will be used again shortly to expand the bsrcode region
        Imgproc.findContours(img_details.probabilities.clone(),
            contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double bounding_rect_area = 0;
        RotatedRect minRect;
        CandidateResult ROI;
        int area_multiplier = (searchParams.RECT_HEIGHT * searchParams.RECT_WIDTH) / (searchParams.TILE_SIZE * searchParams.TILE_SIZE);
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

        return candidateBarcodes;
    }

 
    private Mat findCandidates() {
        // find candidate regions that may contain barcodes
        //  modifies class variable img_details.gradient_direction to contain gradient directions
        Mat probabilities;
        Imgproc.Scharr(img_details.src_grayscale, img_details.scharr_x, CvType.CV_32F, 1, 0);
        Imgproc.Scharr(img_details.src_grayscale, img_details.scharr_y, CvType.CV_32F, 0, 1);

        // calc angle using Core.phase function - should be quicker than using atan2 manually
        Core.phase(img_details.scharr_x, img_details.scharr_y, img_details.gradient_direction, true);

        // convert angles from 180-360 to 0-180 range and set angles from 170-180 to 0
        Core.inRange(img_details.gradient_direction, new Scalar(180), new Scalar(360), img_details.mask);
        Core.add(img_details.gradient_direction, new Scalar(-180), img_details.gradient_direction, img_details.mask);
        Core.inRange(img_details.gradient_direction, new Scalar(170), new Scalar(180), img_details.mask);
        img_details.gradient_direction.setTo(new Scalar(0), img_details.mask);
        
        // convert type after modifying angle so that angles above 360 don't get truncated
        img_details.gradient_direction.convertTo(img_details.gradient_direction, CvType.CV_8U);
        if(DEBUG_IMAGES){
            write_Mat("angles.csv", img_details.gradient_direction);
        }
        // calculate magnitude of gradient, normalize and threshold
        Core.magnitude(img_details.scharr_x, img_details.scharr_y, img_details.gradient_magnitude);
        Core.normalize(img_details.gradient_magnitude, img_details.gradient_magnitude, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        if(DEBUG_IMAGES)
            write_Mat("magnitudes.csv", img_details.gradient_magnitude);

        // calculate probabilities for each pixel from window around it, normalize and threshold
        probabilities = calcProbabilityTilings();
        if (DEBUG_IMAGES)
            write_Mat("probabilities_raw.csv", probabilities);
     
//        Core.normalize(probabilities, probabilities, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);        
//        double debug_prob_thresh = Imgproc.threshold(probabilities, probabilities, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        
      //  probabilities.convertTo(probabilities, CvType.CV_8U, 255);  // scale raw probabilities to be from 0 - 255
        double debug_prob_thresh = Imgproc.threshold(probabilities, probabilities, 128, 255, Imgproc.THRESH_BINARY);
        
        if (DEBUG_IMAGES){
            System.out.println("Probability threshold is " + debug_prob_thresh);
            write_Mat("probabilities.csv", probabilities);
            ImageDisplay.showImageFrameGrid(img_details.gradient_magnitude, "Magnitudes");
            ImageDisplay.showImageFrameGrid(probabilities, "histogram probabilities");            
        }
        return probabilities;
    }

    private Mat calcProbabilityTilings(){
    // calculates probability of each tile being in a 2D barcode region
    // tiles must be square
        assert(searchParams.RECT_HEIGHT == searchParams.RECT_WIDTH): "RECT_HEIGHT and RECT_WIDTH must be equal in searchParams imageSpecificParams";

        int right_col, bottom_row;
        
        Mat imgWindow; // used to hold sub-matrices from the image that represent the window around the current point
        Mat prob_window;

        int num_edges;
        double prob;
        int max_angle_idx, second_highest_angle_index, max_angle_count, second_highest_angle_count, angle_diff;
        
        int offset_increment = (int) (searchParams.tileSize * searchParams.scale_factor);
        prob_mat.setTo(ZERO_SCALAR);
        // set angle to DUMMY_ANGLE = 255 at all points where gradient magnitude is 0 i.e. where there are no edges
        // these angles will be ignored in the histogram calculation since that counts only up to 180
        Core.inRange(img_details.gradient_magnitude, ZERO_SCALAR, ZERO_SCALAR, img_details.mask);
        img_details.gradient_direction.setTo(DUMMY_ANGLE_SCALAR, img_details.mask);
        if(DEBUG_IMAGES)
            write_Mat("angles_modified.csv", img_details.gradient_direction);

        for(int i = 0, row_offset = 0; i < rows; i += searchParams.tileSize, row_offset += offset_increment){
            // first calculate the row locations of the rectangle and set them to -1 
            // if they are outside the matrix bounds

            bottom_row = ((i + searchParams.tileSize) > rows) ? rows : (i + searchParams.tileSize);

            for(int j = 0, col_offset = 0; j < cols; j += searchParams.tileSize, col_offset += offset_increment){

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                
                right_col = ((j + searchParams.tileSize) > cols) ? cols : (j + searchParams.tileSize);
                // TODO: do this more efficiently               

                num_edges = Core.countNonZero(img_details.gradient_magnitude.submat(i, bottom_row, j, right_col));                
                
                if (num_edges < searchParams.THRESHOLD_MIN_GRADIENT_EDGES) 
                // if gradient density is below the threshold level, prob of matrix code in this tile is 0
                    continue;
                imgWindow = img_details.gradient_direction.submat(i, bottom_row, j, right_col);
                Imgproc.calcHist(Arrays.asList(imgWindow), mChannels, new Mat(), hist, mHistSize, mRanges, false);
                Core.sortIdx(hist, histIdx, Core.SORT_EVERY_COLUMN + Core.SORT_DESCENDING);

                max_angle_idx = (int) histIdx.get(0, 0)[0];
                max_angle_count = (int) hist.get(max_angle_idx, 0)[0];

                second_highest_angle_index = (int) histIdx.get(1, 0)[0];
                second_highest_angle_count = (int) hist.get(second_highest_angle_index, 0)[0];                
  
                angle_diff = Math.abs(max_angle_idx - second_highest_angle_index);
                
                // formula below is modified from Szentandrasi, Herout, Dubska paper pp. 4
                prob = 2.0 * Math.min(max_angle_count, second_highest_angle_count) / (max_angle_count + second_highest_angle_count);
                prob = (angle_diff == 1) ? 0 : prob; // ignores tiles where there is just noise between adjacent bins in the histogram
                
                prob_window = prob_mat.submat(row_offset, row_offset + searchParams.TILE_SIZE, col_offset, col_offset + searchParams.TILE_SIZE);
                prob_window.setTo(new Scalar((int) (prob*255)));
                        
            }  // for j
        }  // for i
        
        return prob_mat;
                
    }
 }
