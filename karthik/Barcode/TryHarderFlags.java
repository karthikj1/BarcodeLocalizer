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

/**
 *
 * @author karthik
 * enum to control how hard the localizer tries to find a barcode
 * default is NORMAL
 */
public enum TryHarderFlags {
    /*
     NORMAL, SMALL, LARGE and V_SMALL control the size of the window used to find the barcode
    Eg. if the barcode is small compared to the image size, SMALL has a better chance of finding it
    ALL_SIZES will try all of the sizes
    POSTPROCESS_RESIZE_BARCODE enlarges the captured region after localizing it.
    This can sometimes help the reader decode it more easily.
    */
    
    NORMAL(1), SMALL(2), LARGE(4), VERY_SMALL_LINEAR(8), VERY_SMALL_MATRIX(16), ALL(255);
        
        private int val;
        
        TryHarderFlags(int val) {
            this.val = val;
        }                
        
        int value(){
            return val;
        }
}
