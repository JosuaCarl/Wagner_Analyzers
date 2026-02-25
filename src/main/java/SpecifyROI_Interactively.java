import ij.plugin.PlugIn;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import ij.*;
import ij.gui.*;

/**
 * Open a non-blocking window to interact with the ROI by hand and specification.
 * In large parts inspired by (Specify_ROI_Interactively)[https://imagej.net/ij/plugins/specify-roi-interactively/Specify_ROI_Interactively.java].
 */
public class SpecifyROI_Interactively implements PlugIn, DialogListener, RoiListener, MouseListener {
    private Roi previousRoi;
    private ImagePlus image;

    private int x, y, width, height;
    private boolean oval;
    private boolean centered;
    private boolean squared;

    private NonBlockingGenericDialog dialog;

    public void run(String arg) {
        runOnImage(IJ.getImage());
    }

    public void runOnImage(ImagePlus newImage) {
        image = newImage;
        previousRoi = image.getRoi();
        if (previousRoi == null) {
            width = image.getWidth() / 2;
            height = image.getHeight() / 2;
            x = width / 2;
            y = height / 2;
            Roi newRoi = new Roi(x, y, width, height);
            image.setRoi(newRoi);
        } else {
            oval = previousRoi.getType() == Roi.OVAL;
            squared = previousRoi.getBounds().x == previousRoi.getBounds().y;
        }

        image.getWindow().getCanvas().addMouseListener(this);
        Roi.addRoiListener(this);
        showDialog();
    }

    void showDialog() {
        int w = image.getWidth();
        int h = image.getHeight();

        dialog = new NonBlockingGenericDialog("Specify ROI");
        dialog.addSlider("X:", 0, w, x);
        dialog.addSlider("Y:", 0, h, y);
        dialog.addSlider("Width:", 0, w, width);
        dialog.addSlider("Height:", 0, h, height);
        dialog.addCheckbox("Oval", oval);
        dialog.addCheckbox("Centered", centered);
        dialog.addCheckbox("Squared", squared);
        dialog.addDialogListener(this);
        dialog.showDialog();

        if (dialog.wasCanceled()) {
            if (previousRoi==null)
                image.deleteRoi();
            else // restore initial ROI when cancelled
                image.setRoi(previousRoi);
        }
    }

    void drawRoi() {
        Roi roi;
        if (oval)
            roi = new OvalRoi(x, y, width, height);
        else
            roi = new Roi(x, y, width, height);

        image.setRoi(roi);
        previousRoi = roi;
    }

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        x = (int) gd.getNextNumber();
        y = (int) gd.getNextNumber();
        width = (int) gd.getNextNumber();
        height = (int) gd.getNextNumber();
        oval = gd.getNextBoolean();
        centered = gd.getNextBoolean();
        squared = gd.getNextBoolean();

        if (squared) {
            int avg = (width + height) / 2;
            width = avg;
            height = avg;
        }
        if (centered) {
            x = (image.getWidth() / 2) - (width / 2);
            y = (image.getHeight() / 2) - (height / 2);
        }

        if (gd.invalidNumber()) {
            return false;
        }
        else {
            drawRoi();
            updateDialog();
            return true;
        }
    }

    public void roiModified(ImagePlus image, int modificationType) {
        Roi roi = image.getRoi();
        Rectangle r = roi.getBounds();
        x = r.x;
        y = r.y;
        width = r.width;
        height = r.height;
    }

    private void updateDialog() {
        Object[] values = {x, y, width, height};
        Vector<Scrollbar> sliders = dialog.getSliders();
        Vector<TextField> numbers = dialog.getNumericFields();

        for (int i = 0; i < values.length; i++) {
            Scrollbar slider = sliders.get(i);
            TextField number = numbers.get(i);
            Object value = values[i];
            String strValue = value.toString();

            if (!strValue.equals(number.getText())) {
                slider.setValue(i);
                number.setText(strValue);
            }
        }
    }

    public void mouseClicked(MouseEvent e) {}

    public void mousePressed(MouseEvent e) {}

    public void mouseReleased(MouseEvent e) {
        updateDialog();
    }

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}
}
