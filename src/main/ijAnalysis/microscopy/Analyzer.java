package ijAnalysis.microscopy;

import ij.*;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.*;
import ij.plugin.filter.AVI_Writer;
import ij.plugin.filter.RGBStackSplitter;
import ij.plugin.frame.ContrastAdjuster;
import ij.process.ColorSpaceConverter;
import ij.process.ImageConverter;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;


public class Analyzer implements PlugIn {
    Path inputDirectory;
    Path outputDirectory;
    String fileSuffix;

    public void run(String arg) {
        inputDirectory = Path.of("/Users/josuacarl/Downloads/test"); //Path.of( IJ.getDirectory("Choose input directory") ).normalize().toAbsolutePath();
        outputDirectory = Path.of("/Users/josuacarl/Downloads/test/out");// Path.of( IJ.getDirectory("Choose output directory") ).normalize().toAbsolutePath();
        fileSuffix = ".nd2"; //IJ.getString("File suffix", ".nd2");

        processFolder(inputDirectory, outputDirectory);
    }

    /**
     * Function to analyze files in a folder and mirror the results into an output directory.
     *
     * @param inFolder Input folder
     * @param outFolder Output folder
     */
    public void processFolder(Path inFolder, Path outFolder) {
        // function to scan folders/subfolders/files to find files with correct suffix
        try (Stream<Path> entries = Files.list(inFolder)) {
            for(Path entry : entries.toList()) {
                IJ.log("Checking " + entry.toString());
                if( Files.isDirectory(entry) && !entry.equals(outFolder)) {
                    Path newOutFolder = outFolder.resolve(entry.getFileName());
                    try {
                        IJ.log("Creating folder " + newOutFolder);
                        Files.createDirectory( newOutFolder );
                    } catch (IOException e) {
                        if (e instanceof FileAlreadyExistsException) {
                            IJ.log("Folder " + newOutFolder + " already exists.");
                        } else {
                            throw new RuntimeException(e);
                        }
                    }

                    processFolder(entry, newOutFolder);
                }

                if( entry.getFileName().toString().endsWith(fileSuffix) ) {
                    processFile(entry, outFolder);
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public void processFile(Path inFile, Path outFolder) {
        // Import
        IJ.log("Processing: " + inFile);

        // IJ.run("Bio-Formats Importer", "open=["+ inFile + "] color_mode=Default view=Hyperstack stack_order=XYCZT");

        ImagePlus image = IJ.openImage( inFile.toString() );
        image.show();

        int[] dimensions = image.getDimensions();
        int width = dimensions[0];
        int height = dimensions[1];
        int channels = dimensions[2];
        int slices = dimensions[3];
        int frames = dimensions[4];

        if (frames == 1 && slices == 1) {
            IJ.log("Detected image");
            imageAnalysis(image, outFolder, true);
        }
        else if (slices == 1){
            IJ.log("Detected video");
            videoAnalysis(image, outFolder);
        }
        else {
            IJ.log("Detected z-stack");
            zStackAnalysis(image, outFolder);
        }

        String[] imageTitles = WindowManager.getImageTitles();
        for(String title : imageTitles) {
            image = WindowManager.getImage(title);
            image.changes = false;
            image.close();
        }
    }

    /*
    Analysis steps
     */

    public void imageAnalysis(ImagePlus image, Path outFolder, boolean withCommon) {
        IJ.log("Starting single image analysis...");

        ImagePlus[] rgbs = withCommon ?  commonAnalysisSteps(image) : separateRGB(image);

        // Save merge
        ImagePlus composite = mergeRGB(rgbs);
        save_tif(composite, outFolder, "Merge");

        // Save single files
        String[] colorNamesImage = {"RR", "AF", "DAPI", "Ph2"};

        for (int i = 0; i < rgbs.length; i++) {
            ImagePlus rgbImage = rgbs[i];
            setCurrentImage(rgbImage);

            save_tif(rgbImage, outFolder, colorNamesImage[i]);

            ImagePlus greyImage = toGrey(rgbImage);
            save_tif(greyImage, outFolder, colorNamesImage[i] + "_gray");
        }
    }


    public void videoAnalysis(ImagePlus image, Path outFolder) {
        IJ.log("Starting video analysis...");

        // function for video analysis
        ImagePlus[] rgbs = commonAnalysisSteps(image);

        GenericDialog genericDialog = new GenericDialog("Saving options");
        double fps = image.getCalibration().fps;
        if (fps==0.0) fps = Animator.getFrameRate();
        if (fps<=0.5) fps = 0.5;
        genericDialog.addNumericField("Frames per second", fps, 0, 3, "fps");
        genericDialog.showDialog();
        fps = genericDialog.getNextNumber();

        // Save merge
        ImagePlus composite = mergeRGB(rgbs);
        save_avi(composite, outFolder, "Merge", fps);

        // Save single files
        String[] colorNamesVideo = {"TMR", "GFP", "Hoechst", "Ph2"};
        for (int i = 0; i < rgbs.length; i++) {
            save_avi(rgbs[i], outFolder, colorNamesVideo[i], fps);
        }
    }


    public void zStackAnalysis(ImagePlus image, Path outFolder) {
        IJ.log("Starting z-stack analysis...");

        String[] projectionTypes = {"Z Project", "3D Project", "Select Z-level"};
        GenericDialog genericDialog = new GenericDialog("How should the stack be projected ?");
        genericDialog.addChoice("Projection Type:", projectionTypes, "Z Project");
        genericDialog.showDialog();
        String projectionType = genericDialog.getNextChoice();

        if (projectionType.equals("Select Z-level")) {
            image = adjustBrightnessContrast(image);
            image = cropImage(image);
            image = addScaleBar(image);
            image = makeSubstack(image);

            imageAnalysis(image, outFolder, false);
        }
        else {
            // function for z-stack analysis
            ImagePlus[] rgbs = commonAnalysisSteps(image);

            for (int i = 0; i < rgbs.length; i++) {
                ImagePlus rgbImage = rgbs[i];
                setCurrentImage(rgbImage);

                if (projectionType.equals("Z Project")) {
                    rgbs[i] = ZProjector.run(rgbImage, "max");
                }
                else if (projectionType.equals("3D Project")) {
                    rgbs[i] = project3D(rgbImage);
                }
            }


            // Save merge & individual colors
            ImagePlus composite = mergeRGB(rgbs);
            String[] colorNames = {"TMR", "GFP", "Hoechst", "Ph2"};
            if (projectionType.equals("Z Project")) {
                save_tif(composite, outFolder, "Merge");

                for (int i = 0; i < rgbs.length; i++) {
                    save_tif(rgbs[i], outFolder, colorNames[i]);
                }
            }
            else if (projectionType.equals("3D Project")) {
                genericDialog = new GenericDialog("Saving options");
                genericDialog.addNumericField("Frames per second:", 5, 0);
                genericDialog.showDialog();
                double fps = genericDialog.getNextNumber();


                for (int i = 0; i < rgbs.length; i++) {
                    save_avi(rgbs[i], outFolder, colorNames[i], fps);
                }

                save_avi(composite, outFolder, "Merge", fps);
            }
        }
    }


    //	PRINCIPAL HELPER FUNCTIONS  //

    public void waitUntilClose(Window window) {
        final Object lock = new Object();

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ignore) {
            }
        }
    }


    public void setCurrentImage(ImagePlus image) {
        IJ.log("Setting current image to: " + image.getTitle());
        WindowManager.setCurrentWindow(image.getWindow());
    }

    public ImagePlus adjustBrightnessContrast(ImagePlus image) {
        setCurrentImage(image);
        IJ.log("Adjusting Brightness & Contrast...");

        // Adjust Brightness/Contrast
        ContrastAdjuster contrastAdjuster = new ContrastAdjuster();
        contrastAdjuster.run("");
        waitUntilClose(contrastAdjuster);

        return IJ.getImage();
    }

    public ImagePlus cropImage(ImagePlus image) {
        setCurrentImage(image);
        IJ.log("Cropping image...");

        String[] croppingOptions = {"manual", "specify", "manual+specify"};
        GenericDialog genericDialog = new GenericDialog("Cropping");
        genericDialog.addChoice("Cropping options:", croppingOptions, "manual");
        genericDialog.showDialog();
        String croppingOption =  genericDialog.getNextChoice();

        if(croppingOption.contains("manual")) {
            Roi roi = new Roi(1200, 1200, 1000, 1000);
            image.setRoi(roi);
            WaitForUserDialog waitForUserDialog = new WaitForUserDialog("Completed cropping?");
            waitForUserDialog.show();
        }
        if(croppingOption.contains("specify")) {
            SpecifyROI specifyROI = new SpecifyROI();
            specifyROI.run("");
        }

        Resizer resizer = new Resizer();
        resizer.run("crop");

        return IJ.getImage();
    }

    public ImagePlus addScaleBar(ImagePlus image) {
        setCurrentImage(image);
        IJ.log("Adding scale bar...");

        ScaleBar scaleBar = new ScaleBar();
        Macro.setOptions("width=10 height=5 thickness=5 font=0 hide overlay");
        scaleBar.run("");

        return IJ.getImage();
    }

    public ImagePlus toRGB(ImagePlus image) {
        if (!image.isRGB()) {
            IJ.log("Converting image to RGB...");
            RGBStackConverter.convertToRGB(image);
        }
        return image;
    }

    public ImagePlus toGrey(ImagePlus image) {
        IJ.log("Converting image to Grey...");

        CompositeImage compositeImage = new CompositeImage(image);
        compositeImage.setDisplayMode(IJ.GRAYSCALE);

        return compositeImage;
    }

    public ImagePlus[] separateRGB(ImagePlus image) {
        IJ.log("Separating Red Green and Blue...");

        ImagePlus[] splits = ChannelSplitter.split(image);

        for (ImagePlus split : splits){
            IJ.log("Showing split:" + split.getTitle());
            split.show();
        }

        return splits;
    }


    public ImagePlus mergeRGB(ImagePlus[] rgb) {
        IJ.log("Merging RGB Stack...");
        RGBStackMerge rgbStackMerge = new RGBStackMerge();
        ImagePlus composite = rgbStackMerge.mergeHyperstacks(rgb, true);

        return addScaleBar(composite);
    }

    public ImagePlus makeSubstack(ImagePlus image) {
        setCurrentImage(image);
        IJ.log("Making substack...");

        SubstackMaker substackMaker = new SubstackMaker();
        substackMaker.run("");

        return IJ.getImage();
    }

    public ImagePlus project3D(ImagePlus image) {
        setCurrentImage(image);
        IJ.log("3D projecting...");

        Projector projector = new Projector();
        Macro.setOptions("projection=[Brightest Point] axis=Y-Axis slice=0.20 initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=100 interior=50 interpolate");
        projector.run("");

        return IJ.getImage();
    }

    public ImagePlus[] commonAnalysisSteps(ImagePlus image) {
        IJ.log("Starting common analysis steps...");

        image = adjustBrightnessContrast(image);
        image = cropImage(image);
        image = addScaleBar(image);
        return separateRGB(image);
    }

    /*
     * SAVE Methods
     */

    public void save_tif(ImagePlus image, Path outpath, String suffix) {
        setCurrentImage(image);
        IJ.log("Saving tif...");

        image = image.flatten();

        String fileName = image.getTitle() + "_" + suffix;
        IJ.saveAs(image, "tif", outpath.resolve(fileName).toString() );
        image.changes = false;
        image.close();
    }

    public void save_avi(ImagePlus image, Path outpath, String suffix, double frames) {
        setCurrentImage(image);
        IJ.log("Saving avi...");

        AVI_Writer writer = new AVI_Writer();

        String filePath = outpath.resolve(image.getTitle() + "_" + suffix + ".avi").toString();

        Macro.setOptions("compression=JPEG frame="+ frames + " save="+ filePath);
        writer.run(image.getProcessor());
        image.close();
    }

}
