import ij.*;
import ij.plugin.*;
import ij.plugin.filter.AVI_Writer;
import ij.plugin.frame.ContrastAdjuster;

import java.awt.image.PixelGrabber;
import java.nio.file.Path;

public class ImageChanger {
    public static void setCurrentImage(ImagePlus image) {
        Logger.log("Setting current image to: " + image.getTitle());
        WindowManager.setCurrentWindow(image.getWindow());
    }

    public static ImagePlus adjustBrightnessContrast(ImagePlus image) {
        setCurrentImage(image);
        Logger.log("Adjusting Brightness & Contrast...");

        // Adjust Brightness/Contrast
        ContrastAdjuster contrastAdjuster = new ContrastAdjuster();
        contrastAdjuster.run("");
        WindowHelpers.waitUntilClose(contrastAdjuster);

        return IJ.getImage();
    }

    public static ImagePlus crop(ImagePlus image) {
        setCurrentImage(image);
        Logger.log("Cropping image...");

        SpecifyROI_Interactively specifyRoiInteractively = new SpecifyROI_Interactively();
        specifyRoiInteractively.runOnImage(image);

        Resizer resizer = new Resizer();
        resizer.run("crop");

        return IJ.getImage();
    }

    public static ImagePlus addScaleBar(ImagePlus image) {
        setCurrentImage(image);
        Logger.log("Adding scale bar...");

        ScaleBar scaleBar = new ScaleBar();
        Macro.setOptions("width=10 height=5 thickness=5 font=0 hide overlay");
        scaleBar.run("");
        Macro.setOptions(null);

        return IJ.getImage();
    }

    public static ImagePlus toRGB(ImagePlus image) {
        if (!image.isComposite()) {
            Logger.log("Converting image to RGB...");
            RGBStackConverter.convertToRGB(image);
        }
        return image;
    }

    public static ImagePlus toGrey(ImagePlus image) {
        Logger.log("Converting image to Grey...");

        CompositeImage compositeImage = new CompositeImage(image);
        compositeImage.setDisplayMode(IJ.GRAYSCALE);

        return compositeImage;
    }

    public static String whichColor(ImagePlus image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int numPixels = width * height;

        int[] pixels = new int[numPixels];
        PixelGrabber pg = new PixelGrabber(image.getImage(), 0, 0, width, height, pixels, 0, 1);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int[] rgbValues = new int[3];
        for (int i = 0; i < numPixels; i++) {
            int pixel = pixels[i];
            int red = (pixel & 16711680) >> 16;
            int green = (pixel & '\uff00') >> 8;
            int blue = pixel & 255;

            if (red > green &&  red > blue) {
                rgbValues[0] += 1;
            } else if (green > blue) {
                rgbValues[1] += 1;
            } else if (blue > red) {
                rgbValues[2] += 1;
            }
        }
        Logger.log(rgbValues[0] + " " + rgbValues[1] + " " + rgbValues[2]);
        if (rgbValues[0] > rgbValues[1] && rgbValues[0] > rgbValues[2]) {
            return "red";
        } else if (rgbValues[1] > rgbValues[2]) {
            return "green";
        } else if (rgbValues[2] > rgbValues[0]) {
            return "blue";
        }  else {
            return "unknown";
        }
    }

    public static ImagePlus[] separateRGB(ImagePlus image) {
        Logger.log("Separating Red Green and Blue...");

        ImagePlus[] splits = ChannelSplitter.split(image);

        ImagePlus[] rgbSplits = new ImagePlus[3];
        for (ImagePlus split : splits) {
            switch (whichColor(split)) {
                case "red": rgbSplits[0] = split;
                case "green": rgbSplits[1] = split;
                case "blue": rgbSplits[2] = split;
            }
        }

        for (ImagePlus rgbSplit : rgbSplits){
            Logger.log("Showing split:" + rgbSplit.getTitle());
            rgbSplit.show();
        }

        return rgbSplits;
    }

    public static ImagePlus mergeRGB(ImagePlus[] rgb) {
        Logger.log("Merging RGB Stack...");
        RGBStackMerge rgbStackMerge = new RGBStackMerge();
        ImagePlus composite = rgbStackMerge.mergeHyperstacks(rgb, true);

        Logger.log("Showing composite: " + composite.getTitle());
        composite.show();

        return addScaleBar(composite);
    }

    public static ImagePlus makeSubstack(ImagePlus image) {
        setCurrentImage(image);
        Logger.log("Making substack...");

        SubstackMaker substackMaker = new SubstackMaker();
        substackMaker.run("");
        Logger.log("Substack created");

        return IJ.getImage();
    }

    public static ImagePlus project3D(ImagePlus image) {
        setCurrentImage(image);
        Logger.log("3D projecting...");

        Projector projector = new Projector();
        Macro.setOptions("projection=[Brightest Point] axis=Y-Axis slice=0.20 initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=100 interior=50 interpolate");
        projector.run("");
        Macro.setOptions(null);

        return IJ.getImage();
    }


    //
    // Save methods
    //

    public static void save_tif(ImagePlus image, Path outpath, String suffix) {
        setCurrentImage(image);
        Logger.log("Saving tif...");

        image = image.flatten();

        String fileName = image.getTitle() + "_" + suffix;
        IJ.saveAs(image, "tif", outpath.resolve(fileName).toString() );
        image.changes = false;
        image.close();
    }

    public static void save_avi(ImagePlus image, Path outpath, String suffix, double frames) {
        setCurrentImage(image);
        Logger.log("Saving avi...");

        AVI_Writer writer = new AVI_Writer();

        String filePath = outpath.resolve(image.getTitle() + "_" + suffix + ".avi").toString();

        Macro.setOptions("compression=JPEG frame="+ frames + " save="+ filePath);
        writer.run(image.getProcessor());
        Macro.setOptions(null);

        image.close();
    }
}
