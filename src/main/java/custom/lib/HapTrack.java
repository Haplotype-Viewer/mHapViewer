package custom.lib;

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

import java.awt.*;


public class HapTrack extends AbstractTrack implements IGVEventObserver {
    public static HapTrack Instance;

    private static Logger log = Logger.getLogger(HapTrack.class);

    public HapTrack(String name) {
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
        final Genome currentGenome = GenomeManager.getInstance().getCurrentGenome();

        Chromosome chromosome = currentGenome.getChromosome(chr);
        int start = (int) referenceFrame.getOrigin();
        final int chromosomeLength = chromosome.getLength();

        int end = (int) referenceFrame.getEnd();
        int w = end - start;

        log.info("Repaint the frame with chr: " + chr + " " + "start: " + start + " " + "end: " + end);
    }

    @Override
    public void render(RenderContext context, Rectangle rect) {
        Graphics2D g = context.getGraphics2D("SEQUENCE");
    }

    @Override
    public int getHeight() {
        return 60;
    }
}
