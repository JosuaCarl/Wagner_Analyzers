import ij.*;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.*;

import net.imagej.ImageJ;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Stepwise_Analyzer implements PlugIn, ImageAnalyzer {

    Roi defaultRoi;
    String analysisType;

    public void processImage(ImagePlus image, Path outFolder) {
        int[] dimensions = image.getDimensions();

        // dimensions = [width, height, channels, slices, frames]
        int slices = dimensions[3];
        int frames = dimensions[4];

        if (frames == 1 && slices == 1) {
            Logger.log("Detected image");
            analysisType = imageAnalysis(image, outFolder, true);
        }
        else if (slices == 1){
            Logger.log("Detected video");
            analysisType = videoAnalysis(image, outFolder);
        }
        else {
            Logger.log("Detected z-stack");
            analysisType = zStackAnalysis(image, outFolder);
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

    public String imageAnalysis(ImagePlus image, Path outFolder, boolean withCommon) {
        Logger.log("Starting single image analysis...");

        ImagePlus[] rgbs = withCommon ?  commonAnalysisSteps(image) : ImageChanger.separateRGB(image);

        // Save merge
        ImagePlus composite = ImageChanger.mergeRGB(rgbs, image.getTitle());
        ImageChanger.save_tif(composite, outFolder, "Merge");

        // Save single files
        String[] colorNamesImage = {"RR", "AF", "DAPI", "Ph2"};

        for (int i = 0; i < rgbs.length; i++) {
            ImagePlus rgbImage = rgbs[i];
            ImageChanger.save_tif(rgbImage, outFolder, colorNamesImage[i]);

            ImagePlus greyImage = ImageChanger.toGrey(rgbImage);
            ImageChanger.save_tif(greyImage, outFolder, colorNamesImage[i] + "_gray");
        }

        return "image";
    }

    public String videoAnalysis(ImagePlus image, Path outFolder) {
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
        ImagePlus composite = ImageChanger.mergeRGB(rgbs, image.getTitle());
        ImageChanger.save_avi(composite, outFolder, "Merge", fps);

        // Save single files
        String[] colorNamesVideo = {"TMR", "GFP", "Hoechst", "Ph2"};
        for (int i = 0; i < rgbs.length; i++) {
            ImageChanger.save_avi(rgbs[i], outFolder, colorNamesVideo[i], fps);
        }

        return "video";
    }

    public String zStackAnalysis(ImagePlus image, Path outFolder) {
        Logger.log("Starting z-stack analysis...");

        String[] projectionTypes = {"Z Project", "3D Project", "Select Z-level"};

        boolean saveType = true;
        String projectionType = analysisType;
        if(projectionType == null) {
            GenericDialog stackDialog = new GenericDialog("How should the stack be projected ?");
            stackDialog.addChoice("Projection Type:", projectionTypes, "Z Project");
            stackDialog.addCheckbox("Save choice?", true);
            stackDialog.showDialog();
            projectionType = stackDialog.getNextChoice();
            saveType = stackDialog.getNextBoolean();
            stackDialog.dispose();
            Logger.log("Selected projection type: " + projectionType);
        }
        if (projectionType.equals("Select Z-level")) {
            image = ImageChanger.adjustBrightnessContrast(image);
            image = ImageChanger.crop(image, defaultRoi);
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
            ImagePlus composite = ImageChanger.mergeRGB(rgbs, image.getTitle());
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

        if (saveType) {
            return projectionType;
        }
        else {
            return null;
        }
    }

    public ImagePlus[] commonAnalysisSteps(ImagePlus image) {
        Logger.log("Starting common analysis steps...");

        image = ImageChanger.adjustBrightnessContrast(image);
        image = ImageChanger.crop(image, defaultRoi);
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

    private void defineDefaultRoi() {
        GenericDialog dialog = new GenericDialog("Specify default ROI");
        dialog.addNumericField("X:", 0, 0);
        dialog.addNumericField("Y:", 0, 0);
        dialog.addNumericField("Width:", 500, 0);
        dialog.addNumericField("Height:", 500, 0);
        dialog.showDialog();

        this.defaultRoi = new Roi(
                (int) dialog.getNextNumber(),(int) dialog.getNextNumber(),
                (int) dialog.getNextNumber(), (int) dialog.getNextNumber()
        );
    }

    public void run(String arg){
        Logger.log("Starting run.");
        GenericDialog dialog = new GenericDialog("Single file or folder processing");
        dialog.addChoice("Processing type:", new String[]{"Single file", "Folder"}, "Folder");
        dialog.showDialog();
        String processingType = dialog.getNextChoice();

        // Define Analyzer and Navigator
        Stepwise_Analyzer stepwiseAnalyzer = new Stepwise_Analyzer();
        stepwiseAnalyzer.defineDefaultRoi();

        FileNavigator fileNavigator = new FileNavigator(stepwiseAnalyzer);

        // Collect input
        Path inputPath;
        Path outputDirectory;
        String fileSuffix = null;
        switch (processingType) {
            case "Single file":
                inputPath = Paths.get( IJ.getFilePath("Choose input file") ).normalize().toAbsolutePath();
                outputDirectory = Paths.get( IJ.getDirectory("Choose output directory") ).normalize().toAbsolutePath();

                try {
                    fileNavigator.processFile(inputPath, outputDirectory, null);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;

            case "Folder":
                inputPath = Paths.get( IJ.getDirectory("Choose input directory") ).normalize().toAbsolutePath();
                outputDirectory = Paths.get( IJ.getDirectory("Choose output directory") ).normalize().toAbsolutePath();
                fileSuffix = IJ.getString("File suffix", ".nd2");

                try {
                    fileNavigator.processFolder(inputPath, outputDirectory, fileSuffix);
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
                break;
        }
        Logger.log("Run complete.");
    }
}
