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
import java.util.List;
import java.util.*;
import javax.imageio.ImageIO;
import karthik.Barcode.*;

/**
 *
 * @author karthik
 */
public class SimpleBarcodeTester {

    private static String fileSeparator = System.getProperty("file.separator");
  
    private static boolean DO_ORACLE = false;    
    private static boolean SHOW_INTERMEDIATE_STEPS = false;    
    private static String imgFile;
    
    public static void main(String[] args) {
        List<BufferedImage> candidateCodes = new ArrayList<>();        
        
        Barcode barcode;
        show_usage_syntax();
        parse_args(args);

        // instantiate a class of type LinearBarcode or MatrixBarcode with the image filename 
        // if you are not sure of the barcode type, you can always try both - naturally this increases processing time
        try {        
             barcode = new MatrixBarcode(imgFile, SHOW_INTERMEDIATE_STEPS);

            // set the flags you want to use when searching for the barcode
            // flag types are described in the enum TryHarderFlags
            // default is TryHarderFlags.NORMAL
            
             barcode.setMultipleFlags(TryHarderFlags.VERY_SMALL_MATRIX, TryHarderFlags.POSTPROCESS_RESIZE_BARCODE);
            // findBarcode() returns a List<BufferedImage> with all possible candidate barcode regions from
            // within the image. These images then get passed to a decoder(we use ZXing here but could be any decoder)    
                candidateCodes = barcode.findBarcode();
                String imgFile = barcode.getName();
                
                System.out.println("Decoding " + imgFile + " " + candidateCodes.size() + " codes found");
                decodeBarcode(candidateCodes, imgFile, "Localizer");

                if (DO_ORACLE) {
                    // do comparison test with just ZXing on the source image
                    System.out.print("Now testing " + imgFile + " with just ZXing - ");
                    try {
                        List<BufferedImage> rawImage = new ArrayList<BufferedImage>();
                        rawImage.add(ImageIO.read(new File(imgFile)));
                        decodeBarcode(rawImage, imgFile, "ZXing");
                    } catch (IOException ioe) {
                        System.out.println("ZXing error reading " + imgFile + " " + ioe.getMessage());
                    }
                } // if(DO_ORACLE)
        } catch (IOException ioe) {
            System.out.println("IO Exception when finding barcode " + ioe.getMessage());
        }
    }

    private static void decodeBarcode(List<BufferedImage> candidateCodes, String filename, String caption) {
        // decodes barcode using ZXing and either print the barcode text or says no barcode found
        BufferedImage decodedBarcode = null;
        String title = null;
        Result result = null;

        for (BufferedImage candidate : candidateCodes) {            
            decodedBarcode = null;
            LuminanceSource source = new BufferedImageLuminanceSource(candidate);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Reader reader = new MultiFormatReader();

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            try {
                result = reader.decode(bitmap, hints);
                decodedBarcode = candidate;
                title = filename + " " + caption + " - barcode text " + result.getText();
            } catch (ReaderException re) {
            }
        if (decodedBarcode == null){
            title = filename + " - no barcode found";
            ImageDisplay.showImageFrame(candidate, title);            
        }
        else {
            ImageDisplay.showImageFrame(decodedBarcode, title);
            System.out.println("Barcode text for " + filename + " is " + result.getText());
        }
        }

    }
    
    private static void show_usage_syntax() {
        System.out.println("");
        System.out.println("Barcode localizer by Karthik Jayaraman");
        System.out.println("");
        System.out.println("Usage: BarcodeTester <imagefile> [-matrix] [-oracle] ");
        System.out.println("<imagefile> must be JPEG or PNG");
        System.out.println("[-debug] - shows images for intermediate steps and saves intermediate files");        
        System.out.println("[-oracle] - do comparison with ZXing alone processing the source image");
        System.out.println("");
    }

    private static void parse_args(String[] args) {
        int ctr = 0;
        String arg;

        while (ctr < args.length) {
            arg = args[ctr++];

            if (arg.equalsIgnoreCase("-oracle")) {
                DO_ORACLE = true;
                continue;
            }

            if (arg.equalsIgnoreCase("-debug")) {
                SHOW_INTERMEDIATE_STEPS = true;
                continue;
            }

// must be filename if we got here
            imgFile = arg;            
        }

    }
}
