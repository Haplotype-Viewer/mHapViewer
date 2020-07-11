package custom.lib;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.tabix.TabixIndex;
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

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static org.broad.igv.prefs.Constants.MAX_SEQUENCE_RESOLUTION;


public class HapTrack extends AbstractTrack implements IGVEventObserver {
    // Index for all the hap track. Easy to call update
    public static ArrayList<HapTrack> Instances = new ArrayList<>();

    private static Logger log = Logger.getLogger(HapTrack.class);

    // Cached TCGA
    private Map<String, LoadedDataInterval<SequenceTrack.SeqCache>> loadedIntervalCache = new HashMap(200);

    // For small dataset,we can just cache all the hap files.
    public boolean isHapDataCached = false;

    // All loaded hap data in cache
    public ArrayList<HapData> cachedHapData;

    // Stream loading
    public TabixIndex tabixIndex;
    public File sourceFile;
    private FileInputStream fileInputStream;
    private BlockCompressedInputStream blockCompressedInputStream;

    // All the haps that is in the view range with a little expansion..
    private ArrayList<HapData> matchHapList;

    // Draw bar based on this information
    private final int barBeginY = 25;
    private final int barHeight = 80;
    private final int barMargin = 5;

    private final int GetBarBottom() {
        return barBeginY + barHeight + barMargin;
    }

    // Draw circle based on this information
    private final int circleRadius = 12;
    private final int circleMargin = 14;

    public HapTrack(String name) {
        super(null, name, name);
        setSortable(false);
        loadedIntervalCache = Collections.synchronizedMap(new HashMap<>());
        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
    }

    // A Utility for calculating mean
    public class MeanUtility {
        public int counter = 0;
        public int sum = 0;

        public void AddValue(int val) {
            counter += 1;
            sum += val;
        }

        public double GetMean() {
            return (1.0 * sum) / (1.0 * counter);
        }
    }

    @Override
    public void receiveEvent(Object event) {
        if (event instanceof FrameManager.ChangeEvent) {
            log.info("Reload the data due the event!");

            Collection<ReferenceFrame> frames = ((FrameManager.ChangeEvent) event).getFrames();

            for (ReferenceFrame f : frames) {
                load(f);
            }
        } else {
            log.info("Unknown event type: " + event.getClass());
        }
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

        if (w > 1000) {
            log.info("The view is too large");
            return;
        }

        // Expand the range a bit to avoid missing data.
        final int matchStart = start - 50, matchEnd = end + 50;

        matchHapList = new ArrayList<>();

        if (isHapDataCached) {
            cachedHapData.forEach(e -> {
                if (e.chr.equals(chr) && e.start >= matchStart && e.end <= matchEnd) {
                    matchHapList.add(e);
                }
            });
        } else {
            log.info("Stream request hap data...");

            List<Block> blockList = tabixIndex.getBlocks(chr, matchStart, matchEnd);

            try {
                fileInputStream = new FileInputStream(sourceFile);
                blockCompressedInputStream = new BlockCompressedInputStream(fileInputStream);

                for (Block block : blockList) {
                    blockCompressedInputStream.skip(block.getStartPosition());
                    while (blockCompressedInputStream.getPosition() <= block.getEndPosition()) {
                        String str = blockCompressedInputStream.readLine();
//                        log.info(str);

                        HapData hapData = CustomUtility.CreateHapFromString(str.split("[\\s\t]+"));
                        matchHapList.add(hapData);

//                        log.info(hapData.chr + " " + hapData.start + " " + hapData.end);
                    }
                }
            } catch (IOException exception) {
                log.info("Stream loading exception:" + exception.getMessage());
            }

            log.info(matchHapList.size() + " files have been loaded to MatchHap");
        }

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

//            log.info("Track:" + chr + " " + sequenceStart + " - " + sequenceEnd);

//            if (matchHapList != null) {
//                log.info("Match Data:" + matchHapList.size() + " / " + hapData.size());
//            }


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
            int perferedMinSize = 8;
            int fontSize = Math.min(untranslatedSequenceRect.height, Math.min(dX, 12));

            if (fontSize >= perferedMinSize) {
                Font f = FontManager.getFont(Font.BOLD, perferedMinSize);
                g.setFont(f);
            }

            double origin = context.getOrigin();
            int scale = Math.max(1, (int) context.getScale());

            int readIdx = 0;

            Map<Integer, MeanUtility> MeanDic = new HashMap<>();

            // Display HapData
            for (HapData hapData : matchHapList) {
                int anchor = 0;

                ArrayList<Integer> circleXList = new ArrayList<>();
                ArrayList<Integer> circleYList = new ArrayList<>();

                boolean beginPadding = false;
                boolean endPadding = false;

                // Refer the example in the sequence track and it start with start-1 (I don't know why but just do it!)
                for (int loc = hapData.start - 1; loc <= hapData.end; loc += scale) {
                    int idx = loc - sequenceStart;

                    // Avoid data and line missing and prevent overflow.
                    if (idx < 0) {
                        if (!beginPadding) {
                            beginPadding = true;
                            circleXList.add(0);
                            circleYList.add(yBase + GetBarBottom() + readIdx * circleMargin + circleRadius);
                        }

                        continue;
                    }

                    if (idx >= seq.length - 1) {
                        if (!endPadding) {
                            endPadding = true;
                            circleXList.add((int) (seq.length / locScale));
                            circleYList.add(yBase + GetBarBottom() + readIdx * circleMargin + circleRadius);
                        }

                        continue;
                    }

                    // Draw different point depending on the strand
                    int drawIdx = -1;

                    if (hapData.strand == Strand.NONE || hapData.strand == Strand.POSITIVE) {
                        if (Character.toLowerCase((char) seq[idx]) == 'c' && Character.toLowerCase((char) seq[idx + 1]) == 'g') {
                            drawIdx = idx;
                        }
                    } else if (hapData.strand == Strand.NEGATIVE) {
                        if (Character.toLowerCase((char) seq[idx]) == 'c' && Character.toLowerCase((char) seq[idx + 1]) == 'g') {
                            drawIdx = idx + 1;
                        }
                    }

                    if (drawIdx != -1) {
                        boolean state = hapData.states[anchor];

                        if (!MeanDic.containsKey(drawIdx)) {
                            MeanDic.put(drawIdx, new MeanUtility());
                        }

                        MeanDic.get(drawIdx).AddValue(state ? 1 : 0);

                        int pX0 = (int) ((drawIdx + sequenceStart - origin) / locScale);

                        circleXList.add(pX0);
                        circleYList.add(yBase + GetBarBottom() + readIdx * circleMargin + circleRadius);

                        g.setColor(Color.BLACK);

                        if (!state) {
                            // Draw White Circle!
                            if (fontSize >= perferedMinSize) {
                                drawOval(g, pX0, yBase + GetBarBottom() + readIdx * circleMargin, circleRadius, circleRadius);
                            }
                        } else {
                            // Draw Black Circle!
                            if (fontSize >= perferedMinSize) {
                                drawFillOval(g, pX0, yBase + GetBarBottom() + readIdx * circleMargin, circleRadius, circleRadius);
                            }
                        }
                        anchor++;
                    }
                }

                if (fontSize >= perferedMinSize) {
                    // Draw line to connect every circle with some interval
                    for (int i = 0; i < circleXList.size() - 1; i++) {
                        g.setColor(Color.BLACK);

                        g.drawLine(
                                circleXList.get(i) + (int) (circleRadius * 1.5),
                                circleYList.get(i),
                                circleXList.get(i + 1) + (int) (circleRadius * 0.5),
                                circleYList.get(i + 1)
                        );
                    }
                }

                readIdx += 1;
            }

            // Draw Bar
            g.setColor(Color.BLACK);
            g.drawLine(0, barBeginY, (int) (seq.length / locScale), barBeginY);
            g.drawLine(0, barBeginY + barHeight, (int) (seq.length / locScale), barBeginY + barHeight);

            for (int id : MeanDic.keySet()) {
                int pX0 = (int) ((id + sequenceStart - origin) / locScale);
                double mean = MeanDic.get(id).GetMean();

                if (fontSize >= perferedMinSize) {
                    String str = "";

                    if (mean == 0) {
                        str = "0";
                    } else if (mean == 1) {
                        str = "1";
                    } else {
                        str = String.format("%.2f", mean);
                    }

                    int s = (int) (1.0 / locScale);

                    drawCenteredText(g, str.toCharArray(), pX0, 10, s, s);
                }

                if (mean > 0) {
                    // Add one pixel to the width to make bar better
                    drawRect(g, pX0 + 6, (int) (barBeginY + barHeight * (1 - mean)), 12, (int) (1 + barHeight * mean));
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
        if (matchHapList == null) {
            return 300;
        }

        return Math.max(300, GetBarBottom() + matchHapList.size() * circleMargin + circleMargin);
    }

    private void drawRect(Graphics2D g, int x, int y, int w, int h) {
        g.fillRect(x, y, w, h);
    }

    private void drawOval(Graphics2D g, int x, int y, int w, int h) {
        int X = x + w / 2;
        int Y = y + h / 2;

        g.drawOval(X, Y, w, h);
    }

    private void drawFillOval(Graphics2D g, int x, int y, int w, int h) {
        int X = x + w / 2;
        int Y = y + h / 2;

        g.fillOval(X, Y, w, h);
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
        int msgY = y + h / 2 + ascent / 2 - descent / 2;

        g.drawChars(chars, 0, chars.length, msgX, msgY);
    }
}
