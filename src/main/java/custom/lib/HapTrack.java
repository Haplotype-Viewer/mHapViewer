package custom.lib;

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.event.IGVEventObserver;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.Strand;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.AbstractTrack;
import org.broad.igv.track.LoadedDataInterval;
import org.broad.igv.track.RenderContext;
import org.broad.igv.track.SequenceTrack;
import org.broad.igv.ui.FontManager;
import org.broad.igv.ui.UIConstants;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.UIUtilities;

import java.awt.*;
import java.io.Console;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.broad.igv.prefs.Constants.MAX_SEQUENCE_RESOLUTION;


public class HapTrack extends AbstractTrack implements IGVEventObserver {
    public static ArrayList<HapTrack> Instances = new ArrayList<>();

    private static Logger log = Logger.getLogger(HapTrack.class);

    private Map<String, LoadedDataInterval<SequenceTrack.SeqCache>> loadedIntervalCache = new HashMap(200);

    public ArrayList<HapData> hapData;

    public boolean allCached = false;

    private ArrayList<HapData> matchHapList;

    public HapTrack(String name) {
        super(null, name, name);
        setSortable(false);
        loadedIntervalCache = Collections.synchronizedMap(new HashMap<>());
        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
    }

    @Override
    public void receiveEvent(Object event) {

    }

    @Override
    public boolean isReadyToPaint(ReferenceFrame frame) {
        int resolutionThreshold = PreferencesManager.getPreferences().getAsInt(MAX_SEQUENCE_RESOLUTION);
        boolean visible = frame.getScale() < resolutionThreshold && !frame.getChrName().equals(Globals.CHR_ALL);

        if (!visible) {
            return true; // Nothing to paint
        } else {
            LoadedDataInterval<SequenceTrack.SeqCache> interval = loadedIntervalCache.get(frame.getName());
            return interval != null && interval.contains(frame);
        }
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

        // Expand a bit for panning and AA caluclation
        start = Math.max(0, start - w / 2 + 2);
        end = Math.min(end + w / 2 + 2, chromosomeLength);

        Genome genome = currentGenome;
        String sequence = new String(genome.getSequence(chr, start, end));


        int mod = start % 3;
        int n1 = normalize3(3 - mod);
        int n2 = normalize3(n1 + 1);
        int n3 = normalize3(n2 + 1);

        // Now trim sequence to prevent dangling AAs
        int deltaStart = start == 0 ? 0 : 2;
        int deltaEnd = end == chromosomeLength ? 0 : 02;
        start += deltaStart;
        end -= deltaEnd;
        final int len = sequence.length();
        byte[] seq = sequence.substring(deltaStart, len - deltaEnd).getBytes();

        SequenceTrack.SeqCache cache = new SequenceTrack.SeqCache(start, seq);
        cache.refreshAminoAcids();
        loadedIntervalCache.put(referenceFrame.getName(), new LoadedDataInterval<>(chr, start, end, cache));

        final int matchStart = start, matchEnd = end;

        matchHapList = new ArrayList<>();


        hapData.forEach(e -> {
            if (e.chr.equals(chr) && e.start >= matchStart && e.end <= matchEnd) {
                matchHapList.add(e);
            }
        });

        log.info("Repaint the frame from " + chr + " : " + matchStart + " - " + matchEnd);
    }

    private static int normalize3(int n) {
        return n == 3 ? 0 : n;
    }

    @Override
    public void render(RenderContext context, Rectangle rect) {
        int resolutionThreshold = PreferencesManager.getPreferences().getAsInt(MAX_SEQUENCE_RESOLUTION);

        final String frameName = context.getReferenceFrame().getName();

        if (context.getScale() >= resolutionThreshold) {
            // Zoomed out too far to see sequences.  This can happen when in gene list view and one of the frames
            // is zoomed in but others are not
            context.getGraphic2DForColor(UIConstants.LIGHT_GREY).fill(rect);
        } else {
            LoadedDataInterval<SequenceTrack.SeqCache> sequenceInterval = loadedIntervalCache.get(frameName);

            double locScale = context.getScale();
            int start = (int) context.getOrigin();
            int end = (int) (start + rect.width * locScale) + 1;

            SequenceTrack.SeqCache cache = sequenceInterval.getFeatures();
            byte[] seq = cache.seq;

            int sequenceStart = cache.start;
            int sequenceEnd = sequenceInterval.range.getEnd();

            String chr = sequenceInterval.range.getChr();

            log.info("Track:" + chr + " " + sequenceStart + " - " + sequenceEnd);

            if (matchHapList != null) {
                log.info("Match Data:" + matchHapList.size() + " / " + hapData.size());
            }


            if (end <= sequenceStart) return;

            //The combined height of sequence and (optionally) colorspace bands
            int untranslatedSequenceHeight = (int) rect.getHeight();


            //Rectangle containing the sequence and (optionally) colorspace bands
            Rectangle untranslatedSequenceRect = new Rectangle(rect.x, rect.y,
                    (int) rect.getWidth(), untranslatedSequenceHeight);

            int yBase = untranslatedSequenceRect.y + 2;
            int dY = untranslatedSequenceRect.height - 4;
            int dX = (int) (1.0 / locScale);
            // Create a graphics to use
            Graphics2D g = context.getGraphics2D("SEQUENCE");

            //dhmay adding check for adequate track height
            int fontSize = Math.min(untranslatedSequenceRect.height, Math.min(dX, 12));

            if (fontSize >= 8) {
                Font f = FontManager.getFont(Font.BOLD, fontSize);
                g.setFont(f);
            }

            double origin = context.getOrigin();
            int scale = Math.max(1, (int) context.getScale());

            // Display HapData
            for (HapData hapData : matchHapList) {
                int anchor = 0;

//                for (int x = idx; x < idx + 20; x++) {
//                    log.info(x + " : " + String.valueOf((char) seq[x]));
//                }

                // Refer the example in the sequence track and it start with start-1 (I don't know why but just do it!)
                for (int loc = hapData.start - 1; loc <= hapData.end; loc += scale) {
                    if (hapData.strand == Strand.NONE || hapData.strand == Strand.POSITIVE) {
                        int idx = loc - sequenceStart;

                        if (Character.toLowerCase((char) seq[idx]) == 'c' && Character.toLowerCase((char) seq[idx + 1]) == 'g') {
                            int state = hapData.states[anchor];
                            int pX0 = (int) ((loc - origin) / locScale);

                            g.setColor(Color.RED);

                            if (fontSize >= 8) {
                                if (state == 0) {
                                    drawCenteredText(g, new char[]{'0'}, pX0, yBase + 2, dX, dY - 2);
                                } else {
                                    drawCenteredText(g, new char[]{'1'}, pX0, yBase + 2, dX, dY - 2);
                                }
                            } else {
                                // font is too small to fill
                                int bw = Math.max(1, dX - 1);
                                g.fillRect(pX0, yBase, bw, dY);
                            }

                            log.info(pX0);

                            anchor++;
                        }
                    }
//                    } else {
//                        if (seq[idx] == 'g' && seq[idx + 1] == 'c') {
//                            int state = hapData.states[anchor];
//                            anchor++;
//                        }
//                    }
                }
            }

//            byte[] seqCS = null;
//
//            if (seq != null && seq.length > 0) {
//                int yBase = untranslatedSequenceRect.y + 2;
//                int yCS = untranslatedSequenceRect.y + 2;
//                int dY = untranslatedSequenceRect.height - 4;
//                int dX = (int) (1.0 / locScale);
//                // Create a graphics to use
//                Graphics2D g = context.getGraphics2D("SEQUENCE");
//
//                //dhmay adding check for adequate track height
//                int fontSize = Math.min(untranslatedSequenceRect.height, Math.min(dX, 12));
//                if (fontSize >= 8) {
//                    Font f = FontManager.getFont(Font.BOLD, fontSize);
//                    g.setFont(f);
//                }
//
//                // Loop through base pair coordinates
//                int lastVisibleNucleotideEnd = Math.min(end, seq.length + sequenceStart);
//                int lastPx0 = -1;
//                int scale = Math.max(1, (int) context.getScale());
//                double origin = context.getOrigin();
//                for (int loc = start - 1; loc < lastVisibleNucleotideEnd; loc += scale) {
//                    int pX0 = (int) ((loc - origin) / locScale);
//                    // Skip drawing if we haven't advanced 1 pixel past last nt.  Low zoom
//                    if (pX0 > lastPx0) {
//                        lastPx0 = pX0;
//
//                        int idx = loc - sequenceStart;
//                        if (idx < 0) continue;
//                        if (idx >= seq.length) break;
//
//                        char c = ' ';
//
//                        if (Character.toLowerCase(seq[idx]) == 'c' && Character.toLowerCase(seq[idx + 1]) == 'g') {
//                            c = '1';
//                        }
//
//                        Color color = Color.BLUE;
//                        if (fontSize >= 8) {
//                            g.setColor(color);
//                            drawCenteredText(g, new char[]{c}, pX0, yBase + 2, dX, dY - 2);
//                        } else {
//                            // font is too small to fill
//                            int bw = Math.max(1, dX - 1);
//                            if (color != null) {
//                                g.setColor(color);
//                                g.fillRect(pX0, yBase, bw, dY);
//                            }
//                        }
//                    }
//                }
//            }
        }
    }

    @Override
    public int getHeight() {
        return 200;
    }

    private void drawCenteredText(Graphics2D g, char[] chars, int x, int y, int w, int h) {
        // Get measures needed to center the message
        FontMetrics fm = g.getFontMetrics();

        // How many pixels wide is the string
        int msg_width = fm.charsWidth(chars, 0, 1);

        // How far above the baseline can the font go?
        int ascent = fm.getMaxAscent();

        // How far below the baseline?
        int descent = fm.getMaxDescent();

        // Use the string width to find the starting point
        int msgX = x + w / 2 - msg_width / 2;

        // Use the vertical height of this font to find
        // the vertical starting coordinate
        int msgY = y + h / 2 - descent / 2 + ascent / 2;

        g.drawChars(chars, 0, 1, msgX, msgY);
    }
}
