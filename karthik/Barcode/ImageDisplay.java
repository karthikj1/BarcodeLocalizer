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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author karthik
 */
public class ImageDisplay extends JPanel {

    BufferedImage image;
    private static JPanel imagePanel;
    private static JFrame frame;

    private ImageDisplay(String image_file) {
        try {
            /**
             * ImageIO.read() returns a BufferedImage object, decoding the
             * supplied file with an ImageReader, chosen automatically from
             * registered files The File is wrapped in an ImageInputStream
             * object, so we don't need one. Null is returned, If no registered
             * ImageReader claims to be able to read the resulting stream.
             */
            image = ImageIO.read(new File(image_file));
        } catch (IOException e) {
            //Let us know what happened  
            System.out.println("Error reading dir: " + e.getMessage());
        }

    }

    private ImageDisplay(Mat openCV_img) {
        try {
            image = getBufImg(openCV_img);
        } catch (IOException e) {
            //Let us know what happened  
            System.out.println("Error converting openCV image: " + e.getMessage());
        }
    }

    private ImageDisplay(BufferedImage img){
        image = img;
    }
    
    protected static BufferedImage getBufImg(Mat image) throws IOException {
        // converts image in an openCV Mat object into a Java BufferedImage 
        MatOfByte bytemat = new MatOfByte();
        Imgcodecs.imencode(".jpg", image, bytemat);
        InputStream in = new ByteArrayInputStream(bytemat.toArray());
        BufferedImage img = ImageIO.read(in);
        return img;
    }

    public Dimension getPreferredSize() {
        // set our preferred size if we succeeded in loading image  
        if (image == null)
            return new Dimension(100, 100);
        else
            return new Dimension(image.getWidth(null), image.getHeight(null));
    }

    public void paint(Graphics g) {
        //Draw our image on the screen with Graphic's "drawImage()" method  
        g.drawImage(image, 0, 0, null);
    }

    public static void showImageFrame(String image_file) {
    // convenience function that displays a frame with the image in the parameters
        
        displayFrame(new ImageDisplay(image_file), image_file);
    }

    public static void showImageFrame(Mat openCV_img, String title) {
    // convenience function that displays a frame with the image in the parameters
        displayFrame(new ImageDisplay(openCV_img), title);
    }

    public static ImageDisplay getImageFrame(Mat openCV_img, String title){
        // convenience function that displays a frame with the image in the parameters
        ImageDisplay window = new ImageDisplay(openCV_img);
        displayFrame(window, title);
        return window;        
    }
    
    public void updateImage(Mat img, String title){
        try {
            image = getBufImg(img);
        } catch (IOException e) {
            //Let us know what happened  
            System.out.println("Error converting openCV image: " + e.getMessage());
        }
        frame.setTitle(title);
        this.repaint();
    }
    
public static void showImageFrameGrid(Mat openCV_img, String title) {
    // convenience function that displays a frame with the image in the parameters
    Mat displayImg = openCV_img.clone();
    if(openCV_img.channels() < 3)
        Imgproc.cvtColor(openCV_img, displayImg, Imgproc.COLOR_GRAY2BGR);
    
    int rows = displayImg.rows();
    int cols = displayImg.cols();
    // draw rows
    for(int i = 0; i < rows; i += 10) 
        Imgproc.line(displayImg, new Point(0, i), new Point(cols - 1, i), new Scalar(0, 128, 255));
    
    for(int i = 0; i < cols; i += 10) 
        Imgproc.line(displayImg, new Point(i, 0), new Point(i, rows - 1), new Scalar(0, 128, 255));
    
    if(displayImg.rows() > 750)
        Imgproc.resize(displayImg, displayImg, new Size(1000, 750));
    
    displayFrame(new ImageDisplay(displayImg), title);
    }
    
    public static void showImageFrame(BufferedImage img, String title) {
   // convenience function that displays a frame with the image in the parameters
        displayFrame(new ImageDisplay(img), title);
    }
    
    private static void displayFrame(ImageDisplay img, String title){
        // internal function that displays a frame with the image in the parameters
   
        frame = new JFrame(title);
        imagePanel = new JPanel();
        imagePanel.add(img);
        JScrollPane scroll = new JScrollPane(imagePanel);
        frame.add(scroll);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);        
    }
    
    public void close(){
        frame.dispose();
    }
}
