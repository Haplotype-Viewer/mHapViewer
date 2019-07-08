package org.broad.igv.bedpe;

import org.broad.igv.track.RenderContext;

import java.awt.*;
import java.util.List;

public class PEBlockRenderer implements BedPERenderer {

    InteractionTrack track;
    int rowHeight = 10;

    public PEBlockRenderer(InteractionTrack track) {
        this.track = track;
    }

    @Override
    public void render(List<BedPE> features, RenderContext context, Rectangle trackRectangle) {

        Graphics2D g = null;

        try {
            g = (Graphics2D) context.getGraphics().create();
            double origin = context.getOrigin();
            double locScale = context.getScale();
            Color trackColor = track.getColor();

            final int blockY = trackRectangle.y + trackRectangle.height - rowHeight;

            for (BedPE bedPE : features) {

                Color fcolor = bedPE.getColor() == null ? trackColor : bedPE.getColor();
                if (fcolor != null) {
                    g.setColor(fcolor);
                }


                if (bedPE.isSameChr()) {
                    BedPEFeature feature = bedPE.get();
                    int ps1 = (int) ((feature.start1 - origin) / locScale);
                    int pe1 = (int) ((feature.end1 - origin) / locScale);
                    if (pe1 >= trackRectangle.getX() && ps1 <= trackRectangle.getMaxX()) {
                        drawBlock(ps1, pe1, blockY, g);
                    }

                    int ps2 = (int) ((feature.start2 - origin) / locScale);
                    int pe2 = (int) ((feature.end2 - origin) / locScale);
                    if (pe2 >= trackRectangle.getX() && ps2 <= trackRectangle.getMaxX()) {
                        drawBlock(ps2, pe2, blockY, g);
                    }

                    // connecting line
//                    if (feature.isSameChr()) {
//                        int pl1 = Math.min(pe1, pe2);
//                        int pl2 = Math.max(ps1, ps2);
//                        final int connectorY = blockY + rowHeight / 2;
//                        g.drawLine(pl1, connectorY, pl2, connectorY);
//                    }
                } else {
                    int ps1 = (int) ((bedPE.getStart() - origin) / locScale);
                    int pe1 = (int) ((bedPE.getEnd() - origin) / locScale);
                    if (pe1 >= trackRectangle.getX() && ps1 <= trackRectangle.getMaxX()) {
                        drawBlock(ps1, pe1, blockY, g);
                    }

                }
            }
        } finally {
            if (g != null) g.dispose();
        }
    }

    private void drawBlock(int ps1, int pe1, int blockY, Graphics2D g) {
        // Trim width if possible to insure a gap between blocks
        int w1 = Math.max(1, pe1 - ps1);
        if (w1 > 3) w1--;
        else if (w1 > 5) {
            w1 -= 2;
            ps1++;
        }
        g.fillRect(ps1, blockY, w1, rowHeight);
    }

}
