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

import java.awt.image.BufferedImage;
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

    /**
     * @param args the command line arguments
     */        
   
    public MatrixBarcode(String filename) {
        super(filename);
        img_details.searchType = CodeType.MATRIX;

    }

    public MatrixBarcode(String filename, boolean debug) {
        this(filename);
        DEBUG_IMAGES = debug;
    }


    protected List<BufferedImage> locateBarcode() throws IOException{

        preprocess_image();

        img_details.src_processed = findCandidates();   // find areas with low variance in gradient direction

        connectComponents();
        
        if (DEBUG_IMAGES){
            write_Mat("E3.csv", img_details.src_processed);
            ImageDisplay.showImageFrame(img_details.src_processed, "Image img_details.E3 after morph close and open");
        }
        List<MatOfPoint> contours = new ArrayList<>();
        // findContours modifies source image so src_processed pass it a cosrc_processed of img_details.E3
        // img_details.E3 will be used again shortly to expand the bsrc_processedcode region
        Imgproc.findContours(img_details.src_processed.clone(),
            contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double bounding_rect_area = 0;
        RotatedRect minRect;
        Mat ROI;
        for (int i = 0; i < contours.size(); i++) {
            double area = Imgproc.contourArea(contours.get(i));
            minRect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            bounding_rect_area = minRect.size.width * minRect.size.height;

            if (area < searchParams.THRESHOLD_MIN_AREA) // ignore contour if it is of too small a region
                continue;
                        
            if ((area / bounding_rect_area) > searchParams.THRESHOLD_AREA_RATIO) // check if contour is of a rectangular object
            {
                CandidateBarcode cb = new CandidateBarcode(img_details, minRect, searchParams);
                if(DEBUG_IMAGES)
                    cb.debug_drawCandidateRegion(minRect, new Scalar(0, 255, 0), img_details.src_scaled);
                // get candidate regions to be a barcode
               // minRect = cb.getCandidateRegion();
                minRect.size.width += 2 + 2*searchParams.MATRIX_NUM_BLANKS_THRESHOLD;
                minRect.size.height += 2 + 2*searchParams.MATRIX_NUM_BLANKS_THRESHOLD;
                ROI = cb.NormalizeCandidateRegion(Barcode.USE_ROTATED_RECT_ANGLE);  
                
                if((statusFlags & TryHarderFlags.POSTPROCESS_RESIZE_BARCODE.value()) != 0)
                    ROI = scale_candidateBarcode(ROI);               
                
                candidateBarcodes.add(ImageDisplay.getBufImg(ROI));
                if (DEBUG_IMAGES) {
                    cb.debug_drawCandidateRegion(minRect, new Scalar(0, 0, 255), img_details.src_original);
                }
            }
        }
        if(DEBUG_IMAGES)
            ImageDisplay.showImageFrame(img_details.src_scaled, name + " with candidate regions");

        return candidateBarcodes;
    }

 
    private Mat findCandidates() {
        // find candidate regions that may contain barcodes
        //  modifies class variable img_details.gradient_direction to contain gradient directions
        Mat probabilities;
        img_details.gradient_direction = Mat.zeros(rows, cols, CvType.CV_32F);

        double angle;
        Mat scharr_x, scharr_y;
        scharr_x = new Mat(rows, cols, CvType.CV_32F);
        scharr_y = new Mat(rows, cols, CvType.CV_32F);

        Imgproc.Scharr(img_details.src_grayscale, scharr_x, CvType.CV_32F, 1, 0);
        Imgproc.Scharr(img_details.src_grayscale, scharr_y, CvType.CV_32F, 0, 1);

        // calc angle using Core.phase function - should be quicker than using atan2 manually
        Core.phase(scharr_x, scharr_y, img_details.gradient_direction, true);

        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                angle = img_details.gradient_direction.get(i, j)[0];
                angle = angle % 180;
                angle = (angle > 170) ? 0 : angle;
                img_details.gradient_direction.put(i, j, angle);
            }

        // convert type after modifying angle so that angles above 360 don't get truncated
        img_details.gradient_direction.convertTo(img_details.gradient_direction, CvType.CV_8U);
        if(DEBUG_IMAGES)
            write_Mat("angles.csv", img_details.gradient_direction);

        // calculate magnitude of gradient, normalize and threshold
        img_details.gradient_magnitude = Mat.zeros(scharr_x.size(), scharr_x.type());
        Core.magnitude(scharr_x, scharr_y, img_details.gradient_magnitude);
        Core.normalize(img_details.gradient_magnitude, img_details.gradient_magnitude, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        if(DEBUG_IMAGES)
            write_Mat("magnitudes.csv", img_details.gradient_magnitude);

        // calculate probabilities for each pixel from window around it, normalize and threshold
        probabilities = calcHistogramProbabilities();
        Core.normalize(probabilities, probabilities, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);        
        Imgproc.threshold(probabilities, probabilities, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        
        if (DEBUG_IMAGES){
            write_Mat("probabilities.csv", probabilities);
            ImageDisplay.showImageFrame(img_details.gradient_magnitude, "Magnitudes");
            ImageDisplay.showImageFrame(probabilities, "histogram probabilities");            
        }
        return probabilities;
    }

    private Mat calcHistogramProbabilities() {
        int right_col, left_col, top_row, bottom_row;
        int DUMMY_ANGLE = 255;
        int BIN_WIDTH = 15;
        int HIST_INC = 1;
    
        MatOfInt hist = new MatOfInt();
        Mat imgWindow; // used to hold sub-matrices from the image that represent the window around the current point
        int bins = 180 / BIN_WIDTH;

        MatOfInt mHistSize = new MatOfInt(bins);
        MatOfFloat mRanges = new MatOfFloat(0, 179);
        MatOfInt mChannels = new MatOfInt(0);

        // set angle to DUMMY_ANGLE = 255 at all points where gradient magnitude is 0 i.e. where there are no edges
        // these angles will be ignored in the histogram calculation since that counts only up to 180
        Mat mask = Mat.zeros(img_details.gradient_direction.size(), CvType.CV_8U);
        Core.inRange(img_details.gradient_magnitude, new Scalar(0), new Scalar(0), mask);
        img_details.gradient_direction.setTo(new Scalar(DUMMY_ANGLE), mask);
        if(DEBUG_IMAGES)
            write_Mat("angles_modified.csv", img_details.gradient_direction);

        int width_offset = searchParams.RECT_WIDTH / 2;
        int height_offset = searchParams.RECT_HEIGHT / 2;
        int rect_area;
        Mat prob_mat = Mat.zeros(rows, cols, CvType.CV_32F);
        double prob, max_angle_count, second_highest_angle_count, angle_diff;
        int[][] histLocs;
        for (int i = 0; i < rows; i += HIST_INC) {
            // first calculate the row locations of the rectangle and set them to -1 
            // if they are outside the matrix bounds

            top_row = ((i - height_offset - 1) < 0) ? -1 : (i - height_offset - 1);
            bottom_row = ((i + height_offset) > rows) ? rows : (i + height_offset);

            for (int j = 0; j < cols; j += HIST_INC) {
                // first check if there is a gradient at this pixel
                // no processing needed if so
                if (img_details.gradient_magnitude.get(i, j)[0] == 0)
                    continue;

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                
                left_col = ((j - width_offset - 1) < 0) ? -1 : (j - width_offset - 1);
                right_col = ((j + width_offset) > cols) ? cols : (j + width_offset);
                // TODO: do this more efficiently               

                rect_area = Core.countNonZero(img_details.gradient_magnitude.submat(Math.max(top_row, 0), bottom_row, Math.max(
                    left_col, 0), right_col));
                
                if (rect_area < searchParams.THRESHOLD_MIN_GRADIENT_EDGES) // if gradient density is below the threshold level, prob of matrix code at this pixel is 0
                    continue;
                imgWindow = img_details.gradient_direction.
                    submat(Math.max(top_row, 0), bottom_row, Math.max(left_col, 0), right_col);
                Imgproc.calcHist(Arrays.asList(imgWindow), mChannels, new Mat(), hist, mHistSize, mRanges, false);
                hist.convertTo(hist, CvType.CV_32S);
                histLocs = getMaxElements(hist);
  
                max_angle_count = histLocs[0][1];
                second_highest_angle_count = histLocs[1][1];                
                angle_diff = Math.abs(histLocs[0][0] - histLocs[1][0]) * BIN_WIDTH;
                prob = 1 - (Math.abs(angle_diff - 90) / 90.0);
                prob = prob * 2 * Math.min(max_angle_count, second_highest_angle_count) / (max_angle_count + second_highest_angle_count);
                prob_mat.put(i, j, prob);                             
            }  // for j
        }  // for i
        // dilate matrix so that each pixel gets dilated to fill a square around it
        if(HIST_INC > 1){
            Mat dilation_elem = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(HIST_INC, HIST_INC));
            Imgproc.morphologyEx(prob_mat, prob_mat, Imgproc.MORPH_DILATE, dilation_elem);
        }
        return prob_mat;

    }
    
    private int[][] getMaxElements(MatOfInt histogram) {
        // returns an array of size 2 containing the indices of the highest two elements in 
        // the histogram in hist. Used by calcHist method - only works with 1D histogram
        // first element of return array is the highest and second element is second highest
        // TODO: replace this with a more efficient in-place algorithm

      
        Mat hist = histogram.clone();
        int[][] histLocs = new int[2][2];
        Core.MinMaxLocResult result = Core.minMaxLoc(hist);
        histLocs[0][0] = (int) result.maxLoc.y;
        histLocs[0][1] = (int) hist.get(histLocs[0][0], 0)[0];        

        // now set highest-val location to a low number. The previous second-highest bin is now the highest bin
        hist.put((int) result.maxLoc.y, (int) result.maxLoc.x, 0);
        result = Core.minMaxLoc(hist);
        histLocs[1][0] = (int) result.maxLoc.y;
        histLocs[1][1] = (int) hist.get(histLocs[1][0], 0)[0];

        return histLocs;
    }
}
