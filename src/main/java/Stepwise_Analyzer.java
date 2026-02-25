import ij.*;
import ij.gui.GenericDialog;
import ij.plugin.*;
import ij.plugin.filter.AVI_Writer;
import ij.plugin.frame.ContrastAdjuster;

import net.imagej.ImageJ;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Stepwise_Analyzer implements PlugIn {

    //
    // Runner methods
    //
    protected static void log(String message) {
        System.out.println(message);
        IJ.log(message);
    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        (new Stepwise_Analyzer()).run("");
    }

    public void run(String arg){
        Path inputDirectory = Paths.get( IJ.getDirectory("Choose input directory") ).normalize().toAbsolutePath();
        Path outputDirectory = Paths.get( IJ.getDirectory("Choose output directory") ).normalize().toAbsolutePath();
        String fileSuffix = IJ.getString("File suffix", ".nd2");

        processFolder(inputDirectory, outputDirectory, fileSuffix);
    }

    /**
     * Function to analyze files in a folder and mirror the results into an output directory.
     *
     * @param inFolder Input folder
     * @param outFolder Output folder
     */
    public static void processFolder(Path inFolder, Path outFolder, String fileSuffix) {
        // function to scan folders/subfolders/files to find files with correct suffix
        try (Stream<Path> entries = Files.list(inFolder)) {
            for(Path entry : entries.collect(Collectors.toList())) {
                log("Checking out folder: " + entry.toString());
                if( Files.isDirectory(entry) && !entry.equals(outFolder)) {
                    Path newOutFolder = outFolder.resolve(entry.getFileName());
                    try {
                        log("Creating folder " + newOutFolder);
                        Files.createDirectory( newOutFolder );
                    } catch (IOException e) {
                        if (e instanceof FileAlreadyExistsException) {
                            log("Folder " + newOutFolder + " already exists.");
                        } else {
                            throw new RuntimeException(e);
                        }
                    }

                    processFolder(entry, newOutFolder, fileSuffix);
                }

                if( entry.getFileName().toString().endsWith(fileSuffix) ) {
                    processFile(entry, outFolder);
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static void processFile(Path inFile, Path outFolder) throws IOException {
        log("Processing: " + inFile);

        // Import
        ImagePlus image = IJ.openImage( inFile.toString() );
        image.show();

        processImage(image, outFolder);
    }

    public static void processImage(ImagePlus image, Path outFolder) {
        int[] dimensions = image.getDimensions();

        // dimensions = [width, height, channels, slices, frames]
        int slices = dimensions[3];
        int frames = dimensions[4];

        if (frames == 1 && slices == 1) {
            log("Detected image");
            imageAnalysis(image, outFolder, true);
        }
        else if (slices == 1){
            log("Detected video");
            videoAnalysis(image, outFolder);
        }
        else {
            log("Detected z-stack");
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

    public static void imageAnalysis(ImagePlus image, Path outFolder, boolean withCommon) {
        log("Starting single image analysis...");

        ImagePlus[] rgbs = withCommon ?  commonAnalysisSteps(image) : separateRGB(image);

        // Save merge
        ImagePlus composite = mergeRGB(rgbs);
        save_tif(composite, outFolder, "Merge");

        // Save single files
        String[] colorNamesImage = {"RR", "AF", "DAPI", "Ph2"};

        for (int i = 0; i < rgbs.length; i++) {
            ImagePlus rgbImage = rgbs[i];
            save_tif(rgbImage, outFolder, colorNamesImage[i]);

            ImagePlus greyImage = toGrey(rgbImage);
            save_tif(greyImage, outFolder, colorNamesImage[i] + "_gray");
        }
    }


    public static void videoAnalysis(ImagePlus image, Path outFolder) {
        log("Starting video analysis...");

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
        ImagePlus composite = mergeRGB(rgbs);
        save_avi(composite, outFolder, "Merge", fps);

        // Save single files
        String[] colorNamesVideo = {"TMR", "GFP", "Hoechst", "Ph2"};
        for (int i = 0; i < rgbs.length; i++) {
            save_avi(rgbs[i], outFolder, colorNamesVideo[i], fps);
        }
    }


    public static void zStackAnalysis(ImagePlus image, Path outFolder) {
        log("Starting z-stack analysis...");

        String[] projectionTypes = {"Z Project", "3D Project", "Select Z-level"};
        GenericDialog stackDialog = new GenericDialog("How should the stack be projected ?");
        stackDialog.addChoice("Projection Type:", projectionTypes, "Z Project");
        stackDialog.showDialog();
        String projectionType = stackDialog.getNextChoice();
        stackDialog.dispose();
        log("Selected projection type: " + projectionType);

        if (projectionType.equals("Select Z-level")) {
            image = adjustBrightnessContrast(image);
            image = cropImage(image);
            image = makeSubstack(image);
            image = addScaleBar(image);

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
                GenericDialog frameDialog = new GenericDialog("Frame rate");
                frameDialog.addNumericField("Frames per second:", 5, 0);
                frameDialog.showDialog();
                double fps = frameDialog.getNextNumber();
                frameDialog.dispose();


                for (int i = 0; i < rgbs.length; i++) {
                    save_avi(rgbs[i], outFolder, colorNames[i], fps);
                }

                save_avi(composite, outFolder, "Merge", fps);
            }
        }
    }


    //	PRINCIPAL HELPER FUNCTIONS  //

    public static void waitUntilClose(Window window) {
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


    public static void setCurrentImage(ImagePlus image) {
        log("Setting current image to: " + image.getTitle());
        WindowManager.setCurrentWindow(image.getWindow());
    }

    public static ImagePlus adjustBrightnessContrast(ImagePlus image) {
        setCurrentImage(image);
        log("Adjusting Brightness & Contrast...");

        // Adjust Brightness/Contrast
        ContrastAdjuster contrastAdjuster = new ContrastAdjuster();
        contrastAdjuster.run("");
        waitUntilClose(contrastAdjuster);

        return IJ.getImage();
    }

    public static ImagePlus cropImage(ImagePlus image) {
        setCurrentImage(image);
        log("Cropping image...");

        SpecifyROI_Interactively specifyRoiInteractively = new SpecifyROI_Interactively();
        specifyRoiInteractively.runOnImage(image);

        Resizer resizer = new Resizer();
        resizer.run("crop");

        return IJ.getImage();
    }

    public static ImagePlus addScaleBar(ImagePlus image) {
        setCurrentImage(image);
        log("Adding scale bar...");

        ScaleBar scaleBar = new ScaleBar();
        Macro.setOptions("width=10 height=5 thickness=5 font=0 hide overlay");
        scaleBar.run("");
        Macro.setOptions(null);

        return IJ.getImage();
    }

    public static ImagePlus toRGB(ImagePlus image) {
        if (!image.isComposite()) {
            log("Converting image to RGB...");
            RGBStackConverter.convertToRGB(image);
        }
        return image;
    }

    public static ImagePlus toGrey(ImagePlus image) {
        log("Converting image to Grey...");

        CompositeImage compositeImage = new CompositeImage(image);
        compositeImage.setDisplayMode(IJ.GRAYSCALE);

        return compositeImage;
    }

    public static ImagePlus[] separateRGB(ImagePlus image) {
        log("Separating Red Green and Blue...");

        ImagePlus[] splits = ChannelSplitter.split(image);

        for (ImagePlus split : splits){
            log("Showing split:" + split.getTitle());
            split.show();
        }

        return splits;
    }


    public static ImagePlus mergeRGB(ImagePlus[] rgb) {
        log("Merging RGB Stack...");
        RGBStackMerge rgbStackMerge = new RGBStackMerge();
        ImagePlus composite = rgbStackMerge.mergeHyperstacks(rgb, true);

        log("Showing composite: " + composite.getTitle());
        composite.show();

        return addScaleBar(composite);
    }

    public static ImagePlus makeSubstack(ImagePlus image) {
        setCurrentImage(image);
        log("Making substack...");

        SubstackMaker substackMaker = new SubstackMaker();
        substackMaker.run("");
        log("Substack created");

        return IJ.getImage();
    }

    public static ImagePlus project3D(ImagePlus image) {
        setCurrentImage(image);
        log("3D projecting...");

        Projector projector = new Projector();
        Macro.setOptions("projection=[Brightest Point] axis=Y-Axis slice=0.20 initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=100 interior=50 interpolate");
        projector.run("");
        Macro.setOptions(null);

        return IJ.getImage();
    }

    public static ImagePlus[] commonAnalysisSteps(ImagePlus image) {
        log("Starting common analysis steps...");

        image = adjustBrightnessContrast(image);
        image = cropImage(image);
        image = addScaleBar(image);
        return separateRGB(image);
    }

    /*
     * SAVE Methods
     */

    public static void save_tif(ImagePlus image, Path outpath, String suffix) {
        setCurrentImage(image);
        log("Saving tif...");

        image = image.flatten();

        String fileName = image.getTitle() + "_" + suffix;
        IJ.saveAs(image, "tif", outpath.resolve(fileName).toString() );
        image.changes = false;
        image.close();
    }

    public static void save_avi(ImagePlus image, Path outpath, String suffix, double frames) {
        setCurrentImage(image);
        log("Saving avi...");

        AVI_Writer writer = new AVI_Writer();

        String filePath = outpath.resolve(image.getTitle() + "_" + suffix + ".avi").toString();

        Macro.setOptions("compression=JPEG frame="+ frames + " save="+ filePath);
        writer.run(image.getProcessor());
        Macro.setOptions(null);

        image.close();
    }

}
