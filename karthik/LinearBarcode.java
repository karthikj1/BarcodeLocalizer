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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author karthik
 */
public class LinearBarcode extends Barcode{

    /**
     * @param args the command line arguments
     */
    private double THRESHOLD_T2; // min number of gradient edges in rectangular window to consider as non-zero
    private double THRESHOLD_MIN_AREA; // min area for candidate region to be considered as a barcode
    
    private static final double THRESHOLD_T3 = 0.6;  // threshold for ratio of contour area to bounding rectangle area
    
    // threshold below which normalized variance is considered low enough for angles in that area to be mostly unidirectional
    private static final double THRESHOLD_VARIANCE = 75;  
    private Mat integral_gradient_directions;
    private Size elem_size, large_elem_size;
    

    public LinearBarcode(String filename) {
        super(filename);
        searchType = CodeType.LINEAR;
        elem_size = new Size(10,10);
        large_elem_size = new Size(12,12);
    }
    
    public LinearBarcode(String filename, boolean debug) {
        this(filename);
        DEBUG_IMAGES = debug;
    }
    
    public List<BufferedImage> findBarcode() {

        System.out.println("Searching " + name + " for " + searchType.name());
        preprocess_image();

        findCandidates();   // find areas with low variance in gradient direction
        connectComponents();
        
       if(DEBUG_IMAGES)
            ImageDisplay.showImageFrame(img_details.E3, "Image img_details.E3 after morph close and open");

        List<MatOfPoint> contours = new ArrayList<>();
                // findContours modifies source image so we pass it a copy of img_details.E3
        // img_details.E3 will be used again shortly to expand the barcode region
        Imgproc.findContours(img_details.E3.clone(), contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double bounding_rect_area = 0;
        RotatedRect minRect;
        Mat ROI;
        for (int i = 0; i < contours.size(); i++) {
            double area = Imgproc.contourArea(contours.get(i));
            minRect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            bounding_rect_area = minRect.size.width * minRect.size.height;

            if (area < THRESHOLD_MIN_AREA) // ignore contour if it is of too small a region
                continue;
                
            if ((area / bounding_rect_area) > THRESHOLD_T3) // check if contour is of a rectangular object
            {
                CandidateBarcode cb = new CandidateBarcode(img_details, minRect);
                // get candidate regions to be a barcode
               //  cb.drawCandidateRegion(minRect, new Scalar(0, 255, 0));
                minRect = cb.getCandidateRegion();
                ROI = NormalizeCandidateRegion(minRect);
                ROI = postprocess_image(ROI);
               try{
                candidateBarcodes.add(ImageDisplay.getBufImg(ROI));
                }
                catch(IOException ioe){
                    System.out.println("Error when creating image " + ioe.getMessage());
                    return null;
                }
                cb.drawCandidateRegion(minRect, new Scalar(0, 0, 255));
                if(DEBUG_IMAGES)
                    ImageDisplay.showImageFrame(ROI, "Cropped image of " + name);
             }
        }
        if(DEBUG_IMAGES)
            ImageDisplay.showImageFrame(img_details.src_scaled, name + " with candidate regions");
        return candidateBarcodes;
    }

    private void connectComponents() {
        // connect large components by doing morph close followed by morph open
        // use larger element size for erosion to remove small elements joined by dilation
        Mat small_elemSE, large_elemSE;
        
        small_elemSE =  Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elem_size);
        large_elemSE = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, large_elem_size);
            
        Imgproc.dilate(img_details.E3, img_details.E3, small_elemSE);
        Imgproc.erode(img_details.E3, img_details.E3, large_elemSE);
        
        Imgproc.erode(img_details.E3, img_details.E3, small_elemSE);
        Imgproc.dilate(img_details.E3, img_details.E3, large_elemSE);
    }

    private Mat postprocess_image(Mat ROI) {
        // filters and sharpens candidate barcode region to make it easier to decode
        Imgproc.cvtColor(ROI, ROI, Imgproc.COLOR_RGB2GRAY);
    Core.normalize(ROI, ROI, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
      //  Imgproc.adaptiveThreshold(ROI, ROI, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 0);
        Imgproc.threshold(ROI, ROI, 50, 255, Imgproc.THRESH_TOZERO);
// resize Region of Interest to 100 rows to make small barcodes more readable
/*        int NUM_ROWS = 100;
        int ROI_rows = ROI.rows();
        int ROI_cols = ROI.cols();
        
            ROI_cols = (int) (ROI_cols * (NUM_ROWS * 1.0/ROI_rows));
            ROI_rows = NUM_ROWS;
        
            Mat temp = new Mat(ROI_rows, ROI_cols, CvType.CV_32F);
        Imgproc.resize(ROI, temp, temp.size(), 0, 0, Imgproc.INTER_AREA);       
        ROI = temp;
        
              Mat kernel = Mat.zeros(3, 3, CvType.CV_32F);
        kernel.put(1, 1, 5);
        kernel.put(0, 1, -1);
        kernel.put(2, 1, -1);
        kernel.put(1, 0, -1);
        kernel.put(1, 2, -1);

        Imgproc.filter2D(ROI, ROI, -1, kernel);
    Core.normalize(ROI, ROI, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
  */            
        return ROI;
    }

    private void preprocess_image() {
        int MAX_ROWS = 300;
        if(rows > MAX_ROWS){
            cols = (int) (cols * (MAX_ROWS * 1.0/rows));
            rows = MAX_ROWS;
        img_details.src_scaled = new Mat(rows, cols, CvType.CV_32F);
        Imgproc.resize(img_details.src_original, img_details.src_scaled, img_details.src_scaled.size(), 0, 0, Imgproc.INTER_AREA);                
        }
        THRESHOLD_MIN_AREA = 0.02 * cols * rows;
        RECT_HEIGHT = (int) (0.1 * rows);
        RECT_WIDTH = (int) (0.1 * cols);
        THRESHOLD_T2 = RECT_HEIGHT * RECT_WIDTH * 0.3;
                
        img_details.src_grayscale = new Mat(rows, cols, CvType.CV_32F);
        
        // do pre-processing to increase contrast
        Imgproc.cvtColor(img_details.src_scaled, img_details.src_grayscale, Imgproc.COLOR_RGB2GRAY);
        Imgproc.morphologyEx(img_details.src_grayscale, img_details.src_grayscale, Imgproc.MORPH_BLACKHAT,
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elem_size));
        
        write_Mat("greyscale.csv", img_details.src_grayscale);
        if(DEBUG_IMAGES){
            ImageDisplay.showImageFrame(img_details.src_grayscale, "Pre-processed image");
        }
    }

    
    
    private void findCandidates() {        
    // find candidate regions that could contain barcodes
        
        img_details.gradient_direction = Mat.zeros(rows, cols, CvType.CV_32F);
        
        Mat scharr_x, scharr_y, variance;
        
        scharr_x = new Mat(rows, cols, CvType.CV_32F);
        scharr_y = new Mat(rows, cols, CvType.CV_32F);
        
        Imgproc.Scharr(img_details.src_grayscale, scharr_x, CvType.CV_32F, 1, 0);
        Imgproc.Scharr(img_details.src_grayscale, scharr_y, CvType.CV_32F, 0, 1);
        
        // calc angle using Core.phase function - should be quicker than using atan2 manually
        Core.phase(scharr_x, scharr_y, img_details.gradient_direction, true);     
        // TODO: implement below using array
   /*     float[] direction_array = new float[1];
        img_details.gradient_direction.get(0,0, direction_array);
     
        for (int i = 0; i < direction_array.length; i++) {
            direction_array[i] = direction_array[i] % 180;
            direction_array[i] = (direction_array[i] > 170) ? 0 : direction_array[i];
        }
        
        img_details.gradient_direction.put(0,0, direction_array);
        
     */ double angle;
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                angle = img_details.gradient_direction.get(i, j)[0];
                angle = angle % 180;
                angle = (angle > 170) ? 0 : angle;
                img_details.gradient_direction.put(i, j, angle);
            }

        // convert type after modifying angle so that angles above 360 don't get truncated
        img_details.gradient_direction.convertTo(img_details.gradient_direction, CvType.CV_8U); 
        write_Mat("angles.csv", img_details.gradient_direction);
        
        // calculate magnitude of gradient
        img_details.gradient_magnitude = Mat.zeros(scharr_x.size(), scharr_x.type());
        Core.magnitude(scharr_x, scharr_y, img_details.gradient_magnitude);
        write_Mat("magnitudes_raw.csv", img_details.gradient_magnitude);
        Core.normalize(img_details.gradient_magnitude, img_details.gradient_magnitude, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        write_Mat("magnitudes_normalized.csv", img_details.gradient_magnitude);
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        write_Mat("magnitudes.csv", img_details.gradient_magnitude);
       
        variance = calc_variance();
        write_Mat("variance_raw.csv", variance);  
        Core.normalize(variance, variance, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        write_Mat("variance_normalized.csv", variance);  
            Imgproc.threshold(variance, variance, THRESHOLD_VARIANCE, 255, Imgproc.THRESH_BINARY_INV);
            
        if(DEBUG_IMAGES){
           ImageDisplay.showImageFrame(img_details.gradient_magnitude, "Magnitudes");
           ImageDisplay.showImageFrame(variance, "Variance");
        }
        
        write_Mat("variance.csv", variance);       
        img_details.E3 = variance;
    }

    private Mat calc_variance() {
        /* calculate variance of gradient directions around each pixel
         in the img_details.gradient_directions matrix
         */
        int right_col, left_col, top_row, bottom_row;
        double sum, sumsq, data;

        int DUMMY_VARIANCE = -1;
        integral_gradient_directions = new Mat(rows, cols, CvType.CV_32F);
        Mat integral_sumsq = new Mat(rows, cols, CvType.CV_32F);
        Mat variance = new Mat(rows, cols, CvType.CV_32F);

        int width_offset = RECT_WIDTH / 2;
        int height_offset = RECT_HEIGHT / 2;
        int rect_area;
        
        // set angle to 0 at all points where gradient magnitude is 0 i.e. where there are no edges
        Core.bitwise_and(img_details.gradient_direction, img_details.gradient_magnitude, img_details.gradient_direction);
        write_Mat("angles_modified.csv", img_details.gradient_direction);        
        
        Imgproc.integral2(img_details.gradient_direction, integral_gradient_directions, integral_sumsq, CvType.CV_32F);

        for (int i = 0; i < rows; i++) {
            // first calculate the row locations of the rectangle and set them to -1 
            // if they are outside the matrix bounds

            top_row = ((i - height_offset - 1) < 0) ? -1 : (i - height_offset - 1);
            bottom_row = ((i + height_offset) > rows) ? rows : (i + height_offset);

            for (int j = 0; j < cols; j++) {
                // first check if there is a gradient at this pixel
                // no processing needed if so
                if(img_details.gradient_magnitude.get(i,j)[0] == 0){
                    variance.put(i, j, DUMMY_VARIANCE);
                    continue;
                }

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                

                left_col = ((j - width_offset - 1) < 0) ? -1 : (j - width_offset - 1);
                right_col = ((j + width_offset) > cols) ? cols : (j + width_offset);
                // TODO: do this more efficiently
                rect_area = Core.countNonZero(img_details.gradient_magnitude.submat(Math.max(top_row, 0), bottom_row, Math.max(left_col, 0), right_col));                
                if(rect_area <  THRESHOLD_T2) { 
                    variance.put(i, j, DUMMY_VARIANCE);
                    continue;
                }

                // get the values of the rectangle corners from the integral image - 0 if outside bounds
                sum = calc_rect_sum(integral_gradient_directions, right_col, left_col, top_row, bottom_row);
                sumsq = calc_rect_sum(integral_sumsq, right_col, left_col, top_row, bottom_row);
                
                // calculate variance based only on points in the rectangular window which are edges
                // edges are defined as points with high gradient magnitude
                
                data = (sumsq/rect_area) - (Math.pow(sum/rect_area, 2));                
                variance.put(i, j, data);
            }  // for j
        }  // for i    
        
        integral_gradient_directions = integral_sumsq = null;
        // TODO: find a more efficient way to do this
        // replaces every instance of -1 with the max variance
        // this prevents a situation where areas with no edges show up as low variance bec their angles are 0
        // if the value in these cells are set to double.maxval, all the real variances get normalized to 0
        double maxVal = Core.minMaxLoc(variance).maxVal;
        Mat mask = Mat.zeros(variance.size(), CvType.CV_8U);
        Core.inRange(variance, new Scalar(DUMMY_VARIANCE), new Scalar(DUMMY_VARIANCE), mask);
        variance.setTo(new Scalar(maxVal), mask);
          
        return variance;
    }

}
