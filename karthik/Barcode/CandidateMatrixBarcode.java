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

import java.util.Collections;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

/**
 *
 * @author karthik
 */
public class CandidateMatrixBarcode extends CandidateBarcode{

    private static Scalar ZERO_SCALAR = new Scalar(0);
    
    CandidateMatrixBarcode(ImageInfo img_details, RotatedRect minRect, SearchParameters params) {
        super(img_details, minRect, params);
        
        int adj_factor = params.RECT_HEIGHT/params.TILE_SIZE;
        
        Point candidateCentre = new Point(minRect.center.x * adj_factor, minRect.center.y * adj_factor);
        Size candidateSize = new Size(minRect.size.width * adj_factor, minRect.size.height * adj_factor);
        RotatedRect candidateRect = new RotatedRect(candidateCentre, candidateSize, minRect.angle);
        this.candidateRegion = candidateRect;
        
    }
    
    CandidateResult NormalizeCandidateRegion(double angle) {
        /* candidateRegion is the RotatedRect which contains a candidate region for the barcode
         // angle is the rotation angle or USE_ROTATED_RECT_ANGLE for this function to 
         // estimate rotation angle from the rect parameter
         // returns Mat containing cropped area(region of interest) with just the barcode 
         // The barcode region is from the *original* image, not the scaled image
         // the cropped area is also rotated as necessary to be horizontal or vertical rather than skewed        
         // Some parts of this function are from http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
         // and http://stackoverflow.com/questions/22041699/rotate-an-image-without-cropping-in-opencv-in-c
         */
//        Mat rotation_matrix, enlarged;
        
        double rotation_angle;
        CandidateResult result = new CandidateResult();

        int orig_rows = img_details.src_original.rows();
        int orig_cols = img_details.src_original.cols();

        int offsetX = img_details.offsetX;
        int offsetY = img_details.offsetY;
        img_details.enlarged.setTo(ZERO_SCALAR);

        img_details.src_original.copyTo(img_details.enlarged.rowRange(offsetY, offsetY + orig_rows).colRange(offsetX,
            offsetX + orig_cols));
        // scale candidate region back up to original size to return cropped part from *original* image 
        // need the 1.0 there to force floating-point arithmetic from int values
        double scale_factor = orig_rows / (1.0 * img_details.src_grayscale.rows());        
         
        // expand the region found - this helps capture the entire code including the border zone
          candidateRegion.size.width +=  2 * params.RECT_WIDTH;
          candidateRegion.size.height += 2 * params.RECT_HEIGHT;

        // calculate location of rectangle in original image and its corner points
        RotatedRect scaledRegion = new RotatedRect(candidateRegion.center, candidateRegion.size, candidateRegion.angle);
        scaledRegion.center.x = scaledRegion.center.x * scale_factor + offsetX;
        scaledRegion.center.y = scaledRegion.center.y * scale_factor + offsetY;
        scaledRegion.size.height *= scale_factor;
        scaledRegion.size.width *= scale_factor;
        Point[] scaledCorners = new Point[4];
        scaledRegion.points(scaledCorners);
        // scaledCorners contains the coordinates of the candidate region in the Mat enlarged
        // lets get the coordinates of the ROI in the original image and save it
        result.ROI_coords = new Point[4];
        for (int r = 0; r < 4; r ++){
            result.ROI_coords[r] = new Point(scaledCorners[r].x - offsetX, scaledCorners[r].y - offsetY);
        }
        
        if (angle == Barcode.USE_ROTATED_RECT_ANGLE)
            rotation_angle = estimate_barcode_orientation();
        else
            rotation_angle = angle;

        img_details.rotation_matrix = Imgproc.getRotationMatrix2D(img_details.enlarged_centre, rotation_angle, 1.0);

        // perform the affine transformation
        img_details.rotation_matrix.convertTo(img_details.rotation_matrix, CvType.CV_32F); // convert type so matrix multip. works properly
        
        img_details.newCornerPoints.clear();
        img_details.newCornerCoord.setTo(ZERO_SCALAR);
        // calculate the new location for each corner point of the rectangle ROI
        for (Point p : scaledCorners) {
            img_details.coord.put(0, 0, p.x);
            img_details.coord.put(1, 0, p.y);
            Core.gemm(img_details.rotation_matrix, img_details.coord, 1, img_details.delta, 0, img_details.newCornerCoord);
            img_details.newCornerPoints.add(new Point(img_details.newCornerCoord.get(0, 0)[0], img_details.newCornerCoord.get(1, 0)[0]));
        }
        img_details.rotated.setTo(ZERO_SCALAR);
        Imgproc.warpAffine(img_details.enlarged, img_details.rotated, img_details.rotation_matrix, img_details.enlarged.size(), Imgproc.INTER_CUBIC);

        // sort rectangles points in order by first sorting all 4 points based on x
        // we then sort the first two based on y and then the next two based on y
        // this leaves the array in order top-left, bottom-left, top-right, bottom-right
        Collections.sort(img_details.newCornerPoints, CandidateBarcode.get_x_comparator());
        Collections.sort(img_details.newCornerPoints.subList(0, 2), CandidateBarcode.get_y_comparator());
        Collections.sort(img_details.newCornerPoints.subList(2, 4), CandidateBarcode.get_y_comparator());
        
        // calc height and width of rectangular region

        double height = length(img_details.newCornerPoints.get(1), img_details.newCornerPoints.get(0));
        double width = length(img_details.newCornerPoints.get(2), img_details.newCornerPoints.get(0));
        
        // create destination points for warpPerspective to map to
        img_details.transformedPoints.clear();
        img_details.transformedPoints.add(new Point(0, 0));
        img_details.transformedPoints.add(new Point(0, height));
        img_details.transformedPoints.add(new Point(width, 0));
        img_details.transformedPoints.add(new Point(width, height));

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(Converters.vector_Point2f_to_Mat(img_details.newCornerPoints),
            Converters.vector_Point2f_to_Mat(img_details.transformedPoints));
        Mat perspectiveOut = Mat.zeros((int) height + 2, (int) width + 2, CvType.CV_32F);
        Imgproc.warpPerspective(img_details.rotated, perspectiveOut, perspectiveTransform, perspectiveOut.size(),
            Imgproc.INTER_CUBIC);

        result.ROI = perspectiveOut;
        
        return result;
    }
}
