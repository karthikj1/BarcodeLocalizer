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

import org.opencv.core.Mat;

/**
 *
 * @author karthik
 */
class HistogramResult {

    int[] histogram;
    int max_bin, second_highest_bin;

    private HistogramResult(int numBins) {
        histogram = new int[numBins];
        max_bin = second_highest_bin = 0;
    }

    static HistogramResult calcHist(Mat imgWindow, Mat weights, int histLow, int histHigh, int binWidth) {
        /*
         *
         */
        assert (imgWindow.size() == weights.size()) : "calcHist: imgwindow and weights matrix must be of the same size";
        assert (imgWindow.channels() == 1) : "calcHist: imgWindow must have only one channel";
        assert (weights.channels() == 1) : "calcHist: weights must have only one channel";

        int numBins = 1 + (histHigh - histLow) / binWidth;
        HistogramResult result = new HistogramResult(numBins);
        int rows = imgWindow.rows();
        int cols = imgWindow.cols();

        int bin, imgData;

        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                imgData = (int) imgWindow.get(i, j)[0];
                if ((imgData < histLow) || (imgData >= histHigh))
                    continue;

                bin = (imgData - histLow) / binWidth;
                if (weights == null)
                    result.updateBin(bin, 1);
                else
                    result.updateBin(bin, (int) weights.get(i, j)[0]);
            }
        return result;
    }

    private void updateBin(int bin, int weight) {
        histogram[bin] += weight;
        if (histogram[bin] > histogram[second_highest_bin])
            second_highest_bin = bin;

        if (histogram[second_highest_bin] > histogram[max_bin]) {
            int temp = max_bin;
            max_bin = second_highest_bin;
            second_highest_bin = temp;
        }

    }

    int getMaxBinCount() {
        return histogram[max_bin];
    }

    int getSecondBinCount() {
        return histogram[second_highest_bin];
    }

}
