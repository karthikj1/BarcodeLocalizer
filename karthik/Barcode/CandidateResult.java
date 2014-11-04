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

import java.awt.image.BufferedImage;
import org.opencv.core.Mat;
import org.opencv.core.Point;

/**
 *
 * @author karthik
 */
public class CandidateResult {
    public Mat ROI;
    public Point[] ROI_coords;
    public BufferedImage candidate;
    
    public String getROI_coords(){
        StringBuffer result = new StringBuffer("");
        
        for (Point p:ROI_coords){
            result.append("(" + Math.round(p.x * 1000)/1000.0 + "," + Math.round(p.y * 1000)/1000.0 +"), ");                    
        }
        return result.toString();
    }
}
