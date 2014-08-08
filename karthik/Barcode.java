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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

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
    
    Mat _2DNormalizeCandidateRegion(RotatedRect rect, double angle) {
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
      //  System.out.println("orig centre is " + rect.center.x + ", " + rect.center.y);
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
      //  System.out.println("Calculated angle " + rotation_angle + " provided " + angle);
         if(angle != Barcode.USE_ROTATED_RECT_ANGLE)
            rotation_angle = angle;
    //    ImageDisplay.showImageFrame(rotated, "Image in larger frame " + name);
        // get the rotation matrix - rotate around image's centre
        Point centre = new Point(rotated.rows()/2.0, rotated.cols()/2.0);
        rotation_matrix = Imgproc.getRotationMatrix2D(centre, rotation_angle, 1.0);
        // perform the affine transformation
        Imgproc.warpAffine(rotated, rotated, rotation_matrix, rotated.size(), Imgproc.INTER_CUBIC);
       // ImageDisplay.showImageFrame(rotated, "rotated and uncropped " + name);
        // get the new location for the rectangle's centre
        //System.out.println("Rotation matrix is: \n" + rotation_matrix.dump());
        double new_x = rotation_matrix.get(0, 2)[0] + rotation_matrix.get(0, 0)[0] * rect.center.x + rotation_matrix.get(0, 1)[0] * rect.center.y;
        double new_y = rotation_matrix.get(1, 2)[0] + rotation_matrix.get(1, 0)[0] * rect.center.x + rotation_matrix.get(1, 1)[0] * rect.center.y;        
        Point new_rect_centre = new Point(new_x, new_y);        
       // System.out.println(new_x + " " + new_y);
        // crop the resulting image
        Imgproc.getRectSubPix(rotated, rect_size, new_rect_centre, cropped);
   //     ImageDisplay.showImageFrame(cropped, "Cropped and deskewed " + name);
        return cropped;
    }
    
    Mat NormalizeCandidateRegionWithPerspective(RotatedRect rect, double angle){
        Mat rotation_matrix, enlarged;
        double rotation_angle;
        
        int orig_rows = img_details.src_original.rows();
        int orig_cols = img_details.src_original.cols();
        int diagonal = (int) Math.sqrt(orig_rows*orig_rows + orig_cols*orig_cols);

        int newWidth = diagonal;
        int newHeight = diagonal;

        int offsetX = (newWidth - orig_cols) / 2;
        int offsetY = (newHeight - orig_rows) / 2;
        enlarged = new Mat(newWidth, newHeight, img_details.src_original.type());
        
        img_details.src_original.copyTo(enlarged.rowRange(offsetY, offsetY + orig_rows).colRange(offsetX, offsetX + orig_cols));
        // scale candidate region back up to original size to return cropped part from *original* image 
        // need the 1.0 there to force floating-point arithmetic from int values
        double scale_factor = orig_rows/(1.0 * img_details.src_scaled.rows());        
        
        // calculate location of rectangle in original image and its corner points
        rect.center.x = rect.center.x*scale_factor + offsetX;
        rect.center.y = rect.center.y*scale_factor + offsetY;
        rect.size.height *= scale_factor;
        rect.size.width *= scale_factor;
        Point[] scaledCorners = new Point[4];
        rect.points(scaledCorners);       
        
        rotation_angle = estimate_barcode_orientation(rect);
  //      System.out.println("CropRectangle: Calculated angle " + rotation_angle + " provided " + angle);
         if(angle != Barcode.USE_ROTATED_RECT_ANGLE)
            rotation_angle = angle;
        
        Point centre = new Point(enlarged.rows()/2.0, enlarged.cols()/2.0);
        rotation_matrix = Imgproc.getRotationMatrix2D(centre, rotation_angle, 1.0);
    //    System.out.println("CropRectangle: Rotation matrix is: \n" + rotation_matrix.dump());
        // perform the affine transformation
        rotation_matrix.convertTo(rotation_matrix, CvType.CV_32F); // convert type so matrix multip. works properly
        List<Point> newCornerPoints = new ArrayList<>();
        Mat newCornerCoord = Mat.zeros(2, 1, CvType.CV_32F);
        Mat coord = Mat.ones(3,1, CvType.CV_32F);
       // calculate the new location for each corner point of the rectangle ROI
        for(Point p: scaledCorners){
            coord.put(0, 0, p.x);
            coord.put(1, 0, p.y);
            Core.gemm(rotation_matrix, coord, 1, Mat.zeros(3, 3, CvType.CV_32F), 0, newCornerCoord);                         
            newCornerPoints.add(new Point(newCornerCoord.get(0, 0)[0], newCornerCoord.get(1,0)[0]));
        }
        Mat rotated = Mat.zeros(enlarged.size(), enlarged.type());
        Imgproc.warpAffine(enlarged, rotated, rotation_matrix, enlarged.size(), Imgproc.INTER_CUBIC);
        // draw circles at calculated location of corner points after rotation
        
        Point rectPoints[] = newCornerPoints.toArray(new Point[4]);
        
        // sort rectangles points in order by first sorting all 4 points based on x
        // we then sort the first two based on y and then the next two based on y
        // this leaves the array in order top-left, bottom-left, top-right, bottom-right
        Arrays.sort(rectPoints, new compare_x());
        if(rectPoints[0].y > rectPoints[1].y){
            Point temp = rectPoints[1];
            rectPoints[1] = rectPoints[0];
            rectPoints[0] = temp;
        }
        
        if(rectPoints[2].y > rectPoints[3].y){
            Point temp = rectPoints[2];
            rectPoints[2] = rectPoints[3];
            rectPoints[3] = temp;
        }
        
        newCornerPoints = Arrays.asList(rectPoints);
  //      ImageDisplay.showImageFrame(rotated, "CropRectangle: rotated and uncropped " + name);
            // calc height and width of rectangular region
        double height, width;
        height = Math.sqrt(Math.pow(rectPoints[1].x - rectPoints[0].x, 2) + Math.pow(rectPoints[1].y - rectPoints[0].y, 2));
        width = Math.sqrt(Math.pow(rectPoints[2].x - rectPoints[0].x, 2) + Math.pow(rectPoints[2].y - rectPoints[0].y, 2));
        // create destination points for warpPerspective to map to
        List<Point> transformedPoints = new ArrayList<>();
        transformedPoints.add(new Point(0,0));
        transformedPoints.add(new Point(0,height));
        transformedPoints.add(new Point(width,0));
        transformedPoints.add(new Point(width,height));
        
        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(Converters.vector_Point2f_to_Mat(newCornerPoints),
            Converters.vector_Point2f_to_Mat(transformedPoints));
        Mat perspectiveOut = Mat.zeros((int)height + 2, (int) width + 2, CvType.CV_32F);
        Imgproc.warpPerspective(rotated, perspectiveOut, perspectiveTransform, perspectiveOut.size(), Imgproc.INTER_CUBIC);
//        ImageDisplay.showImageFrame(perspectiveOut, "CropRectangle: perspective warped " + name);
        
        return perspectiveOut;
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

    private class compare_x implements Comparator<Point>{
        // Comparator class to compare x coordinate of Point objects
        public int compare(Point a, Point b){
            if(a.x == b.x)
                return 0;
            if(a.x > b.x)
                return 1;
            return -1;
        }
    }
}
