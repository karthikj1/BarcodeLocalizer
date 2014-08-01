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

    protected String name;

    // size of rectangular window to calculate D_i matrices 
    protected int RECT_WIDTH, RECT_HEIGHT;

    boolean DEBUG_IMAGES;

    // TODO: put below matrices in a separate ImageInfo class so it can be passed easily to CandidateBarcode class
//    Mat src_scaled, src_grayscale, E3;
//    Mat gradient_direction, gradient_magnitude;
    protected ImageInfo img_details;
    protected int rows, cols;
 
    List<BufferedImage> candidateBarcodes = new ArrayList<>();

    public static enum CodeType {LINEAR, MATRIX};
    protected CodeType searchType;

    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    Barcode(String filename) {
        name = filename;
        img_details = new ImageInfo(loadImage(filename));
        
        rows = img_details.src_original.rows();
        cols = img_details.src_original.cols();
        DEBUG_IMAGES = false;
      }

    public abstract List<BufferedImage> findBarcode();
    
    Mat NormalizeCandidateRegion(RotatedRect rect) {
        // rect is the RotatedRect which contains a candidate region for the barcode
        // returns Mat containing cropped area(region of interest) with just the barcode 
        // The barcode region is from the *original* image, not the scaled image
        // the cropped area is also rotated as necessary to be horizontal or vertical rather than skewed
        // matrices we'll use
        // parts of this are from http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
        // and http://stackoverflow.com/questions/22041699/rotate-an-image-without-cropping-in-opencv-in-c
        
        Mat rotation_matrix, rotated, cropped;
        // get angle and size from the bounding box
        double orientation = rect.angle + 90;
        double rotation_angle;

        
        int orig_rows = img_details.src_original.rows();
        int orig_cols = img_details.src_original.cols();
        
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

        Size rect_size = new Size(rect.size.width, rect.size.height);
        cropped = new Mat();

        // find orientation for barcode
        if (rect_size.width < rect_size.height) {
            orientation += 90;
            double temp = rect_size.width;
            rect_size.width = rect_size.height;
            rect_size.height = temp;
        }

        rotation_angle = orientation - 90;  // rotate 90 degress from its orientation to straighten it out       
        
        // get the rotation matrix
        Point src_center = new Point(rotated.cols() / 2.0, rotated.rows() / 2.0);
        rotation_matrix = Imgproc.getRotationMatrix2D(src_center, rotation_angle, 1.0);
        // perform the affine transformation
        Imgproc.warpAffine(rotated, rotated, rotation_matrix, rotated.size(), Imgproc.INTER_CUBIC);
        // crop the resulting image
        Imgproc.getRectSubPix(rotated, rect_size, rect.center, cropped);
        ImageDisplay.showImageFrame(cropped, "Cropped and deskewed image");
        return cropped;
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
