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

    /**
     * @param args the command line arguments
     */        
   
    public MatrixBarcode(String filename) throws IOException{
        super(filename);
        img_details.searchType = CodeType.MATRIX;

    }

    public MatrixBarcode(String filename, boolean debug) throws IOException{
        this(filename);
        DEBUG_IMAGES = debug;
    }


    protected List<CandidateResult> locateBarcode() throws IOException{
        
        preprocess_image();

        img_details.src_processed = findCandidates();   // find areas with low variance in gradient direction

        connectComponents();
        
        if (DEBUG_IMAGES){
            write_Mat("Processed.csv", img_details.src_processed);
            ImageDisplay.showImageFrameGrid(img_details.src_processed, "Image after morph close and open");
        }
        List<MatOfPoint> contours = new ArrayList<>();
        // findContours modifies source image so src_processed pass it a clone of img_details.src_processed
        // img_details.src_processed will be used again shortly to expand the bsrcode region
        Imgproc.findContours(img_details.src_processed.clone(),
            contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double bounding_rect_area = 0;
        RotatedRect minRect;
        CandidateResult ROI;
        int area_multiplier = (searchParams.RECT_HEIGHT * searchParams.RECT_WIDTH)/(searchParams.TILE_SIZE * searchParams.TILE_SIZE);  
    // pictures were downsampled during probability calc so we multiply it by the tile size to get area in the original picture
        
            for (int i = 0; i < contours.size(); i++) {
                double area = Imgproc.contourArea(contours.get(i));
                minRect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
                bounding_rect_area = minRect.size.width * minRect.size.height;
                if(DEBUG_IMAGES){
                    System.out.println("Area is " + area * area_multiplier + " MIN_AREA is " + searchParams.THRESHOLD_MIN_AREA);
                    System.out.println("area ratio is " + ((area / bounding_rect_area)));
            }
            
            if (area * area_multiplier < searchParams.THRESHOLD_MIN_AREA) // ignore contour if it is of too small a region
                continue;
                        
            if ((area / bounding_rect_area) > searchParams.THRESHOLD_AREA_RATIO) // check if contour is of a rectangular object
            {
                CandidateMatrixBarcode cb = new CandidateMatrixBarcode(img_details, minRect, searchParams);
                if(DEBUG_IMAGES)
                    cb.debug_drawCandidateRegion(new Scalar(0, 255, 128), img_details.src_scaled);
                // get candidate regions to be a barcode

                // rotates candidate region to straighten it based on the angle of the enclosing RotatedRect                
                ROI = cb.NormalizeCandidateRegion(Barcode.USE_ROTATED_RECT_ANGLE);  
                // TODO: remove commented out code below - tester to make sure coordinates can be displayed on source image
                /*
                Point[] rectPoints = ROI.ROI_coords;
                Scalar colour = new Scalar(255, 0,0);
                StringBuffer coords = new StringBuffer("");
                Mat temp = img_details.src_original.clone();
                      for (int j = 0; j < 3; j++){
                           coords.append("(" + rectPoints[j].x + "," + rectPoints[j].y + ")");
                            Core.line(temp, rectPoints[j], rectPoints[j + 1], colour, 5, Core.LINE_AA, 0);
                      }
                       Core.line(temp, rectPoints[3], rectPoints[0], colour, 5, Core.LINE_AA, 0);
                ImageDisplay.showImageFrameGrid(temp, "Original image with marked region " + coords.toString());
                */
                if((statusFlags & TryHarderFlags.POSTPROCESS_RESIZE_BARCODE.value()) != 0)
                    ROI.ROI = scale_candidateBarcode(ROI.ROI);               
                
                ROI.candidate = ImageDisplay.getBufImg(ROI.ROI);
                candidateBarcodes.add(ROI);
 
                if (DEBUG_IMAGES) {
                    cb.debug_drawCandidateRegion(new Scalar(0, 0, 255), img_details.src_scaled);
                }
            }
        }
        if(DEBUG_IMAGES)
            ImageDisplay.showImageFrameGrid(img_details.src_scaled, name + " with candidate regions");

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
        if(DEBUG_IMAGES){
            write_Mat("angles.csv", img_details.gradient_direction);
        }
        // calculate magnitude of gradient, normalize and threshold
        img_details.gradient_magnitude = Mat.zeros(scharr_x.size(), scharr_x.type());
        Core.magnitude(scharr_x, scharr_y, img_details.gradient_magnitude);
        Core.normalize(img_details.gradient_magnitude, img_details.gradient_magnitude, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        if(DEBUG_IMAGES)
            write_Mat("magnitudes.csv", img_details.gradient_magnitude);

        // calculate probabilities for each pixel from window around it, normalize and threshold
  //      probabilities = calcHistogramProbabilities();
        probabilities = calcProbabilityTilings();
        if (DEBUG_IMAGES)
            write_Mat("probabilities_raw.csv", probabilities);
     
        Core.normalize(probabilities, probabilities, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);        
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
        int tileSize = searchParams.RECT_HEIGHT;

        int right_col, bottom_row;
        int DUMMY_ANGLE = 255;
        int BIN_WIDTH = 15;  // bin width for histogram    
        double adj_factor = searchParams.TILE_SIZE/(searchParams.RECT_HEIGHT * 1.0);
        
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

        int num_edges;
        Mat prob_mat = Mat.zeros((int) (rows * adj_factor), (int) (cols * adj_factor), CvType.CV_32F);
        double prob, max_angle_count, second_highest_angle_count, angle_diff;
        int[][] histLocs;
        for(int i = 0; i < rows; i += tileSize){
            // first calculate the row locations of the rectangle and set them to -1 
            // if they are outside the matrix bounds

            bottom_row = ((i + tileSize) > rows) ? rows : (i + tileSize);

            for(int j = 0; j < cols; j += tileSize){

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                
                right_col = ((j + tileSize) > cols) ? cols : (j + tileSize);
                // TODO: do this more efficiently               

                num_edges = Core.countNonZero(img_details.gradient_magnitude.submat(i, bottom_row, j, right_col));                
                
                if (num_edges < searchParams.THRESHOLD_MIN_GRADIENT_EDGES) 
                // if gradient density is below the threshold level, prob of matrix code in this tile is 0
                    continue;
                imgWindow = img_details.gradient_direction.submat(i, bottom_row, j, right_col);
                Imgproc.calcHist(Arrays.asList(imgWindow), mChannels, new Mat(), hist, mHistSize, mRanges, false);
                hist.convertTo(hist, CvType.CV_32S);
                histLocs = getMaxElements(hist);
  
                max_angle_count = histLocs[0][1];
                second_highest_angle_count = histLocs[1][1];                
                angle_diff = Math.abs(histLocs[0][0] - histLocs[1][0]) * BIN_WIDTH;
/*
                Mat weights = img_details.gradient_magnitude.submat(top_row, bottom_row, left_col, right_col);
                HistogramResult histResult = HistogramResult.calcHist(imgWindow, null , 0, 179, BIN_WIDTH);
                max_angle_count = histResult.getMaxBinCount();
                second_highest_angle_count = histResult.getSecondBinCount();
                angle_diff = Math.abs(histResult.max_bin - histResult.second_highest_bin) * BIN_WIDTH;
*/
                
                // formula below is modified from Szentandrasi, Herout, Dubska paper pp. 4
                prob = 1;
               // prob = 1 - (Math.abs(angle_diff - 90) / 90.0);
                prob = prob * 2* Math.min(max_angle_count, second_highest_angle_count) / (max_angle_count + second_highest_angle_count);
                prob = (angle_diff == BIN_WIDTH) ? 0 : prob; // ignores tiles where there is just noise between adjacent bins in the histogram

                int row_offset = (int) (i * adj_factor);
                int col_offset = (int) (j * adj_factor);
                
                for(int r = 0; r < searchParams.TILE_SIZE; r++)
                   for(int c = 0; c < searchParams.TILE_SIZE; c++)
                        prob_mat.put(r + row_offset, c + col_offset, prob);                             
            }  // for j
        }  // for i
        
        return prob_mat;
                
    }
    
    private Mat calcHistogramProbabilities() {
        // calculates probability of each pixel being in a 2D barcode zone based on the 
        // gradient angles around it
        
        int right_col, left_col, top_row, bottom_row;
        int DUMMY_ANGLE = 255;
        int BIN_WIDTH = 15;  // bin width for histogram
        int HIST_INC = 3;
        
        MatOfInt hist = new MatOfInt();
        Mat imgWindow; // used to hold sub-matrices from the image that represent the window around the current point
        int bins = 180 / BIN_WIDTH;

        MatOfInt mHistSize = new MatOfInt(bins);
        MatOfFloat mRanges = new MatOfFloat(0, 179);
        MatOfInt mChannels = new MatOfInt(0);

        // set angle to DUMMY_ANGLE = 255 at all points where gradient magnitude is 0 i.e. where there are no edges
        // these angles will be ignored in the histogram calculation since that counts only up to 180
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
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

            top_row = ((i - height_offset - 1) < 0) ? 0 : (i - height_offset - 1);
            bottom_row = ((i + height_offset) > rows) ? rows : (i + height_offset);

            for (int j = 0; j < cols; j += HIST_INC) {
                // first check if there is a gradient at this pixel
                // no processing needed if so
                if (img_details.gradient_magnitude.get(i, j)[0] == 0)
                    continue;

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                
                left_col = ((j - width_offset - 1) < 0) ? 0 : (j - width_offset - 1);
                right_col = ((j + width_offset) > cols) ? cols : (j + width_offset);
                // TODO: do this more efficiently               

                rect_area = Core.countNonZero(img_details.gradient_magnitude.submat(top_row, bottom_row, left_col, right_col));
                
                
                if (rect_area < searchParams.THRESHOLD_MIN_GRADIENT_EDGES) 
                // if gradient density is below the threshold level, prob of matrix code at this pixel is 0
                    continue;
                imgWindow = img_details.gradient_direction.submat(top_row, bottom_row, left_col, right_col);
                Imgproc.calcHist(Arrays.asList(imgWindow), mChannels, new Mat(), hist, mHistSize, mRanges, false);
                hist.convertTo(hist, CvType.CV_32S);
                histLocs = getMaxElements(hist);
  
                max_angle_count = histLocs[0][1];
                second_highest_angle_count = histLocs[1][1];                
                angle_diff = Math.abs(histLocs[0][0] - histLocs[1][0]) * BIN_WIDTH;
/*
                Mat weights = img_details.gradient_magnitude.submat(top_row, bottom_row, left_col, right_col);
                HistogramResult histResult = HistogramResult.calcHist(imgWindow, null , 0, 179, BIN_WIDTH);
                max_angle_count = histResult.getMaxBinCount();
                second_highest_angle_count = histResult.getSecondBinCount();
                angle_diff = Math.abs(histResult.max_bin - histResult.second_highest_bin) * BIN_WIDTH;
*/
                // formula below is from Szentandrasi, Herout, Dubska paper pp. 4
                prob = 1 - (Math.abs(angle_diff - 90) / 90.0);
                prob = prob * 2* Math.min(max_angle_count, second_highest_angle_count) / (max_angle_count + second_highest_angle_count);

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
