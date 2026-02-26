import ij.IJ;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileNavigator {
    ImageAnalyzer imageAnalyzer;

    FileNavigator(ImageAnalyzer imageAnalyzer) {
        this.imageAnalyzer = imageAnalyzer;
    }

    /**
     * Function to analyze files in a folder and mirror the results into an output directory.
     *
     * @param inFolder Input folder
     * @param outFolder Output folder
     */
    public void processFolder(Path inFolder, Path outFolder, String fileSuffix) {
        // function to scan folders/subfolders/files to find files with correct suffix
        try (Stream<Path> entries = Files.list(inFolder)) {
            for(Path entry : entries.collect(Collectors.toList())) {
                Logger.log("Checking out folder: " + entry.toString());
                if( Files.isDirectory(entry) && !entry.equals(outFolder)) {
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
                    processFile(entry, outFolder);
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }

        Logger.log("Finished processing.");
    }

    public void processFile(Path inFile, Path outFolder) throws IOException {
        Logger.log("Processing: " + inFile);

        // Import
        ImagePlus image = IJ.openImage( inFile.toString() );
        image.show();

        imageAnalyzer.processImage(image, outFolder);
    }
}
