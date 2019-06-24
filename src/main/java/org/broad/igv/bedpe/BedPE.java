package org.broad.igv.bedpe;

import org.broad.igv.feature.Locatable;

import java.awt.*;

public interface BedPE extends Locatable {

    BedPEFeature get();

    double getScore();

    boolean isSameChr();

    void setRow(int row);

    int getRow();

    Color getColor();

    int getThickness();
}
