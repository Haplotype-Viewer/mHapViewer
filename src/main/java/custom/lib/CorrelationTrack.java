package custom.lib;

import com.jidesoft.utils.CachedArrayList;
import htsjdk.tribble.readers.TabixReader;
import org.apache.log4j.Logger;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.event.IGVEventObserver;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.track.AbstractTrack;
import org.broad.igv.track.RenderContext;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.ReferenceFrame;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class CorrelationTrack extends AbstractTrack implements IGVEventObserver {
    public static ArrayList<CorrelationTrack> Instances = new ArrayList<>();

    public TabixReader tabixReader;

    private static Logger log = Logger.getLogger(HapTrack.class);

    private ArrayList<CorrelationData> correlationDataArrayList = new ArrayList<>();

    public CorrelationTrack(String name) {
        super(null, name, name);
        setSortable(false);
        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
    }

    @Override
    public void receiveEvent(Object event) {

    }

    @Override
    public boolean isReadyToPaint(ReferenceFrame frame) {
        return true;
    }

    @Override
    public void load(ReferenceFrame referenceFrame) {
        String chr = referenceFrame.getChrName();

        int start = (int) referenceFrame.getOrigin();
        int end = (int) referenceFrame.getEnd();

        int w = end - start;

        if (w > 3000) {
            log.info("View range is too large");
        }

        int matchStart = start - w / 2;
        int matchEnd = end + w / 2;

        log.info("Stream request correlation data... Starting from:" + matchStart + " to " + matchEnd + " in " + chr);

        TabixReader.Iterator it = tabixReader.query(chr, matchStart, matchEnd);
        correlationDataArrayList.clear();

        while (true) {
            try {
                String str = it.next();

                if (str == null) {
                    break;
                }
                CorrelationData correlationData = CustomUtility.CreateCorrelationFromString(str.split("[\\s\t]+"));
                correlationDataArrayList.add(correlationData);
            } catch (IOException exception) {
                exception.printStackTrace();
                JOptionPane.showConfirmDialog(null, "Failed to load .tbi file! You should put .tbi file in the same folder.", "Exception", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void render(RenderContext context, Rectangle rect) {
        double locScale = context.getScale();

        int start = (int) context.getOrigin();
        int end = (int) (start + rect.width * locScale) + 1;

        Graphics2D g = context.getGraphics2D("SEQUENCE");

        for (CorrelationData cor : correlationDataArrayList) {
            log.info(cor.start + " " + cor.end + " " + cor.cor);

            int startIdx = cor.start - start;
            int endIdx = cor.end - start;

            int middleIdx = cor.start + (cor.end - cor.start + 1) / 2 - start;

            int dX = (int) (1.0 / locScale);
            int pX0 = (int) (startIdx / locScale);
            int pX1 = (int) (middleIdx / locScale);
            int pX2 = (int) (endIdx / locScale);

            int w = 0;

            if ((cor.end - cor.start) % 2 == 0) {
                w = (int) ((cor.end - cor.start) / 2 / locScale);
            } else {
                w = (int) ((cor.end - cor.start + 1) / 2 / locScale);
            }

            g.setColor(new Color(cor.cor, 0, 0));

            Polygon polygon = new Polygon();
            polygon.addPoint(pX1, w);
            polygon.addPoint(pX1 + dX, w + dX);
            polygon.addPoint(pX1, w + dX * 2);
            polygon.addPoint(pX1 - dX, w + dX);
            polygon.addPoint(pX1, w);

            g.fillPolygon(polygon);

            var chars = String.valueOf(Math.round(100 * cor.cor)).toCharArray();

            g.setColor(Color.BLACK);
            g.drawChars(chars, 0, chars.length, pX1 - dX / 2, w / 2 + dX);
        }
    }

    @Override
    public int getHeight() {
        return 300;
    }
}
