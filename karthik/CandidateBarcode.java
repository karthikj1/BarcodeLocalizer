package karthik;

import org.opencv.core.*;

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

    private final int NUM_BLANKS_THRESHOLD = 10;

    CandidateBarcode(ImageInfo img_details, RotatedRect minRect) {
        this.img_details = img_details;
        this.minRect = minRect;
    }

    void drawCandidateRegion(RotatedRect region, Scalar colour, Mat img) {
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
         we calculate long_axis manually above because width and height parameters
         in RotatedRect don't reliably choose between longer of X or Y-axis
        
         to move parallel to long side from the rectangle's centre
         long_axis * cos(modified theta) moves along y-axis
         long_axis * sin(modified theta) moves along x-axis     
         cos for x and sin for y are correct - this is because the orientation angle was modified
         to allow for the weird way openCV RotatedRect records its rotation angle
         */
        num_blanks = 0;

        y = minRect.center.y + (long_axis / 2.0) * y_increment;
        x = minRect.center.x + (long_axis / 2.0) * x_increment;
        // start at one edge of candidate region
        while (isValidCoordinate(x, y) && (num_blanks < NUM_BLANKS_THRESHOLD)) {
            num_blanks = (img_details.searchType == Barcode.CodeType.LINEAR) ? 
                countQuietZonePixel(y, x) : countMatrixBorderZonePixel(y, x);
            x += x_increment;
            y += y_increment;
        }
        start_x = x;
        start_y = y;
        // now expand along other edge
        y = minRect.center.y - (long_axis / 2.0) * y_increment;
        x = minRect.center.x - (long_axis / 2.0) * x_increment;
        num_blanks = 0;
        while (isValidCoordinate(x, y) && (num_blanks < NUM_BLANKS_THRESHOLD)) {
            num_blanks = (img_details.searchType == Barcode.CodeType.LINEAR) ? countQuietZonePixel(y, x) : 
                countMatrixBorderZonePixel(y, x);
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

        num_blanks = 0;

        y = minRect.center.y + (short_axis / 2.0) * y_increment;
        x = minRect.center.x + (short_axis / 2.0) * x_increment;
        double target_magnitude = img_details.E3.get((int) y, (int) x)[0];

        // start at "top" of candidate region i.e. moving parallel to barcode lines
        while (isValidCoordinate(x, y) && (num_blanks < NUM_BLANKS_THRESHOLD)) {
            num_blanks = (img_details.searchType == Barcode.CodeType.LINEAR) ? 
                countBorderZonePixel(y, x, target_magnitude) : countMatrixBorderZonePixel(y, x);
            x += x_increment;
            y += y_increment;
        }
        start_x = x;
        start_y = y;
        // now expand along "bottom"
        y = minRect.center.y - (short_axis / 2.0) * y_increment;
        x = minRect.center.x - (short_axis / 2.0) * x_increment;
        num_blanks = 0;
        while (isValidCoordinate(x, y) && (num_blanks < NUM_BLANKS_THRESHOLD)) {
            num_blanks = (img_details.searchType == Barcode.CodeType.LINEAR) ? countBorderZonePixel(y, x,
                target_magnitude) : countMatrixBorderZonePixel(y, x);
            x -= x_increment;
            y -= y_increment;
        }
        expanded.center.x = (start_x + x) / 2.0;
        expanded.center.y = (start_y + y) / 2.0;

        if (short_axis == minRect.size.width)
            expanded.size.width = length(x, y, start_x, start_y);
        else
            expanded.size.height = length(x, y, start_x, start_y);

        return expanded;
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

        if (img_details.gradient_magnitude.get(int_y, int_x)[0] == 0)
            if (img_details.E3.get(int_y, int_x)[0] == 0)
                num_blanks++;
            else
                num_blanks = NUM_BLANKS_THRESHOLD;
        else // reset counter if we hit a gradient 
            // - handles situations when we the original captured region only captured part of the barcode
            num_blanks = 0;
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
            return NUM_BLANKS_THRESHOLD;

        if (img_details.E3.get(int_y, int_x)[0] == 255)
            // stop if we hit a high-variance pixel
            return NUM_BLANKS_THRESHOLD;

        // otherwise increment number of low variance pixels and return
        return ++num_blanks;
    }

    private int countMatrixBorderZonePixel(double y, double x) {
        // finds pixels in the border zone for a matrix barcode
        // stops when it finds a sequence of low-probability pixels 
        int int_y = (int) y;
        int int_x = (int) x;

        if (img_details.E3.get(int_y, int_x)[0] == 0)
            // increment if we hit a low probability pixel
            return ++num_blanks;
        else // reset and start search for sequence again
            num_blanks = 0;

        return num_blanks;

    }

    private boolean isValidCoordinate(double x, double y) {
        // check if coordinate (x,y) is inside the bounds of the image in img_details
        if ((x < 0) || (y < 0))
            return false;

        if ((x >= img_details.E3.cols()) || (y >= img_details.E3.rows()))
            return false;

        return true;
    }

    private void printPoints(RotatedRect rotRect) {

        Point[] points = new Point[4];
        rotRect.points(points);

        for (Point p : points)
            System.out.println("Point coords are x = " + p.x + " y = " + p.y);
    }

    private double length(double x1, double y1, double x2, double y2) {
        // returns length of line segment between (x1, y1) and (x2, y2)

        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

}
