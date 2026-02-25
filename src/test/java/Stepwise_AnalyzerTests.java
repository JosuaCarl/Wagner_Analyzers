import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.LociImporter;
import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.Importer;
import loci.plugins.in.ImporterOptions;
import net.imagej.ImageJ;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Stepwise_AnalyzerTests {
    public static void main(String[] args) throws IOException, FormatException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        testSingleFile(Paths.get("src/test/resources/FluorescentCells.nd2").toAbsolutePath());
    }

    private static boolean testSingleFile(Path inFile) throws IOException, FormatException {

        // IJ.run("Bio-Formats Importer", "open=["+ inFile + "] color_mode=Default view=Hyperstack stack_order=XYCZT");
        LociImporter lociImporter = new LociImporter();
        Importer importer = new Importer(lociImporter);
        ImporterOptions options = importer.parseOptions("open=[" + inFile.toString() + "] color_mode=Default view=Hyperstack stack_order=XYCZT");
        ImportProcess importProcess = new ImportProcess(options);
        importProcess.execute();
        ImagePlusReader imagePlusReader = new ImagePlusReader(importProcess);
        ImagePlus image = imagePlusReader.openImagePlus()[0];
        image.show();

        Stepwise_Analyzer.processImage( image, Paths.get("out") );

        return true;
    }
}
