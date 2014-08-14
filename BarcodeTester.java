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

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.*;
import javax.imageio.ImageIO;
import karthik.Barcode;
import karthik.ImageDisplay;
import karthik.LinearBarcode;
import karthik.MatrixBarcode;

/**
 *
 * @author karthik
 */
public class BarcodeTester {

    public static void main(String[] args) {
        String fileSeparator = System.getProperty("file.separator");
        String imgDir = "images";
        File folder = new File(System.getProperty("user.dir") + fileSeparator + imgDir);
        List<File> listOfFiles = new ArrayList<>(Arrays.asList(folder.listFiles()));
        List<String> images = new ArrayList<>();
        List<BufferedImage> candidateCodes = new ArrayList<>();
        String s;
        boolean show_intermediate_steps = true;
        boolean test_2D = true;
        Barcode b;

        if (false)
            for (File f : listOfFiles) {
                s = f.getName();
                if (test_2D) {
                    if (s.matches("(?i).*?(QR|Matrix).*?.[jpg|png|gif]"))
                        images.add(imgDir + fileSeparator + s);
                } else if (s.matches("(?i).*?barcode.*?.[jpg|png|gif]"))
                    images.add(imgDir + fileSeparator + s);
            }

        images.add(imgDir + fileSeparator + "QRCode14.jpg");
        if (images.size() > 1)
            show_intermediate_steps = false;

        for (String imgFile : images) {
            if (imgFile.contains("barcode") || imgFile.contains("Barcode"))
                b = new LinearBarcode(imgFile, show_intermediate_steps);
            else
                b = new MatrixBarcode(imgFile, show_intermediate_steps);

            b.setMultipleFlags(Barcode.TryHarderFlags.NORMAL);
            try {
                candidateCodes = b.findBarcode();
            } catch (IOException ioe) {
                System.out.println("IO Exception when finding barcode " + ioe.getMessage());
            }

            decodeBarcode(candidateCodes, imgFile, "Localizer", show_intermediate_steps);
            if (false) {
                // do comparison test with just ZXing on the source image
                System.out.print("Now testing " + imgFile + " with just ZXing - ");
                try {
                    List<BufferedImage> rawImage = new ArrayList<BufferedImage>();
                    rawImage.add(ImageIO.read(new File(imgFile)));
                    decodeBarcode(rawImage, imgFile, "ZXing", show_intermediate_steps);
                } catch (IOException ioe) {
                    System.out.println("ZXing error reading " + imgFile + " " + ioe.getMessage());
                }
            }
        }
    }

    private static void decodeBarcode(List<BufferedImage> candidateCodes, String filename, String caption, boolean show_intermediate) {

        BufferedImage decodedBarcode = null;
        String title = null;
        Result result = null;

        for (BufferedImage candidate : candidateCodes) {
            if(show_intermediate){
                ImageDisplay.showImageFrame(candidate, filename + " candidate");
            try {
                ImageIO.write(candidate, "JPG", new File("Candidate.jpg"));
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage());
            }
            }
            LuminanceSource source = new BufferedImageLuminanceSource(candidate);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Reader reader = new MultiFormatReader();

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            // hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            // hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.allOf(BarcodeFormat.class));
            try {
                result = reader.decode(bitmap, hints);
                decodedBarcode = candidate;
                title = filename + " " + caption + " - barcode text " + result.getText();                
            } catch (ReaderException re) {} 
      /*      try {
        // Look for multiple barcodes
                MultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(reader);
                Result[] theResults = multiReader.decodeMultiple(bitmap, hints);
                if (theResults != null)
                    System.out.println("got a result");
            } catch (ReaderException re) {
                System.out.println("Exception - " + re.getClass());
            }*/
        }

        if (decodedBarcode == null)
            System.out.println(filename + " " + caption + " - no barcode found");
        else{
            ImageDisplay.showImageFrame(decodedBarcode, title);
            System.out.println("Barcode text for " + filename + " is " + result.getText());
        }
    }
}
