import ij.IJ;
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
    Stepwise_Analyzer stepwiseAnalyzer = new Stepwise_Analyzer();

    public static void main(String[] args) throws IOException, FormatException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        Stepwise_AnalyzerTests stepwiseAnalyzerTests = new Stepwise_AnalyzerTests();
        stepwiseAnalyzerTests.testSingleFile(
                Paths.get("src/test/resources/Rat_Hippocampal_Neuron.tif").toAbsolutePath(),
                "Default"
        );

    }

    private ImagePlus[] bioImportImage(Path inFile, String colorMode) throws IOException, FormatException {

        ImagePlus[] image = {IJ.openImage(inFile.toString())};
        if (image[0] == null) {
            // IJ.run("Bio-Formats Importer", "open=["+ inFile + "] color_mode=Default view=Hyperstack stack_order=XYCZT");
            LociImporter lociImporter = new LociImporter();
            Importer importer = new Importer(lociImporter);
            ImporterOptions options = importer.parseOptions("open=[" + inFile.toString() + "] color_mode=" + colorMode + " view=Hyperstack stack_order=XYCZT");
            ImportProcess importProcess = new ImportProcess(options);
            importProcess.execute();
            ImagePlusReader imagePlusReader = new ImagePlusReader(importProcess);

            image = imagePlusReader.openImagePlus();
        }
        return image;
    }

    private boolean testSingleFile(Path inFile, String colorMode) throws IOException, FormatException {
        ImagePlus image = bioImportImage(inFile, colorMode)[0];
        image.show();

        stepwiseAnalyzer.processImage( image, Paths.get("src/test/resources/out").toAbsolutePath() );

        return true;
    }
}
