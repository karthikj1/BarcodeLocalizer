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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author karthik
 */
abstract class Barcode {

    public static enum TryHarderFlags {
            TRY_NORMAL(1), TRY_SMALL(2), TRY_LARGE(4), TRY_ALL(255);
        
        private int val;
        
        TryHarderFlags(int val) {
            this.val = val;
        }                
        
        int value(){
            return val;
        }
    };
    // flag to indicate what kind of searches to perform on image to locate barcode
    protected int statusFlags = TryHarderFlags.TRY_NORMAL.value();
    protected static double USE_ROTATED_RECT_ANGLE = 361;

    protected String name;
    
    boolean DEBUG_IMAGES;

    SearchParameters searchParams;
    protected ImageInfo img_details;
    protected int rows, cols;
 
    List<BufferedImage> candidateBarcodes = new ArrayList<>();

    static enum CodeType {LINEAR, MATRIX};        
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    Barcode(String filename) {
        name = filename;
        img_details = new ImageInfo(loadImage(filename));
        
        rows = img_details.src_original.rows();
        cols = img_details.src_original.cols();
        
        searchParams = SearchParameters.getNormalParameters();
        
        DEBUG_IMAGES = false;
      }

    public void setOneFlag(TryHarderFlags flag){
        statusFlags = statusFlags | flag.value();
    }
    
    public void clearFlag(TryHarderFlags flag){
        statusFlags = statusFlags & (~flag.value());
    }
    
    public void resetFlags(){
        statusFlags = TryHarderFlags.TRY_NORMAL.value();
    }
    

    public void setMultipleFlags(TryHarderFlags... flags){
        // clears the flags and sets it to only the ones specified in the argument list
        statusFlags = 0;
        for (TryHarderFlags flag : flags)
            setOneFlag(flag);
    }
    
    // actual locateBarcode algo must be implemented in child class
    protected abstract List<BufferedImage> locateBarcode();
    
    public List<BufferedImage> findBarcode() {
        /*
        finds barcodes using searches according to the flags set in statusFlags
        */

        if ((statusFlags & TryHarderFlags.TRY_NORMAL.value()) != 0) {
            searchParams = SearchParameters.getNormalParameters();
            locateBarcode();
        }
        
        if ((statusFlags & TryHarderFlags.TRY_SMALL.value()) != 0) {
            searchParams = SearchParameters.getSmallParameters();
            locateBarcode();
        }
        
        if ((statusFlags & TryHarderFlags.TRY_LARGE.value()) != 0) {
            searchParams = SearchParameters.getLargeParameters();
            locateBarcode();
        }
        
        return candidateBarcodes;
    }
    
    Mat NewNormalizeCandidateRegion(RotatedRect ROIRotatedRect) {
        /* rect is the RotatedRect which contains a candidate region for the barcode
        // returns Mat containing cropped area(region of interest) with just the barcode 
        // The barcode region is from the *original* image, not the scaled image
        // the cropped area is also rotated as necessary to be horizontal or vertical rather than skewed
        // 
        // Some parts of this function are from http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
        // and http://stackoverflow.com/questions/22041699/rotate-an-image-without-cropping-in-opencv-in-c
        */
        
        Mat rotation_matrix, rotated, cropped;
        // get angle and size from the bounding box
        double orientation = ROIRotatedRect.angle + 90;
        double rotation_angle;
        
        // scale candidate region back up to original size and return cropped part from *original* image 
        // need the 1.0 there to force floating-point arithmetic from int values
        int orig_rows = img_details.src_original.rows();
        int orig_cols = img_details.src_original.cols();
        
        double scale_factor = orig_rows/(1.0 * img_details.src_scaled.rows());        
        
        ROIRotatedRect.center.x = ROIRotatedRect.center.x*scale_factor;
        ROIRotatedRect.center.y = ROIRotatedRect.center.y*scale_factor;
        ROIRotatedRect.size.height *= scale_factor;
        ROIRotatedRect.size.width *= scale_factor;
        
       
        Rect boundingRect = ROIRotatedRect.boundingRect();
        Point centre = new Point(boundingRect.x + boundingRect.width/2, 
                boundingRect.y + boundingRect.height/2 );
        Rect rectifiedRect = getBoundingRect(boundingRect, img_details.src_original);
               
        Mat targetRegion = img_details.src_original.submat(rectifiedRect);
        orig_rows = (int) rectifiedRect.height;
        orig_cols = (int) rectifiedRect.width;
        
        int diagonal = (int) Math.sqrt(orig_rows*orig_rows + orig_cols*orig_cols);

        int newWidth = diagonal;
        int newHeight = diagonal;

        int offsetX = (newWidth - orig_cols) / 2;
        int offsetY = (newHeight - orig_rows) / 2;
        centre.x += offsetX;
        centre.y += offsetY;
        
        rotated = new Mat(newWidth, newHeight, img_details.src_original.type());
        
        targetRegion.copyTo(rotated.rowRange(offsetY, offsetY + orig_rows).colRange(offsetX, offsetX + orig_cols));
        // ImageDisplay.showImageFrame(rotated, "Cropped and centered before rotating");
        ROIRotatedRect.center.x = ROIRotatedRect.center.x - boundingRect.x + offsetX;
        ROIRotatedRect.center.y = ROIRotatedRect.center.y - boundingRect.y + offsetY;
        Size rect_size = new Size(ROIRotatedRect.size.width, ROIRotatedRect.size.height);
        cropped = new Mat();

        // find orientation for barcode
        if (rect_size.width < rect_size.height) {
            orientation += 90;
            double temp = rect_size.width;
            rect_size.width = rect_size.height;
            rect_size.height = temp;
        }

        rotation_angle = orientation - 90;  // rotate 90 degress from its orientation to straighten it out               
        // get the rotation matrix - rotate around rectangle's centre, not image's centre
        
//        rotation_matrix = Imgproc.getRotationMatrix2D(ROIRotatedRect.center, rotation_angle, 1.0);
         rotation_matrix = Imgproc.getRotationMatrix2D(centre, rotation_angle, 1.0);
        // perform the affine transformation
        Imgproc.warpAffine(rotated, rotated, rotation_matrix, rotated.size(), Imgproc.INTER_CUBIC);
        // ImageDisplay.showImageFrame(rotated, "rotated and uncropped " + name);
        // crop the resulting image
        Imgproc.getRectSubPix(rotated, rect_size, ROIRotatedRect.center, cropped);
        // ImageDisplay.showImageFrame(cropped, "Cropped and deskewed " + name);
        return cropped;
    }

    private Rect getBoundingRect(Rect boundingRect, Mat parent){
    /* rotated rectangle from openCV function minAreaRect can have negative coordinates
    // which bleeds through to negative coordinates when using openCV function boundingRect
    // this then creates an error if we try to use those coordinates to extract a sub-matrix
    // this function returns a bounding rectangle for the given rotated rectangle that has 
    // valid coordinates relative to the provided source image
    */
        // ensure that the top-left is within bounds
        int x = Math.max(0, boundingRect.x);
        int y = Math.max(0, boundingRect.y);
        
        //ensure that the points in the boundingRect are valid coordinates in the original source image 
        int width = Math.min(parent.cols() - x - 1, boundingRect.width);
        int height = Math.min(parent.rows() - y - 1, boundingRect.height);
        
        return new Rect(x, y, width, height);
    }
       
    Mat NormalizeCandidateRegion(RotatedRect rect, double angle) {
        /* rect is the RotatedRect which contains a candidate region for the barcode
        // angle is the rotation angle or USE_ROTATED_RECT_ANGLE for this function to 
        // estimate rotation angle from the rect parameter
        // returns Mat containing cropped area(region of interest) with just the barcode 
        // The barcode region is from the *original* image, not the scaled image
        // the cropped area is also rotated as necessary to be horizontal or vertical rather than skewed
        // matrices we'll use
        // Some parts of this function are from http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
        // and http://stackoverflow.com/questions/22041699/rotate-an-image-without-cropping-in-opencv-in-c
        */
        
        Mat rotation_matrix, rotated, cropped;
        double rotation_angle;
        
        int orig_rows = img_details.src_original.rows();
        int orig_cols = img_details.src_original.cols();
        System.out.println("orig centre is " + rect.center.x + ", " + rect.center.y);
        int diagonal = (int) Math.sqrt(orig_rows*orig_rows + orig_cols*orig_cols);

        int newWidth = diagonal;
        int newHeight = diagonal;

        int offsetX = (newWidth - orig_cols) / 2;
        int offsetY = (newHeight - orig_rows) / 2;
        rotated = new Mat(newWidth, newHeight, img_details.src_original.type());
        
        img_details.src_original.copyTo(rotated.rowRange(offsetY, offsetY + orig_rows).colRange(offsetX, offsetX + orig_cols));
        // scale candidate region back up to original size and return cropped part from *original* image 
        // need the 1.0 there to force floating-point arithmetic from int values
        double scale_factor = orig_rows/(1.0 * img_details.src_scaled.rows());        
        
        rect.center.x = rect.center.x*scale_factor + offsetX;
        rect.center.y = rect.center.y*scale_factor + offsetY;
        rect.size.height *= scale_factor;
        rect.size.width *= scale_factor;

       // System.out.println("scaled centre is " + rect.center.x + ", " + rect.center.y);
        Size rect_size = new Size(rect.size.width, rect.size.height);
        cropped = new Mat();
        if (rect_size.width < rect_size.height) {            
            double temp = rect_size.width;
            rect_size.width = rect_size.height;
            rect_size.height = temp;
        }
        
        rotation_angle = estimate_barcode_orientation(rect);
        System.out.println("Calculated angle " + rotation_angle + " provided " + angle);
         if(angle != Barcode.USE_ROTATED_RECT_ANGLE)
            rotation_angle = angle;
    //    ImageDisplay.showImageFrame(rotated, "Image in larger frame " + name);
        // get the rotation matrix - rotate around image's centre
        Point centre = new Point(rotated.rows()/2.0, rotated.cols()/2.0);
        rotation_matrix = Imgproc.getRotationMatrix2D(centre, rotation_angle, 1.0);
        // perform the affine transformation
        Imgproc.warpAffine(rotated, rotated, rotation_matrix, rotated.size(), Imgproc.INTER_CUBIC);
        ImageDisplay.showImageFrame(rotated, "rotated and uncropped " + name);
        // get the new location for the rectangle's centre
        System.out.println("Rotation matrix is: \n" + rotation_matrix.dump());
        double new_x = rotation_matrix.get(0, 2)[0] + rotation_matrix.get(0, 0)[0] * rect.center.x + rotation_matrix.get(0, 1)[0] * rect.center.y;
        double new_y = rotation_matrix.get(1, 2)[0] + rotation_matrix.get(1, 0)[0] * rect.center.x + rotation_matrix.get(1, 1)[0] * rect.center.y;        
        Point new_rect_centre = new Point(new_x, new_y);        
        System.out.println(new_x + " " + new_y);
        // crop the resulting image
        Imgproc.getRectSubPix(rotated, rect_size, new_rect_centre, cropped);
   //     ImageDisplay.showImageFrame(cropped, "Cropped and deskewed " + name);
        return cropped;
    }
    
    private double estimate_barcode_orientation(RotatedRect rect){
        // uses angle of orientation of enclosing rotated rectangle to rotate barcode
        // and make it horizontal - only relevant for linear barcodes currently
              
        // get angle and size from the bounding box
        double orientation = rect.angle + 90;
        double rotation_angle;
        Size rect_size = new Size(rect.size.width, rect.size.height);

        // find orientation for barcode
        if (rect_size.width < rect_size.height) 
            orientation += 90;

        rotation_angle = orientation - 90;  // rotate 90 degrees from its orientation to straighten it out    
        return rotation_angle;
    }

        protected void preprocess_image() {
       // pre-process image to convert to grayscale and do morph black hat
        // also resizes image if it is above a specified size and sets the search parameters
        // based on image size
        
        scaleImage();
        searchParams.setImageSpecificParameters(rows, cols);
        
        // do pre-processing to increase contrast
        img_details.src_grayscale = new Mat(rows, cols, CvType.CV_32F);
        Imgproc.cvtColor(img_details.src_scaled, img_details.src_grayscale, Imgproc.COLOR_RGB2GRAY);
        Imgproc.morphologyEx(img_details.src_grayscale, img_details.src_grayscale, Imgproc.MORPH_BLACKHAT,
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, searchParams.elem_size));
 
        if (DEBUG_IMAGES) {
            write_Mat("greyscale.csv", img_details.src_grayscale);
            ImageDisplay.showImageFrame(img_details.src_grayscale, "Pre-processed image");
        }
    }

    protected void connectComponents() {
        // connect large components by doing morph close followed by morph open
        // use larger element size for erosion to remove small elements joined by dilation
        Mat small_elemSE, large_elemSE;
        
        small_elemSE =  Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, searchParams.elem_size);
        large_elemSE = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, searchParams.large_elem_size);
            
        Imgproc.dilate(img_details.E3, img_details.E3, small_elemSE);
        Imgproc.erode(img_details.E3, img_details.E3, large_elemSE);
        
        Imgproc.erode(img_details.E3, img_details.E3, small_elemSE);
        Imgproc.dilate(img_details.E3, img_details.E3, large_elemSE);
    }
    
        protected void scaleImage() {
        // shrink image if it is above a certain size   
        // it also reduces image size for large images which helps with processing speed
        // and reducing sensitivity to barcode size within the image
        
        if(rows > searchParams.MAX_ROWS){
            cols = (int) (cols * (searchParams.MAX_ROWS * 1.0/rows));
            rows = searchParams.MAX_ROWS;
            img_details.src_scaled = new Mat(rows, cols, CvType.CV_32F);
            Imgproc.resize(img_details.src_original, img_details.src_scaled, img_details.src_scaled.size(), 0, 0, Imgproc.INTER_AREA);
            
        }
    }

    protected double calc_rect_sum(Mat integral, int right_col, int left_col, int top_row, int bottom_row) {
        double top_left, top_right, bottom_left, bottom_right;
        double sum;

        bottom_right = integral.get(bottom_row, right_col)[0];
        top_right = (top_row == -1) ? 0 : integral.get(top_row, right_col)[0];
        top_left = (left_col == -1 || top_row == -1) ? 0 : integral.get(top_row, left_col)[0];
        bottom_left = (left_col == -1) ? 0 : integral.get(bottom_row, left_col)[0];

        sum = (bottom_right - bottom_left - top_right + top_left);
        return sum;

    }

    protected Mat abs(Mat input) {
        // calc abs value of a matrix by calc'ing absdiff with a zero matrix
        Mat output = Mat.zeros(input.size(), input.type());

        Core.absdiff(input, output, output);
        return output;
    }

    protected static void write_Mat(String filename, Mat img) {
        try {
            PrintStream original = new PrintStream(System.out);
            PrintStream printStream = new PrintStream(new FileOutputStream(
                new File(
                    filename)));
            System.setOut(printStream);
            System.out.println(img.dump());
            System.setOut(original);
        } catch (IOException ioe) {
        }

    }

    protected Mat loadImage(String filename) {

        return Highgui.imread(filename, Highgui.CV_LOAD_IMAGE_COLOR);
    }

       private Mat calcLines(Mat cropped_ROI, double threshold) {
        /*
        calculate number of parallel lines in the image region cropped_ROI
        */
        Mat mLines = new Mat();
        Mat ROI_grayscale = new Mat();
        Imgproc.cvtColor(cropped_ROI, ROI_grayscale, Imgproc.COLOR_RGB2GRAY);
        Imgproc.HoughLines(ROI_grayscale, mLines, 10, Math.PI/2, 5);
        System.out.println("Number of lines is " + mLines.cols());
        Scalar color = new Scalar(0, 0, 255);

        double[] data;
        double rho, theta;
        Point pt1 = new Point();
        Point pt2 = new Point();
        double a, b;
        double x0, y0;
        for (int i = 0; i < mLines.cols(); i++) {
            data = mLines.get(0, i);
            rho = data[0];
            theta = data[1];
            a = Math.cos(theta);
            b = Math.sin(theta);
            x0 = a * rho;
            y0 = b * rho;
            pt1.x = Math.round(x0 + 1000 * (-b));
            pt1.y = Math.round(y0 + 1000 * a);
            pt2.x = Math.round(x0 - 1000 * (-b));
            pt2.y = Math.round(y0 - 1000 * a);
            Core.line(cropped_ROI, pt1, pt2, color, 1);
        }
        return cropped_ROI;
    }      
}
