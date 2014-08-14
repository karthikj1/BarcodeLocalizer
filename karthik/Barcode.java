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
public abstract class Barcode {

    public static enum TryHarderFlags {
            NORMAL(1), SMALL(2), LARGE(4), ALL_SIZES(7), ALL(255);
        
        private int val;
        
        TryHarderFlags(int val) {
            this.val = val;
        }                
        
        int value(){
            return val;
        }
    };
    // flag to indicate what kind of searches to perform on image to locate barcode
    protected int statusFlags = TryHarderFlags.NORMAL.value();
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
        statusFlags = TryHarderFlags.NORMAL.value();
    }
    

    public void setMultipleFlags(TryHarderFlags... flags){
        // clears the flags and sets it to only the ones specified in the argument list
        statusFlags = 0;
        
        for (TryHarderFlags flag : flags)
            setOneFlag(flag);
        // at least one of the size flags must be set so it chooses NORMAL as the default if nothing is set
        if((statusFlags & TryHarderFlags.ALL_SIZES.value()) == 0)
            setOneFlag(TryHarderFlags.NORMAL);
    }
    
    // actual locateBarcode algo must be implemented in child class
    protected abstract List<BufferedImage> locateBarcode() throws IOException;
    
    public List<BufferedImage> findBarcode() throws IOException{
        /*
        finds barcodes using searches according to the flags set in statusFlags
        */

        if ((statusFlags & TryHarderFlags.NORMAL.value()) != 0) {
            searchParams = SearchParameters.getNormalParameters();
            locateBarcode();
        }
        
        if ((statusFlags & TryHarderFlags.SMALL.value()) != 0) {
            searchParams = SearchParameters.getSmallParameters();
            locateBarcode();
        }
        
        if ((statusFlags & TryHarderFlags.LARGE.value()) != 0) {
            searchParams = SearchParameters.getLargeParameters();
            locateBarcode();
        }

        return candidateBarcodes;
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
        
    protected Mat scale_candidateBarcode(Mat candidate){
        // resizes candidate image to have at least MIN_COLS columns and MIN_ROWS rows
        // called when RESIZE_BEFORE_DECODE is set - seems to help ZXing decode barcode
        // TODO: combine this into one function with scaleImage
        int MIN_COLS = 200;
        int MIN_ROWS = 200;
        
        int num_rows = candidate.rows();
        int num_cols = candidate.cols();                        
        
        if((num_cols > MIN_COLS) && (num_rows > MIN_ROWS))
            return candidate;
        
        if(num_cols < MIN_COLS){
            num_rows = (int) (num_rows * MIN_COLS/(1.0 * num_cols));
            num_cols= MIN_COLS;
        }
        
        if(num_rows < MIN_ROWS){
            num_cols = (int) (num_cols * MIN_ROWS/(1.0 * num_rows));
            num_rows = MIN_ROWS;
        }
        
        Mat result = Mat.zeros(num_rows, num_cols, candidate.type());
        
        Imgproc.resize(candidate, result, result.size(), 0, 0, Imgproc.INTER_CUBIC);
        return result;
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

}
