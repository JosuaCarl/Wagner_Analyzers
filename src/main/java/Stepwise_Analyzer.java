import ij.*;
import ij.gui.GenericDialog;
import ij.plugin.*;

import net.imagej.ImageJ;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Stepwise_Analyzer implements PlugIn, ImageAnalyzer {

    public void processImage(ImagePlus image, Path outFolder) {
        int[] dimensions = image.getDimensions();

        // dimensions = [width, height, channels, slices, frames]
        int slices = dimensions[3];
        int frames = dimensions[4];

        if (frames == 1 && slices == 1) {
            Logger.log("Detected image");
            imageAnalysis(image, outFolder, true);
        }
        else if (slices == 1){
            Logger.log("Detected video");
            videoAnalysis(image, outFolder);
        }
        else {
            Logger.log("Detected z-stack");
            zStackAnalysis(image, outFolder);
        }

        String[] imageTitles = WindowManager.getImageTitles();
        for(String title : imageTitles) {
            image  = WindowManager.getImage(title);
            image.changes = false;
            image.close();
        }
    }

    /*
    Analysis steps
     */

    public void imageAnalysis(ImagePlus image, Path outFolder, boolean withCommon) {
        Logger.log("Starting single image analysis...");

        ImagePlus[] rgbs = withCommon ?  commonAnalysisSteps(image) : ImageChanger.separateRGB(image);

        // Save merge
        ImagePlus composite = ImageChanger.mergeRGB(rgbs);
        ImageChanger.save_tif(composite, outFolder, "Merge");

        // Save single files
        String[] colorNamesImage = {"RR", "AF", "DAPI", "Ph2"};

        for (int i = 0; i < rgbs.length; i++) {
            ImagePlus rgbImage = rgbs[i];
            ImageChanger.save_tif(rgbImage, outFolder, colorNamesImage[i]);

            ImagePlus greyImage = ImageChanger.toGrey(rgbImage);
            ImageChanger.save_tif(greyImage, outFolder, colorNamesImage[i] + "_gray");
        }
    }


    public void videoAnalysis(ImagePlus image, Path outFolder) {
        Logger.log("Starting video analysis...");

        // function for video analysis
        ImagePlus[] rgbs = commonAnalysisSteps(image);

        GenericDialog frameDialog = new GenericDialog("Frame rate");
        double fps = image.getCalibration().fps;
        if (fps==0.0) fps = Animator.getFrameRate();
        if (fps<=0.5) fps = 0.5;
        frameDialog.addNumericField("Frames per second", fps, 0, 3, "fps");
        frameDialog.showDialog();
        fps = frameDialog.getNextNumber();
        frameDialog.dispose();

        // Save merge
        ImagePlus composite = ImageChanger.mergeRGB(rgbs);
        ImageChanger.save_avi(composite, outFolder, "Merge", fps);

        // Save single files
        String[] colorNamesVideo = {"TMR", "GFP", "Hoechst", "Ph2"};
        for (int i = 0; i < rgbs.length; i++) {
            ImageChanger.save_avi(rgbs[i], outFolder, colorNamesVideo[i], fps);
        }
    }


    public void zStackAnalysis(ImagePlus image, Path outFolder) {
        Logger.log("Starting z-stack analysis...");

        String[] projectionTypes = {"Z Project", "3D Project", "Select Z-level"};
        GenericDialog stackDialog = new GenericDialog("How should the stack be projected ?");
        stackDialog.addChoice("Projection Type:", projectionTypes, "Z Project");
        stackDialog.showDialog();
        String projectionType = stackDialog.getNextChoice();
        stackDialog.dispose();
        Logger.log("Selected projection type: " + projectionType);

        if (projectionType.equals("Select Z-level")) {
            image = ImageChanger.adjustBrightnessContrast(image);
            image = ImageChanger.crop(image);
            image = ImageChanger.makeSubstack(image);
            image = ImageChanger.addScaleBar(image);

            imageAnalysis(image, outFolder, false);
        }
        else {
            // function for z-stack analysis
            ImagePlus[] rgbs = commonAnalysisSteps(image);

            for (int i = 0; i < rgbs.length; i++) {
                ImagePlus rgbImage = rgbs[i];
                ImageChanger.setCurrentImage(rgbImage);

                if (projectionType.equals("Z Project")) {
                    rgbs[i] = ZProjector.run(rgbImage, "max");
                }
                else if (projectionType.equals("3D Project")) {
                    rgbs[i] = ImageChanger.project3D(rgbImage);
                }
            }

            // Save merge & individual colors
            ImagePlus composite = ImageChanger.mergeRGB(rgbs);
            String[] colorNames = {"TMR", "GFP", "Hoechst", "Ph2"};
            if (projectionType.equals("Z Project")) {
                ImageChanger.save_tif(composite, outFolder, "Merge");

                for (int i = 0; i < rgbs.length; i++) {
                    ImageChanger.save_tif(rgbs[i], outFolder, colorNames[i]);
                }
            }
            else if (projectionType.equals("3D Project")) {
                GenericDialog frameDialog = new GenericDialog("Frame rate");
                frameDialog.addNumericField("Frames per second:", 5, 0);
                frameDialog.showDialog();
                double fps = frameDialog.getNextNumber();
                frameDialog.dispose();


                for (int i = 0; i < rgbs.length; i++) {
                    ImageChanger.save_avi(rgbs[i], outFolder, colorNames[i], fps);
                }

                ImageChanger.save_avi(composite, outFolder, "Merge", fps);
            }
        }
    }

    public ImagePlus[] commonAnalysisSteps(ImagePlus image) {
        Logger.log("Starting common analysis steps...");

        image = ImageChanger.adjustBrightnessContrast(image);
        image = ImageChanger.crop(image);
        image = ImageChanger.addScaleBar(image);
        return ImageChanger.separateRGB(image);
    }

    //
    // Runner methods
    //

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        (new Stepwise_Analyzer()).run("");
    }

    public void run(String arg){
        Logger.log("Starting run.");
        Path inputDirectory = Paths.get( IJ.getDirectory("Choose input directory") ).normalize().toAbsolutePath();
        Path outputDirectory = Paths.get( IJ.getDirectory("Choose output directory") ).normalize().toAbsolutePath();
        String fileSuffix = IJ.getString("File suffix", ".nd2");

        Stepwise_Analyzer stepwiseAnalyzer = new Stepwise_Analyzer();
        FileNavigator fileNavigator = new FileNavigator(stepwiseAnalyzer);


        fileNavigator.processFolder(inputDirectory, outputDirectory, fileSuffix);
        Logger.log("Run complete.");
    }

}
