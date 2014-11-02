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
import java.io.IOException;
import java.util.List;
import java.util.*;
import karthik.Barcode.*;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.highgui.VideoCapture;

/**
 *
 * @author karthik
 */
public class SimpleBarcodeTester {

    private static String fileSeparator = System.getProperty("file.separator");

    private static boolean IS_VIDEO = false;
    private static boolean IS_CAMERA = false;
    private static boolean SHOW_INTERMEDIATE_STEPS = false;
    private static String imgFile;
    private static VideoCapture video;
    private static int CV_CAP_PROP_FPS = 5;
    private static int CV_CAP_PROP_POS_FRAMES = 1;
    private static int CV_FRAME_COUNT = 7;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.loadLibrary("opencv_ffmpeg249_64");
    }

    public static void main(String[] args) {

        Map<CharSequence, BarcodeLocation> results = new HashMap<>();
        show_usage_syntax();
        parse_args(args);
        if (IS_VIDEO && IS_CAMERA)
            IS_VIDEO = false;

        if (!IS_VIDEO && !IS_CAMERA)
            process_image();

        if (IS_VIDEO) {
            video = new VideoCapture(imgFile);
            results = processVideo(imgFile);
            if (results.size() == 0)
                System.out.println("No results found");
        }

        if (IS_CAMERA) {
            video = new VideoCapture(0);
            video.open(0);
            if (video.isOpened())
                System.out.println("Camera is open");
            results = processCamera("Camera feed");
            if (results.size() == 0)
                System.out.println("No results found");
            
        }
        Mat image = new Mat();
        for (CharSequence result : results.keySet()) {
            BarcodeLocation resultLoc = results.get(result);
            System.out.println("Found " + result + " " + resultLoc.toString());
            video.set(CV_CAP_PROP_POS_FRAMES, resultLoc.frame);
            video.read(image);
            Point[] rectPoints = resultLoc.coords;
            Scalar colour = new Scalar(0, 0, 255);
            for (int j = 0; j < 3; j++)
                Core.line(image, rectPoints[j], rectPoints[j + 1], colour, 2, Core.LINE_AA, 0);
            Core.line(image, rectPoints[3], rectPoints[0], colour, 2, Core.LINE_AA, 0);
            ImageDisplay.showImageFrame(image, "Barcode text - " + result);
        }
        if(IS_CAMERA)
            video.release();
    }

    private static Map<CharSequence, BarcodeLocation> processVideo(String filename) {

        double frames_per_second;
        int frame_count;
        Mat image = new Mat();

        Barcode barcode;
        Map<CharSequence, BarcodeLocation> foundCodes = new HashMap<>();

        try {
            frames_per_second = video.get(CV_CAP_PROP_FPS);
            frame_count = (int) video.get(CV_FRAME_COUNT);

            System.out.println("FPS is " + frames_per_second);
            System.out.println("Frame count is " + frame_count);
            video.read(image);
            ImageDisplay videoDisp = ImageDisplay.getImageFrame(image, "Video Frames");
            for (int i = 0; i < frame_count; i += (frames_per_second / 3.0)) {
                video.set(CV_CAP_PROP_POS_FRAMES, i);
                video.read(image);
                String imgName = filename + "_Frame_" + i;
        //    ImageDisplay.showImageFrame(image, imgName);

                barcode = new MatrixBarcode(imgName, image);

            // set the flags you want to use when searching for the barcode
                // flag types are described in the enum TryHarderFlags
                // default is TryHarderFlags.NORMAL
                barcode.setMultipleFlags(TryHarderFlags.VERY_SMALL_MATRIX, TryHarderFlags.POSTPROCESS_RESIZE_BARCODE);
            // findBarcode() returns a List<BufferedImage> with all possible candidate barcode regions from
                // within the image. These images then get passed to a decoder(we use ZXing here but could be any decoder)

                List<CandidateResult> results = barcode.findBarcode();

                String imgFile = barcode.getName();
                Map<CharSequence, BarcodeLocation> frame_results = decodeBarcodeFromVideo(results, i);
                foundCodes.putAll(frame_results);
                System.out.println("Processed frame " + i + "- Found " + frame_results.size() + " results");

                for (BarcodeLocation bl : frame_results.values()) {
                    Point[] rectPoints = bl.coords;
                    Scalar colour = new Scalar(255, 0, 0);
                    for (int j = 0; j < 3; j++)
                        Core.line(image, rectPoints[j], rectPoints[j + 1], colour, 2, Core.LINE_AA, 0);
                    Core.line(image, rectPoints[3], rectPoints[0], colour, 2, Core.LINE_AA, 0);
                }
                videoDisp.updateImage(image, "Video frame " + i);

            }            
            videoDisp.close();
        } catch (IOException ioe) {
            System.out.println("IO Exception when finding barcode " + ioe.getMessage());
        }
        
        return foundCodes;
    }

    private static Map<CharSequence, BarcodeLocation> processCamera(String caption) {

        double frames_per_second;
        int frame_count;
        Mat image = new Mat();
        Barcode barcode;
        Map<CharSequence, BarcodeLocation> foundCodes = new HashMap<>();

        try {
            frames_per_second = video.get(CV_CAP_PROP_FPS);
            frame_count = (int) video.get(CV_FRAME_COUNT);

            System.out.println("FPS is " + frames_per_second);
            System.out.println("Frame count is " + frame_count);
            video.read(image);
            ImageDisplay videoDisp = ImageDisplay.getImageFrame(image, "Video Frames");

            long end_time = System.currentTimeMillis() + 240000;
            int i = 0;
            while (System.currentTimeMillis() < end_time) {
                i += 1;
                video.read(image);
                String imgName = caption + "_" + System.currentTimeMillis();
        //    ImageDisplay.showImageFrame(image, imgName);

                barcode = new MatrixBarcode(imgName, image);

            // set the flags you want to use when searching for the barcode
                // flag types are described in the enum TryHarderFlags
                // default is TryHarderFlags.NORMAL
                barcode.setMultipleFlags(TryHarderFlags.VERY_SMALL_MATRIX, TryHarderFlags.POSTPROCESS_RESIZE_BARCODE);
            // findBarcode() returns a List<BufferedImage> with all possible candidate barcode regions from
                // within the image. These images then get passed to a decoder(we use ZXing here but could be any decoder)

                List<CandidateResult> results = barcode.findBarcode();

                String imgFile = barcode.getName();
                Map<CharSequence, BarcodeLocation> frame_results = decodeBarcodeFromVideo(results, i);
                foundCodes.putAll(frame_results);
                System.out.println("Processed frame " + i + "- Found " + frame_results.size() + " results");

                for (BarcodeLocation bl : frame_results.values()) {
                    Point[] rectPoints = bl.coords;
                    Scalar colour = new Scalar(255, 0, 0);
                    for (int j = 0; j < 3; j++)
                        Core.line(image, rectPoints[j], rectPoints[j + 1], colour, 2, Core.LINE_AA, 0);
                    Core.line(image, rectPoints[3], rectPoints[0], colour, 2, Core.LINE_AA, 0);
                }
                videoDisp.updateImage(image, "Video frame " + i);

            }
        } catch (IOException ioe) {
            System.out.println("IO Exception when finding barcode " + ioe.getMessage());
        }
        return foundCodes;
    }

    private static Map<CharSequence, BarcodeLocation> decodeBarcodeFromVideo(List<CandidateResult> candidateCodes,
        int frameNumber) {
        // decodes barcode using ZXing and either print the barcode text or says no barcode found
        Result result = null;
        Map<CharSequence, BarcodeLocation> results = new HashMap<>();

        for (CandidateResult cr : candidateCodes) {
            BufferedImage candidate = cr.candidate;
            LuminanceSource source = new BufferedImageLuminanceSource(candidate);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Reader reader = new MultiFormatReader();

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            try {
                result = reader.decode(bitmap, hints);
                results.put(result.getText(), new BarcodeLocation(cr.ROI_coords, frameNumber));
            } catch (ReaderException re) {
            }
        }

        return results;
    }

    private static void process_image() {
        Barcode barcode;
        // instantiate a class of type MatrixBarcode with the image filename
        try {
            barcode = new MatrixBarcode(imgFile, SHOW_INTERMEDIATE_STEPS);

            // set the flags you want to use when searching for the barcode
            // flag types are described in the enum TryHarderFlags
            // default is TryHarderFlags.NORMAL
            barcode.setMultipleFlags(TryHarderFlags.VERY_SMALL_MATRIX, TryHarderFlags.POSTPROCESS_RESIZE_BARCODE);
            // findBarcode() returns a List<BufferedImage> with all possible candidate barcode regions from
            // within the image. These images then get passed to a decoder(we use ZXing here but could be any decoder)
            List<CandidateResult> results = barcode.findBarcode();
            System.out.println("Decoding " + imgFile + " " + results.size() + " candidate codes found");

            String imgFile = barcode.getName();

            decodeBarcode(results, imgFile, "Localizer");
        } catch (IOException ioe) {
            System.out.println("IO Exception when finding barcode " + ioe.getMessage());
        }
    }

    private static void decodeBarcode(List<CandidateResult> candidateCodes, String filename, String caption) {
        // decodes barcode using ZXing and either print the barcode text or says no barcode found
        BufferedImage decodedBarcode = null;
        String title = null;
        Result result = null;
        
        for (CandidateResult cr : candidateCodes) {
            BufferedImage candidate = cr.candidate;
            decodedBarcode = null;
            LuminanceSource source = new BufferedImageLuminanceSource(candidate);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Reader reader = new MultiFormatReader();

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            try {
                result = reader.decode(bitmap, hints);
                decodedBarcode = candidate;
                title = filename + " " + caption + " - barcode text " + result.getText() + " " + cr.getROI_coords();
            } catch (ReaderException re) {
            }
            if (decodedBarcode == null) {
                title = filename + " - no barcode found - " + cr.getROI_coords();
                ImageDisplay.showImageFrame(candidate, title);
            } else {
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
        System.out.println("[-video] - <imagefile> is a video");
        System.out.println("[-camera] - capture from camera");
        System.out.println("");
    }

    private static void parse_args(String[] args) {
        int ctr = 0;
        String arg;

        while (ctr < args.length) {
            arg = args[ctr++];

            if (arg.equalsIgnoreCase("-debug")) {
                SHOW_INTERMEDIATE_STEPS = true;
                continue;
            }

            if (arg.equalsIgnoreCase("-video")) {
                IS_VIDEO = true;
                continue;
            }

            if (arg.equalsIgnoreCase("-camera")) {
                IS_CAMERA = true;
                continue;
            }
// must be filename if we got here
            imgFile = arg;
        }

    }

    private static class BarcodeLocation {

        int frame;
        Point[] coords;

        private BarcodeLocation(Point[] coords, int frame) {
            this.frame = frame;
            this.coords = coords;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer("Frame " + frame);

            for (Point p : coords)
                sb.append("(" + (int) (p.x) + "," + (int) (p.y) + "), ");

            return sb.toString();
        }
    }

}
