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
import org.broad.igv.ui.FontManager;
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

    private int dX;

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

        dX = (int) (1.0 / locScale);

        Graphics2D g = context.getGraphics2D("SEQUENCE");

        int fontSize = (int) (dX * 0.8);
        Font f = FontManager.getFont(Font.BOLD, fontSize);
        g.setFont(f);

        for (CorrelationData cor : correlationDataArrayList) {
            int startIdx = cor.start - start;
            int endIdx = cor.end - start;


            float w = endIdx - startIdx;

            int pX1 = (int) ((startIdx + w / 2) / locScale);
            int pY1 = (int) (Math.sqrt(2) * (w / 2) / locScale) + 20;
            int h = (int) (Math.sqrt(2) * dX);

            g.setColor(new Color(cor.cor, 0, 0));

            Polygon polygon = new Polygon();
            polygon.addPoint(pX1, pY1);
            polygon.addPoint(pX1 + dX, pY1 - h);
            polygon.addPoint(pX1, pY1 - 2 * h);
            polygon.addPoint(pX1 - dX, pY1 - h);
            polygon.addPoint(pX1, pY1);
            g.fillPolygon(polygon);

            var chars = String.valueOf(Math.round(100 * cor.cor)).toCharArray();

            g.setColor(Color.white);
            g.drawChars(chars, 0, chars.length, (int) (pX1 - dX * 1.2 + fontSize), (int) (pY1 - h * 1.4 + fontSize));
        }
    }

    @Override
    public int getHeight() {
        if (correlationDataArrayList.size() > 0) {
            CorrelationData target = correlationDataArrayList.stream().max((a, b) -> Integer.compare(a.end - a.start, b.end - b.start)).get();
            return (int) (300 + Math.sqrt(2) * dX * (target.end - target.start));
        } else {
            return 300;
        }
    }
}
