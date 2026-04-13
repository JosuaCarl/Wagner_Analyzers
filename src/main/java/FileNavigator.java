import ij.IJ;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class for file navigation and scheduling.
 */
public class FileNavigator {
    ImageAnalyzer imageAnalyzer;

    FileNavigator(ImageAnalyzer imageAnalyzer) {
        this.imageAnalyzer = imageAnalyzer;
    }

    /**
     * A container class for a input path, output folder combination.
     */
    private class FileIO {
        public Path inPath;
        public Path outFolder;

        FileIO(Path inPath, Path outFolder) {
            this.inPath = inPath;
            this.outFolder = outFolder;
        }
    }

    /**
     * Collect all valid nested file paths.
     *
     * @param inFolder Starting folder to scan for files with correct suffix. Subfolders will also be scanned.
     * @param outFolder Starting output folder. Subfolders will be created to mirror the structure of the input folder.
     * @param fileSuffix Only files with this suffix will be selected.
     * @return A List of in-out path combinations.
     */
    public List<FileIO> collectFilePaths(Path inFolder, Path outFolder, String fileSuffix) {
        List<FileIO> filePaths = new ArrayList<FileIO>();

        // function to scan folders/subfolders/files to find files with correct suffix
        try (Stream<Path> entries = Files.list(inFolder)) {
            for(Path entry : entries.collect(Collectors.toList())) {
                if( Files.isDirectory(entry) && !entry.equals(outFolder)) {
                    Logger.log("Checking out folder: " + entry.toString());
                    Path newOutFolder = outFolder.resolve(entry.getFileName());
                    try {
                        Logger.log("Creating folder " + newOutFolder);
                        Files.createDirectory( newOutFolder );
                    } catch (IOException e) {
                        if (e instanceof FileAlreadyExistsException) {
                            Logger.log("Folder " + newOutFolder + " already exists.");
                        } else {
                            throw new RuntimeException(e);
                        }
                    }

                    processFolder(entry, newOutFolder, fileSuffix);
                }

                if( entry.getFileName().toString().endsWith(fileSuffix) ) {
                    filePaths.add( new FileIO(entry, outFolder) );
                }
            }
        } catch (IOException | InterruptedException e){
            throw new RuntimeException(e);
        }

        return filePaths;
    }

    /**
     * Process a single file or image if already loaded.
     *
     * @param inFile Path to the input file.
     * @param outFolder Output folder for derived files.
     * @param image Optional image that is already loaded from inFile.
     * @throws IOException
     */
    public void processFile(Path inFile, Path outFolder, ImagePlus image) throws IOException {
        Logger.log("Processing: " + inFile);

        // Import
        if (image == null) {
            image = IJ.openImage( inFile.toString() );
        }
        image.show();

        imageAnalyzer.processImage(image, outFolder);
    }


    /**
     * Function to analyze files in a folder and mirror the results into an output directory.
     *
     * @param inFolder Input folder
     * @param outFolder Output folder
     */
    public void processFolder(Path inFolder, Path outFolder, String fileSuffix) throws InterruptedException, IOException {
        List<FileIO> fileIOs = collectFilePaths(inFolder, outFolder, fileSuffix);
        ImagePlus currentImage;
        AtomicReference<ImagePlus> nextImage = new AtomicReference<>();

        for (int i = 1; i < fileIOs.size(); i++) {
            // Set new current
            if (nextImage.get() == null) {
                currentImage = IJ.openImage( fileIOs.get(i - 1).inPath.toString() );
            }
            else {
                currentImage = nextImage.get();
            }

            // Load next image in parallel
            FileIO fileIO = fileIOs.get(i);
            Thread imageThread = new Thread(
                () -> {
                    nextImage.set( IJ.openImage( fileIO.inPath.toString() ) );
                }
            );
            imageThread.start();

            // Process current image
            processFile(fileIO.inPath, fileIO.outFolder, currentImage);

            // Wait at most 5min for the next image to load
            imageThread.join(1000 * 60 * 5);
        }

        Logger.log("Finished processing " + inFolder + ".");
    }
}
