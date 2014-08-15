package karthik;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

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
/**
 *
 * @author karthik
 */
class CandidateBarcode {

    private ImageInfo img_details;
    private RotatedRect minRect;
    private int num_blanks;
    private SearchParameters params;
    private int threshold;  // threshold for number of blanks around barcode
       

    CandidateBarcode(ImageInfo img_details, RotatedRect minRect, SearchParameters params) {
        this.img_details = img_details;
        this.minRect = minRect;
        this.params = params;
       // set threshold for number of blanks around barcode based on whether it is linear or 2D code
        threshold = (img_details.searchType == Barcode.CodeType.LINEAR) ? params.NUM_BLANKS_THRESHOLD : params.MATRIX_NUM_BLANKS_THRESHOLD;

    }

    void debug_drawCandidateRegion(RotatedRect region, Scalar colour, Mat img) {
        // convenience function to draw outline of candidate region on image
        Point rectPoints[] = new Point[4];
        region.points(rectPoints);
        // draw the rectangle
        for (int j = 0; j < 3; j++)
            Core.line(img, rectPoints[j], rectPoints[j + 1], colour, 2, Core.LINE_AA, 0);
        Core.line(img, rectPoints[3], rectPoints[0], colour, 2, Core.LINE_AA, 0);
    }

    RotatedRect getCandidateRegion() {
        /*
         Takes a candidate barcode region and expands it along its axes until it finds the 
         quiet zone on both sides and a border zone above and below
         For matrix barcodes, it finds border zones on all sides
         */
        RotatedRect expanded = new RotatedRect(minRect.center, minRect.size, minRect.angle);
        double start_x, start_y, x, y;

        // find orientation for barcode so that we can extend it along its long axis looking for quiet zone
        double barcode_orientation = minRect.angle + 90;
        if (minRect.size.width < minRect.size.height)
            barcode_orientation += 90;

        double long_axis = Math.max(minRect.size.width, minRect.size.height);
        double short_axis = Math.min(minRect.size.width, minRect.size.height);

        // TODO: change this code to increment more than one pixel at a time to improve speed
        double y_increment = Math.cos(Math.toRadians(barcode_orientation));
        double x_increment = Math.sin(Math.toRadians(barcode_orientation));
        /*
         * used to adjust the boundaries of the search area to avoid some issues created by rounding the pixel address
        */
        double adjust_y = Math.signum(y_increment);
        double adjust_x = Math.signum(x_increment);
        /*
         we calculate long_axis manually above because width and height parameters
         in RotatedRect don't reliably choose between longer of X or Y-axis
        
         to move parallel to long side from the rectangle's centre
         long_axis * cos(modified theta) moves along y-axis
         long_axis * sin(modified theta) moves along x-axis     
         cos for y and sin for x are correct - this is because the orientation angle was modified
         to allow for the weird way openCV RotatedRect records its rotation angle
         */
        num_blanks = 0;

        y = adjust_y + minRect.center.y + (long_axis / 2.0) * y_increment;
        x = adjust_x + minRect.center.x + (long_axis / 2.0) * x_increment;
        // start at one edge of candidate region
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countQuietZonePixel(y, x);
            x += x_increment;
            y += y_increment;
        }
        start_x = x;
        start_y = y;
        // now expand along other edge
        y = minRect.center.y - (long_axis / 2.0) * y_increment - adjust_y;
        x = minRect.center.x - (long_axis / 2.0) * x_increment - adjust_x;
        num_blanks = 0;
        //TODO: remove this
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countQuietZonePixel(y, x);
            x -= x_increment;
            y -= y_increment;
        }
        expanded.center.x = (start_x + x) / 2.0;
        expanded.center.y = (start_y + y) / 2.0;

        if (long_axis == minRect.size.width)
            expanded.size.width = length(x, y, start_x, start_y);
        else
            expanded.size.height = length(x, y, start_x, start_y);

        // now expand along short axis of candidate region to include full extent of barcode
        barcode_orientation = (barcode_orientation + 90) % 180;

        y_increment = Math.cos(Math.toRadians(barcode_orientation));
        x_increment = Math.sin(Math.toRadians(barcode_orientation));
        adjust_y = Math.signum(y_increment);
        adjust_x = Math.signum(x_increment);

        num_blanks = 0;

        y = adjust_y + minRect.center.y + (short_axis / 2.0) * y_increment;
        x = adjust_x + minRect.center.x + (short_axis / 2.0) * x_increment;
        double target_magnitude = img_details.E3.get((int) y, (int) x)[0];

        // start at "top" of candidate region i.e. moving parallel to barcode lines
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countBorderZonePixel(y, x, target_magnitude);
            x += x_increment;
            y += y_increment;
        }
        start_x = x;
        start_y = y;
        // now expand along "bottom"
        y = minRect.center.y - (short_axis / 2.0) * y_increment - adjust_y;
        x = minRect.center.x - (short_axis / 2.0) * x_increment - adjust_x;
        num_blanks = 0;
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countBorderZonePixel(y, x, target_magnitude);
            x -= x_increment;
            y -= y_increment;
        }
        expanded.center.x = (start_x + x) / 2.0;
        expanded.center.y = (start_y + y) / 2.0;

        if (short_axis == minRect.size.width)
            expanded.size.width = length(x, y, start_x, start_y);
        else
            expanded.size.height = length(x, y, start_x, start_y);

        minRect = expanded;
        return expanded;
    }

    RotatedRect getLinearBarcodeCandidateRegion(double barcode_orientation) {
        /*
         Takes a candidate linear barcode region and expands it along its axes until it finds the 
         quiet zone on both sides and a border zone above and below         
         */
        RotatedRect expanded = new RotatedRect(minRect.center, minRect.size, minRect.angle);
        expanded.angle = barcode_orientation;
        
        double start_x, start_y, x, y;
        System.out.println("Barcode orientation is  " + barcode_orientation + " Rotated rect angle is " + minRect.angle);
        System.out.println("minRect height = " + minRect.size.height + " width = " + minRect.size.width);
        
        // TODO: change this code to increment more than one pixel at a time to improve speed
        double y_increment = Math.cos(Math.toRadians(barcode_orientation));
        double x_increment = Math.sin(Math.toRadians(barcode_orientation));

        // expand along short axis of candidate region to include full height of barcode

        num_blanks = 0;

        y = minRect.center.y;
        x = minRect.center.x;
        // start at edge of captured area
        while(isValidCoordinate(x, y) && img_details.E3.get((int) y, (int) x)[0] == 255){
            x += x_increment;
            y += y_increment;            
        }
        double target_magnitude = img_details.gradient_magnitude.get((int) y, (int) x)[0];

        // start at "top" of candidate region i.e. moving parallel to barcode lines
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countBorderZonePixel(y, x, target_magnitude);
            x += x_increment;
            y += y_increment;
        }
        start_x = x;
        start_y = y;
        Core.circle(img_details.src_scaled, new Point(start_x, start_y), 3, new Scalar(255, 0, 0));
        // now expand along "bottom"
        y = minRect.center.y;
        x = minRect.center.x;
        num_blanks = 0;
        // start at other edge of captured area
        while(isValidCoordinate(x, y) && img_details.E3.get((int) y, (int) x)[0] == 255){
            x -= x_increment;
            y -= y_increment;            
        }
        target_magnitude = img_details.gradient_magnitude.get((int) y, (int) x)[0];

        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countBorderZonePixel(y, x, target_magnitude);
            x -= x_increment;
            y -= y_increment;
        }
        expanded.center.x = (start_x + x) / 2.0;
        expanded.center.y = (start_y + y) / 2.0;

        Core.circle(img_details.src_scaled, new Point(x, y), 3, new Scalar(255, 0, 0));
        expanded.size.height = length(x, y, start_x, start_y);
        System.out.println("expanded height = " + expanded.size.height);
        /*
         to move parallel to long side from the rectangle's centre
         */
        barcode_orientation = (barcode_orientation + 90) % 180;
        y_increment = Math.cos(Math.toRadians(barcode_orientation));
        x_increment = Math.sin(Math.toRadians(barcode_orientation));
        num_blanks = 0;

        y = minRect.center.y;
        x = minRect.center.x;
        // start at one edge of candidate region
        while(isValidCoordinate(x, y) && img_details.E3.get((int) y, (int) x)[0] == 255){
            x += x_increment;
            y += y_increment;            
        }
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countQuietZonePixel(y, x);
            x += x_increment;
            y += y_increment;
        }
        start_x = x;
        start_y = y;
        Core.circle(img_details.src_scaled, new Point(x, y), 3, new Scalar(255, 0, 0));
        // now expand along other edge
        y = minRect.center.y;
        x = minRect.center.x;
        num_blanks = 0;
        
        
        while(isValidCoordinate(x, y) && img_details.E3.get((int) y, (int) x)[0] == 255){
            x -= x_increment;
            y -= y_increment;            
        }
        while (isValidCoordinate(x, y) && (num_blanks < threshold)) {
            num_blanks = countQuietZonePixel(y, x);
            x -= x_increment;
            y -= y_increment;
        }
        expanded.center.x = (start_x + x) / 2.0;
        expanded.center.y = (start_y + y) / 2.0;
        Core.circle(img_details.src_scaled, new Point(x, y), 3, new Scalar(255, 0, 0));

        expanded.size.width = length(x, y, start_x, start_y);
        System.out.println("expanded width = " + expanded.size.width);
        expanded.angle = (expanded.angle % 90);
        if(expanded.size.width < expanded.size.height)
            expanded.angle -= 90;
        
        System.out.println("Expanded barcode angle is " + expanded.angle);
        
        minRect = expanded;
        return expanded;
    }

    
    Mat NormalizeCandidateRegion(double angle){
        /* rect is the RotatedRect which contains a candidate region for the barcode
        // angle is the rotation angle or USE_ROTATED_RECT_ANGLE for this function to 
        // estimate rotation angle from the rect parameter
        // returns Mat containing cropped area(region of interest) with just the barcode 
        // The barcode region is from the *original* image, not the scaled image
        // the cropped area is also rotated as necessary to be horizontal or vertical rather than skewed        
        // Some parts of this function are from http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
        // and http://stackoverflow.com/questions/22041699/rotate-an-image-without-cropping-in-opencv-in-c
        */
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
        minRect.center.x = minRect.center.x*scale_factor + offsetX;
        minRect.center.y = minRect.center.y*scale_factor + offsetY;
        minRect.size.height *= scale_factor;
        minRect.size.width *= scale_factor;
        Point[] scaledCorners = new Point[4];
        minRect.points(scaledCorners);       
                
  //      System.out.println("CropRectangle: Calculated angle " + rotation_angle + " provided " + angle);
         if(angle == Barcode.USE_ROTATED_RECT_ANGLE)
             rotation_angle = estimate_barcode_orientation();
         else
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
        Arrays.sort(rectPoints, new CandidateBarcode.compare_x());
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
            // calc height and width of rectangular region
        double height, width;
        height = length(rectPoints[1].x, rectPoints[1].y, rectPoints[0].x, rectPoints[0].y);
        width = length(rectPoints[2].x, rectPoints[2].y, rectPoints[0].x, rectPoints[0].y);
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
        
        return perspectiveOut;
    }
    
      private double estimate_barcode_orientation(){
        // uses angle of orientation of enclosing rotated rectangle to rotate barcode
        // and make it horizontal - only relevant for linear barcodes currently
              
        // get angle and size from the bounding box
        double orientation = minRect.angle + 90;
        double rotation_angle;
        Size rect_size = new Size(minRect.size.width, minRect.size.height);

        // find orientation for barcode
        if (rect_size.width < rect_size.height) 
            orientation += 90;

        rotation_angle = orientation - 90;  // rotate 90 degrees from its orientation to straighten it out    
        return rotation_angle;
    }

    private int countQuietZonePixel(double y, double x) {
        /* checks if the pixel is in the barcode or quiet zone region
         // code searches to find a consecutive sequence of low gradient points in 
         // the axis on which it is searching
         // it stops when it gets the consecutive sequence OR if it hits a point with high variance 
         // of angles in the area around it - this is a signal that it has left the barcode and quiet zone region
         */

        int int_y = (int) y;
        int int_x = (int) x;

        double val = img_details.adjusted_variance.get(int_y, int_x)[0];
        if (val == 0)
             // reset counter if we hit a gradient 
            // - handles situations when the original captured region only captured part of the barcode
            num_blanks = 0;
        
        if(val == LinearBarcode.NO_GRADIENT) // we hit a point with no gradient in the original image
            num_blanks++;
        
        if(val == LinearBarcode.HIGH_VARIANCE_GRADIENT) // we hit a point with a gradient but high variance of angles around it
            num_blanks = threshold;
        
        return num_blanks;
    }

    private int countBorderZonePixel(double y, double x, double magnitude) {
        /* checks if the pixel is in the barcode or the border region on top
         * code searches until it hits a point with high variance of angles in the area around it
         * magnitude controls what pixel magnitude to search for
         * we should be traversing along a line in the linear barcode so depending on 
         * whether the centre pixel fell on a white or black line, we follow that colour up and down
         * for a sequence of low-variance pixels or stop if it hits a high-variance pixel
         * - this is a signal that it has left the barcode and quiet zone region
         */

        int int_y = (int) y;
        int int_x = (int) x;
        assert (magnitude == 0 || magnitude == 255) : "Target magnitude must be 0 or 255, was " + magnitude + " in countBorderZonePixel";

        if (img_details.gradient_magnitude.get(int_y, int_x)[0] != magnitude)
            // stop when we are following a gradient and hit a non-gradient pixel or vice versa
            return threshold;

        if (img_details.adjusted_variance.get(int_y, int_x)[0] == LinearBarcode.HIGH_VARIANCE_GRADIENT)
            // stop if we hit a high-variance pixel
            return threshold;

        // otherwise increment number of low variance pixels and return
        return ++num_blanks;
    }

    private boolean isValidCoordinate(double x, double y) {
        // check if coordinate (x,y) is inside the bounds of the image in img_details
        if ((x < 0) || (y < 0))
            return false;

        if ((x >= img_details.E3.cols()) || (y >= img_details.E3.rows()))
            return false;

        return true;
    }

    private double length(double x1, double y1, double x2, double y2) {
        // returns length of line segment between (x1, y1) and (x2, y2)

        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
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
