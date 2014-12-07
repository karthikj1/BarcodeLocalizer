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

import java.util.Arrays;
import java.util.Collections;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

/**
 *
 * @author karthik
 */
public class CandidateMatrixBarcode extends CandidateBarcode{

    private static final Scalar ZERO_SCALAR = new Scalar(0);
    
    CandidateMatrixBarcode(ImageInfo img_details, RotatedRect minRect, SearchParameters params) {
        super(img_details, minRect, params);
        
        int adj_factor = params.RECT_HEIGHT/params.PROB_MAT_TILE_SIZE;
        
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
        
        double rotation_angle;
        CandidateResult result = new CandidateResult();

        // scale candidate region back up to original size to return cropped part from *original* image 
        // need the 1.0 there to force floating-point arithmetic from int values
        double scale_factor = img_details.src_original.rows() / (1.0 * img_details.src_grayscale.rows());        
         
        // expand the region found - this helps capture the entire code including the border zone
        candidateRegion.size.width +=  2 * params.RECT_WIDTH;
        candidateRegion.size.height += 2 * params.RECT_HEIGHT;

        // calculate location of rectangle in original image and its corner points
        RotatedRect scaledRegion = new RotatedRect(candidateRegion.center, candidateRegion.size, candidateRegion.angle);
        scaledRegion.center.x = scaledRegion.center.x * scale_factor;
        scaledRegion.center.y = scaledRegion.center.y * scale_factor;
        scaledRegion.size.height *= scale_factor;
        scaledRegion.size.width *= scale_factor;
        
        scaledRegion.points(img_details.scaledCorners);
        // lets get the coordinates of the ROI in the original image and save it
        
        result.ROI_coords = Arrays.copyOf(img_details.scaledCorners, 4);
        
        // get the bounding rectangle of the ROI by sorting its corner points
        // we do it manually because RotatedRect can generate corner points outside the Mat area
        Arrays.sort(img_details.scaledCorners, CandidateBarcode.get_x_comparator());
        int leftCol = (int) img_details.scaledCorners[0].x;
        int rightCol = (int) img_details.scaledCorners[3].x;
        leftCol = (leftCol < 0) ? 0 : leftCol;
        rightCol = (rightCol > img_details.src_original.cols() - 1) ? img_details.src_original.cols() - 1 : rightCol;
        
        Arrays.sort(img_details.scaledCorners, CandidateBarcode.get_y_comparator());
        int topRow = (int) img_details.scaledCorners[0].y;
        int bottomRow = (int) img_details.scaledCorners[3].y;        
        topRow = (topRow < 0) ? 0 : topRow;
        bottomRow = (bottomRow > img_details.src_original.rows() - 1) ? img_details.src_original.rows() - 1 : bottomRow;
        
        Mat ROI_region = img_details.src_original.submat(topRow, bottomRow, leftCol, rightCol);
        
        // create a container that is a square with side = diagonal of ROI.
        // this is large enough to accommodate the ROI region with rotation without cropping it
        
        int orig_rows = bottomRow - topRow;
        int orig_cols = rightCol - leftCol;
        int diagonal = (int) Math.sqrt(orig_rows * orig_rows + orig_cols * orig_cols);

        int newWidth = diagonal + 1;
        int newHeight = diagonal + 1;

        int offsetX = (newWidth - orig_cols) / 2;
        int offsetY = (newHeight - orig_rows) / 2;

        Mat enlarged_ROI_container = new Mat(newWidth, newHeight, img_details.src_original.type());
        enlarged_ROI_container.setTo(ZERO_SCALAR);
        
        // copy ROI to centre of container and rotate it
        ROI_region.copyTo(enlarged_ROI_container.rowRange(offsetY, offsetY + orig_rows).colRange(offsetX,
            offsetX + orig_cols));        
        Point enlarged_ROI_container_centre = new Point(enlarged_ROI_container.rows()/2.0, enlarged_ROI_container.cols()/2.0);
        Mat rotated = Mat.zeros(enlarged_ROI_container.size(), enlarged_ROI_container.type());

        if (angle == Barcode.USE_ROTATED_RECT_ANGLE)
            rotation_angle = estimate_barcode_orientation();
        else
            rotation_angle = angle;

        // perform the affine transformation
        img_details.rotation_matrix = Imgproc.getRotationMatrix2D(enlarged_ROI_container_centre, rotation_angle, 1.0);
        img_details.rotation_matrix.convertTo(img_details.rotation_matrix, CvType.CV_32F); // convert type so matrix multip. works properly
        
        img_details.newCornerCoord.setTo(ZERO_SCALAR);
        
        // convert scaledCorners to contain locations of corners in enlarged_ROI_container Mat
        img_details.scaledCorners[0] = new Point(offsetX, offsetY);
        img_details.scaledCorners[1] = new Point(offsetX, offsetY + orig_rows);
        img_details.scaledCorners[2] = new Point(offsetX + orig_cols, offsetY);
        img_details.scaledCorners[3] = new Point(offsetX + orig_cols, offsetY + orig_rows);
        // calculate the new location for each corner point of the rectangle ROI after rotation
        for (int r = 0; r < 4; r++) {
            img_details.coord.put(0, 0, img_details.scaledCorners[r].x);
            img_details.coord.put(1, 0, img_details.scaledCorners[r].y);
            Core.gemm(img_details.rotation_matrix, img_details.coord, 1, img_details.delta, 0, img_details.newCornerCoord);
            updatePoint(img_details.newCornerPoints.get(r), img_details.newCornerCoord.get(0, 0)[0], img_details.newCornerCoord.get(1, 0)[0]);
        }
        rotated.setTo(ZERO_SCALAR);
        Imgproc.warpAffine(enlarged_ROI_container, rotated, img_details.rotation_matrix, enlarged_ROI_container.size(), Imgproc.INTER_CUBIC);
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
        updatePoint(img_details.transformedPoints.get(0), 0, 0);
        updatePoint(img_details.transformedPoints.get(1), 0, height);
        updatePoint(img_details.transformedPoints.get(2), width, 0);
        updatePoint(img_details.transformedPoints.get(3), width, height);

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(Converters.vector_Point2f_to_Mat(img_details.newCornerPoints),
            Converters.vector_Point2f_to_Mat(img_details.transformedPoints));
        Mat perspectiveOut = Mat.zeros((int) height + 2, (int) width + 2, CvType.CV_32F);
        Imgproc.warpPerspective(rotated, perspectiveOut, perspectiveTransform, perspectiveOut.size(),
            Imgproc.INTER_CUBIC);

        result.ROI = perspectiveOut;
        return result;
    }
    
    private static void updatePoint(Point p, double newX, double newY){
        p.x = newX;
        p.y = newY;
    }
}
