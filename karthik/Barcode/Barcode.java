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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author karthik
 */
public abstract class Barcode {
// parent class containing common methods and definitions for 1D and 2D barcode searches

    public String getName() {
        return name;
    }

    // flag to indicate what kind of searches to perform on image to locate barcode
    protected int sizeFlag = TryHarderFlags.VERY_SMALL_MATRIX.value();
    protected boolean postProcessResizeBarcode = true;
    protected static double USE_ROTATED_RECT_ANGLE = 361;

    protected String name; // filename of barcode image file

    boolean DEBUG_IMAGES;   // flag if we want to show intermediate steps for debugging

    SearchParameters searchParams; //various parameters and thresholds used during the search 
    protected ImageInfo img_details;
    protected int rows, cols;
    
    List<CandidateResult> candidateBarcodes = new ArrayList<CandidateResult>();    
    
    static enum CodeType {

        LINEAR, MATRIX
    };

    Barcode(String filename, TryHarderFlags flag) throws IOException {
        name = filename;
        img_details = new ImageInfo(loadImage());

        rows = img_details.src_original.rows();
        cols = img_details.src_original.cols();

        setBarcodeSize(flag);

        DEBUG_IMAGES = false;
    }

    Barcode(Mat img, TryHarderFlags flag) throws IOException {
        // used in mobile implementation to avoid recreating Mat objects repeatedly
        img_details = new ImageInfo(img);

        rows = img_details.src_original.rows();
        cols = img_details.src_original.cols();

        setBarcodeSize(flag);
        DEBUG_IMAGES = false;
    }

    public static boolean updateImage(Barcode barcode, final Mat img, final String img_name) {
        barcode.name = img_name;
        return updateImage(barcode, img);
    }

    public static boolean updateImage(Barcode barcode, Mat img) {
        // used for video or camera feed when all images are the same size
        int orig_rows = barcode.img_details.src_original.rows();
        int orig_cols = barcode.img_details.src_original.cols();

        int new_rows = img.rows();
        int new_cols = img.cols();

        if ((orig_rows != new_rows) || (orig_cols != new_cols))
            return false;

        barcode.candidateBarcodes.clear();
        barcode.img_details.src_original = img;
        Imgproc.resize(barcode.img_details.src_original, barcode.img_details.src_scaled, barcode.img_details.src_scaled.size(), 0, 0,
            Imgproc.INTER_AREA);
        Imgproc.cvtColor(barcode.img_details.src_scaled, barcode.img_details.src_grayscale, Imgproc.COLOR_RGB2GRAY);

        return true;
    }

    public void setBarcodeSize(TryHarderFlags size) {
        // at least one of the size flags must be set so it chooses NORMAL as the default if nothing is set
        sizeFlag = size.value();
        setSearchParameters(size);
    }

    public void doPostProcessResizeBarcode(boolean postProcess) {
        postProcessResizeBarcode = postProcess;
    }

    protected void setSearchParameters(TryHarderFlags flags) {
        // should not be used when multiple size flags are set
        // it will set the search parameters to one of them and ignore the others

        if ((sizeFlag & TryHarderFlags.SMALL.value()) != 0)
            searchParams = SearchParameters.getSmallParameters();

        if ((sizeFlag & TryHarderFlags.LARGE.value()) != 0)
            searchParams = SearchParameters.getLargeParameters();

        if ((sizeFlag & TryHarderFlags.NORMAL.value()) != 0)
            searchParams = SearchParameters.getNormalParameters();

        if ((sizeFlag & TryHarderFlags.VERY_SMALL_MATRIX.value()) != 0)
            searchParams = SearchParameters.getVSmall_MatrixParameters();

        preprocess_image();
    }

    // actual locateBarcode algo must be implemented in child class
    public abstract List<CandidateResult> locateBarcode() throws IOException;

    protected void preprocess_image() {
   // pre-process image to convert to grayscale and do morph black hat
        // also resizes image if it is above a specified size and sets the search parameters
        // based on image size

    // shrink image if it is above a certain size   
        // it reduces image size for large images which helps with processing speed
        // and reducing sensitivity to barcode size within the image
        if (rows > searchParams.MAX_ROWS) {
            cols = (int) (cols * (searchParams.MAX_ROWS * 1.0 / rows));
            rows = searchParams.MAX_ROWS;
            img_details.src_scaled = new Mat(rows, cols, CvType.CV_32F);
            Imgproc.resize(img_details.src_original, img_details.src_scaled, img_details.src_scaled.size(), 0, 0, Imgproc.INTER_AREA);
        }
        if (img_details.src_scaled == null)
            img_details.src_scaled = img_details.src_original.clone();

        searchParams.setImageSpecificParameters(rows, cols);
        // do pre-processing to increase contrast
        img_details.initializeMats(rows, cols, searchParams);
        
        Imgproc.cvtColor(img_details.src_scaled, img_details.src_grayscale, Imgproc.COLOR_RGB2GRAY);
    }

    protected Mat scale_candidateBarcode(Mat candidate) {
    // resizes candidate image to have at least MIN_COLS columns and MIN_ROWS rows
        // called when RESIZE_BEFORE_DECODE is set - seems to help ZXing decode barcode

        int MIN_COLS = 200;
        int MIN_ROWS = 200;

        int num_rows = candidate.rows();
        int num_cols = candidate.cols();

        if ((num_cols > MIN_COLS) && (num_rows > MIN_ROWS))
            return candidate;

        if (num_cols < MIN_COLS) {
            num_rows = (int) (num_rows * MIN_COLS / (1.0 * num_cols));
            num_cols = MIN_COLS;
        }

        if (num_rows < MIN_ROWS) {
            num_cols = (int) (num_cols * MIN_ROWS / (1.0 * num_rows));
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

        if (searchParams.is_VSmallMatrix) {
            // test out slightly different process for small codes in a large image
            small_elemSE = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, searchParams.elem_size);
            large_elemSE = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, searchParams.large_elem_size);

            Imgproc.dilate(img_details.probabilities, img_details.probabilities, small_elemSE);
            Imgproc.erode(img_details.probabilities, img_details.probabilities, large_elemSE);

            Imgproc.erode(img_details.probabilities, img_details.probabilities, small_elemSE);
            Imgproc.dilate(img_details.probabilities, img_details.probabilities, large_elemSE);
            return;
        }

        small_elemSE = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, searchParams.elem_size);
        large_elemSE = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, searchParams.large_elem_size);

        Imgproc.dilate(img_details.probabilities, img_details.probabilities, small_elemSE);
        Imgproc.erode(img_details.probabilities, img_details.probabilities, large_elemSE);

        Imgproc.erode(img_details.probabilities, img_details.probabilities, small_elemSE);
        Imgproc.dilate(img_details.probabilities, img_details.probabilities, large_elemSE);
    }

    protected double calc_rect_sum(Mat integral, int top_row, int bottom_row, int left_col, int right_col) {
        // calculates sum of values within a rectangle from a given integral image
        // if the right col or bottom row falls outside the image bounds, sets it to max col and max row
        // in actuality, top_row - 1 and left_col - 1 are used - see p. 185 of Learning OpenCV ed. 1 by Gary Bradski for an explanation
        // if top_row or left_col are outside image boundaries, it uses 0 for their value
        // this is useful when one part of the rectangle lies outside the image bounds
        
        double top_left, top_right, bottom_left, bottom_right;
        double sum;

        int numRows = integral.rows();
        int numCols = integral.cols();
        
        // do bounds checking on provided parameters
        bottom_row = java.lang.Math.min(bottom_row, numRows);
        right_col = java.lang.Math.min(right_col, numCols);
        
        bottom_right = integral.get(bottom_row, right_col)[0];
        top_right = (top_row < 0) ? 0 : integral.get(top_row, right_col)[0];
        top_left = (left_col < 0 || top_row < 0) ? 0 : integral.get(top_row, left_col)[0];
        bottom_left = (left_col < 0) ? 0 : integral.get(bottom_row, left_col)[0];

        sum = (bottom_right - bottom_left - top_right + top_left);
        return sum;
    }

    protected static void write_Mat(String filename, Mat img) {
        // write the contents of a Mat object to disk
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

    protected Mat loadImage() throws IOException {
    // reads the image file in the class variable name
        // Imgcodecs produces an incomprehensible error message if the filename is incorrect so
        // we do the check ourselves first
        File f = new File(name);
        if (!f.isFile())
            throw new IOException("BarcodeLocalizer was called with an invalid filename " + name);
        return Imgcodecs.imread(name, Imgcodecs.CV_LOAD_IMAGE_COLOR);
    }

}
