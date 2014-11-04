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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

/**
 *
 * @author karthik
 */
public class CandidateMatrixBarcode extends CandidateBarcode{

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
        Mat rotation_matrix, enlarged;
        double rotation_angle;
        CandidateResult result = new CandidateResult();

        int orig_rows = img_details.src_original.rows();
        int orig_cols = img_details.src_original.cols();
        int diagonal = (int) Math.sqrt(orig_rows * orig_rows + orig_cols * orig_cols);

        int newWidth = diagonal;
        int newHeight = diagonal;

        int offsetX = (newWidth - orig_cols) / 2;
        int offsetY = (newHeight - orig_rows) / 2;
        enlarged = new Mat(newWidth, newHeight, img_details.src_original.type());

        img_details.src_original.copyTo(enlarged.rowRange(offsetY, offsetY + orig_rows).colRange(offsetX,
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

        Point centre = new Point(enlarged.rows() / 2.0, enlarged.cols() / 2.0);
        rotation_matrix = Imgproc.getRotationMatrix2D(centre, rotation_angle, 1.0);

        // perform the affine transformation
        rotation_matrix.convertTo(rotation_matrix, CvType.CV_32F); // convert type so matrix multip. works properly
        List<Point> newCornerPoints = new ArrayList<Point>();
        Mat newCornerCoord = Mat.zeros(2, 1, CvType.CV_32F);
        Mat coord = Mat.ones(3, 1, CvType.CV_32F);
        // calculate the new location for each corner point of the rectangle ROI
        for (Point p : scaledCorners) {
            coord.put(0, 0, p.x);
            coord.put(1, 0, p.y);
            Core.gemm(rotation_matrix, coord, 1, Mat.zeros(3, 3, CvType.CV_32F), 0, newCornerCoord);
            newCornerPoints.add(new Point(newCornerCoord.get(0, 0)[0], newCornerCoord.get(1, 0)[0]));
        }
        Mat rotated = Mat.zeros(enlarged.size(), enlarged.type());
        Imgproc.warpAffine(enlarged, rotated, rotation_matrix, enlarged.size(), Imgproc.INTER_CUBIC);

        Point rectPoints[] = newCornerPoints.toArray(new Point[4]);

        // sort rectangles points in order by first sorting all 4 points based on x
        // we then sort the first two based on y and then the next two based on y
        // this leaves the array in order top-left, bottom-left, top-right, bottom-right
        Arrays.sort(rectPoints, new CandidateBarcode.compare_x());
        if (rectPoints[0].y > rectPoints[1].y) {
            Point temp = rectPoints[1];
            rectPoints[1] = rectPoints[0];
            rectPoints[0] = temp;
        }

        if (rectPoints[2].y > rectPoints[3].y) {
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
        List<Point> transformedPoints = new ArrayList<Point>();
        transformedPoints.add(new Point(0, 0));
        transformedPoints.add(new Point(0, height));
        transformedPoints.add(new Point(width, 0));
        transformedPoints.add(new Point(width, height));

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(Converters.vector_Point2f_to_Mat(newCornerPoints),
            Converters.vector_Point2f_to_Mat(transformedPoints));
        Mat perspectiveOut = Mat.zeros((int) height + 2, (int) width + 2, CvType.CV_32F);
        Imgproc.warpPerspective(rotated, perspectiveOut, perspectiveTransform, perspectiveOut.size(),
            Imgproc.INTER_CUBIC);

        result.ROI = perspectiveOut;
        
        return result;
    }
}
