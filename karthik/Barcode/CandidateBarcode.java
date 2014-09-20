package karthik.Barcode;

import java.util.Comparator;
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

    protected ImageInfo img_details;
    protected RotatedRect candidateRegion;
    protected int num_blanks;
    protected SearchParameters params;
    protected int threshold;  // threshold for number of blanks around barcode

    protected CandidateBarcode(ImageInfo img_details, RotatedRect minRect, SearchParameters params) {
        this.img_details = img_details;
        this.candidateRegion = minRect;
        
        this.params = params;
        // set threshold for number of blanks around barcode based on whether it is linear or 2D code
        threshold = (img_details.searchType == Barcode.CodeType.LINEAR) ? params.NUM_BLANKS_THRESHOLD : params.MATRIX_NUM_BLANKS_THRESHOLD;
}

    protected void debug_drawCandidateRegion(Scalar colour, Mat img) {
        // convenience function to draw outline of candidate region on image
        Point rectPoints[] = new Point[4];
        candidateRegion.points(rectPoints);
        // draw the rectangle
        for (int j = 0; j < 3; j++)
            Core.line(img, rectPoints[j], rectPoints[j + 1], colour, 2, Core.LINE_AA, 0);
        Core.line(img, rectPoints[3], rectPoints[0], colour, 2, Core.LINE_AA, 0);
    }

    protected double estimate_barcode_orientation() {
        // uses angle of orientation of enclosing rotated rectangle to rotate barcode
        // and make it horizontal - only relevant for linear barcodes currently

        // get angle and size from the bounding box
        double orientation = candidateRegion.angle + 90;
        double rotation_angle;
        Size rect_size = new Size(candidateRegion.size.width, candidateRegion.size.height);

        // find orientation for barcode
        if (rect_size.width < rect_size.height)
            orientation += 90;

        rotation_angle = orientation - 90;  // rotate 90 degrees from its orientation to straighten it out    
        return rotation_angle;
    }

    protected double length(double x1, double y1, double x2, double y2) {
        // returns length of line segment between (x1, y1) and (x2, y2)

        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    protected class compare_x implements Comparator<Point> {

        // Comparator class to compare x coordinate of Point objects
        public int compare(Point a, Point b) {
            if (a.x == b.x)
                return 0;
            if (a.x > b.x)
                return 1;
            return -1;
        }
    }
}
