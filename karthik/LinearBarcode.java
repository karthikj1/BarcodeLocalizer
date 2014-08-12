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

    // threshold below which normalized variance is considered low enough for angles in that area to be mostly unidirectional
    private static final double THRESHOLD_VARIANCE = 75;  
    private Mat integral_gradient_directions;
    

    public LinearBarcode(String filename) {
        super(filename);
        img_details.searchType = CodeType.LINEAR;
    }
    
    public LinearBarcode(String filename, boolean debug) {
        this(filename);
        DEBUG_IMAGES = debug;
    }
    
    protected List<BufferedImage> locateBarcode() throws IOException{

        System.out.println("Searching " + name + " for " + img_details.searchType.name());
        preprocess_image();

        findCandidates();   // find areas with low variance in gradient direction
        connectComponents();
        
       if(DEBUG_IMAGES){
            write_Mat("E3.csv", img_details.E3);
            ImageDisplay.showImageFrame(img_details.E3, "Image E3 after morph close and open");
       }
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

            if (area < searchParams.THRESHOLD_MIN_AREA) // ignore contour if it is of too small a region
                continue;
                
            if ((area / bounding_rect_area) > searchParams.THRESHOLD_AREA_RATIO) // check if contour is of a rectangular object
            {
                CandidateBarcode cb = new CandidateBarcode(img_details, minRect, searchParams);
                double barcode_orientation = getBarcodeOrientation(contours, i);                
                // get candidate regions to be a barcode
                if(DEBUG_IMAGES)
                     cb.debug_drawCandidateRegion(minRect, new Scalar(0, 255, 0), img_details.src_scaled);
                minRect = cb.getCandidateRegion();
               if(DEBUG_IMAGES)
                    cb.debug_drawCandidateRegion(minRect, new Scalar(0, 0, 255), img_details.src_scaled);                                
                ROI = cb.NormalizeCandidateRegion(barcode_orientation);               
               
           //     if((statusFlags & TryHarderFlags.POSTPROCESS.value()) != 0)
           //         ROI = postprocess_image(ROI);    
                
                if((statusFlags & TryHarderFlags.RESIZE_BEFORE_DECODE.value()) != 0)
                    ROI = scale_candidateBarcode(ROI);               
                candidateBarcodes.add(ImageDisplay.getBufImg(ROI));
             }
        }
        if(DEBUG_IMAGES)
            ImageDisplay.showImageFrame(img_details.src_scaled, name + " with candidate regions");
        return candidateBarcodes;
    }

    private double getBarcodeOrientation(List<MatOfPoint> contours, int i) {
        // get mean angle within contour region so we can rotate by that amount
        
        Mat mask = Mat.zeros(img_details.src_scaled.size(), CvType.CV_8U);
        Mat temp_directions = Mat.zeros(img_details.src_scaled.size(), CvType.CV_8U);
        Mat temp_magnitudes = Mat.zeros(img_details.src_scaled.size(), CvType.CV_8U);
        
        Imgproc.drawContours(mask, contours, i, new Scalar(255), -1); // -1 thickness to fill contour
        Core.bitwise_and(img_details.gradient_direction, mask, temp_directions);
        Core.bitwise_and(img_details.gradient_magnitude, mask, temp_magnitudes);
        // gradient_direction now contains non-zero values only where there is a gradient
        // mask now contains angles only for pixels within region enclosed by contour

        double barcode_orientation = Core.sumElems(temp_directions).val[0];
        barcode_orientation = barcode_orientation/Core.countNonZero(temp_magnitudes);
        
        return barcode_orientation;
    }
    

    private Mat postprocess_image(Mat ROI) {
        // filters and sharpens candidate barcode region to make it easier to decode
        Imgproc.cvtColor(ROI, ROI, Imgproc.COLOR_RGB2GRAY);
        ROI.convertTo(ROI, CvType.CV_8U);
        Imgproc.threshold(ROI, ROI, 50, 255, Imgproc.THRESH_TOZERO);
        return ROI;
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
        double angle;
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
       
        // calculate variances, normalize and threshold so that low-variance areas are bright(255) and 
        // high-variance areas are dark(0)
        variance = calc_variance();
        Core.normalize(variance, variance, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        Imgproc.threshold(variance, variance, THRESHOLD_VARIANCE, 255, Imgproc.THRESH_BINARY_INV);
            
        if(DEBUG_IMAGES){
           ImageDisplay.showImageFrame(img_details.gradient_magnitude, "Magnitudes");
           ImageDisplay.showImageFrame(variance, "Variance");
           write_Mat("variance.csv", variance);       
        }
        
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

        int width_offset = searchParams.RECT_WIDTH / 2;
        int height_offset = searchParams.RECT_HEIGHT / 2;
        int rect_area;
        
        // set angle to 0 at all points where gradient magnitude is 0 i.e. where there are no edges
        Core.bitwise_and(img_details.gradient_direction, img_details.gradient_magnitude, img_details.gradient_direction);
        if(DEBUG_IMAGES)
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
                if(rect_area <  searchParams.THRESHOLD_MIN_GRADIENT_EDGES) { 
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
