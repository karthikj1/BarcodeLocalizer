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
package karthik;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.*;

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
        boolean test_2D = false;
        Barcode b;
        
        if(false){
        for (File f : listOfFiles) {
            s = f.getName();
                if(test_2D){
                    if(s.matches("(?i).*?(QR|Matrix).*?.[jpg|png]"))
                        images.add(imgDir + fileSeparator + s);
                }
                else if(s.matches("(?i).*?barcode.*?.[jpg|png]"))
                    images.add(imgDir + fileSeparator + s);
            }
        }
        

        images.add(imgDir + fileSeparator + "barcode15.jpg");
        if(images.size() > 1)
            show_intermediate_steps = false;
        
        for(String imgFile: images){
            if (imgFile.contains("barcode") || imgFile.contains("Barcode"))            
                b = new LinearBarcode(imgFile, show_intermediate_steps);   
            else
                b = new MatrixBarcode(imgFile, show_intermediate_steps); 
            // b.setMultipleFlags(Barcode.TryHarderFlags.TRY_LARGE);
            candidateCodes = b.findBarcode();
            for(BufferedImage img: candidateCodes){
                  ImageDisplay.showImageFrame(img, "Tester:" + imgFile + " with cropped candidate barcode");
                  decodeBarcode(img, imgFile);
            }

        }
    
        System.out.println("Now testing with just ZXing");
    
      /*  for (String imgFile : images) {           
        try{
            System.out.println("ZXing checking " + imgFile);
            BufferedImage img = ImageIO.read(new File(imgFile));
            decodeBarcode(img, imgFile);
        }
        catch(IOException ioe){
                System.out.println("ZXing error reading " + imgFile + " " + ioe.getMessage());      
        }
        }*/
    }
    
    private static void decodeBarcode(BufferedImage candidate, String filename){
        LuminanceSource source = new BufferedImageLuminanceSource(candidate);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Reader reader = new MultiFormatReader();
        
        Map<DecodeHintType, Boolean> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try{
        Result result = reader.decode(bitmap, hints);
        System.out.println("Barcode text for " + filename + " is " + result.getText());
        }
        catch(NotFoundException nfe){
            System.out.println("ZXing - Could not find a barcode in " + filename + " " + nfe.getMessage());
        }
        catch(ChecksumException cse){
            System.out.println("ZXing - Barcode failed checksum: " + cse.getMessage());            
        }
        catch(FormatException fe){
            System.out.println("ZXing - Barcode format was invalid: " + fe.getMessage());                        
        }

    }
}
