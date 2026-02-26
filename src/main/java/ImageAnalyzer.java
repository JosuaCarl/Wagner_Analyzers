import ij.ImagePlus;

import java.nio.file.Path;

public interface ImageAnalyzer {
    void processImage(ImagePlus image, Path outFolder);
}
