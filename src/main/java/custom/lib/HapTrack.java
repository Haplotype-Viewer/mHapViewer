package custom.lib;

import htsjdk.tribble.readers.TabixReader;
import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.event.IGVEventObserver;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.Strand;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.*;
import org.broad.igv.ui.FontManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.UIConstants;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.IGVPopupMenu;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.UIUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

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
    public TabixReader tabixReader;

    // All the haps that is in the view range with a little expansion..
    private ArrayList<HapData> matchHapList;
    private List<HapData> displayableHapList;

    // Draw bar based on this information
    private Boolean isShowBar = true;

    private final int barBeginY = 25;
    private int barHeight = 80;
    private int barWidth = 12;
    private int barMargin = 5;

    private final int GetBarBottom() {
        if (isShowBar) {
            return barBeginY + barHeight + barMargin;
        } else {
            return barMargin;
        }
    }

    // Draw circle based on this information
    private int circleRadius = 12;
    private int circleMargin = 14;

    private Color PositiveStrandColor = Color.BLACK;
    private Color NegativeStrandColor = Color.BLACK;
    private Color UnknownStrandColor = Color.BLACK;

    private boolean isCombineCpGBar = false;

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

    public class AlignInfo {
        public AlignInfo(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int start;
        public int end;
    }

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
    public IGVPopupMenu getPopupMenu(final TrackClickEvent te) {
        IGVPopupMenu menu = new IGVPopupMenu();

        final JCheckBoxMenuItem refreshItem = new JCheckBoxMenuItem("Refresh");
        refreshItem.setSelected(false);
        refreshItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                repaint();
            }
        });
        menu.add(refreshItem);

        final JCheckBoxMenuItem toggleAverageSizeItem = new JCheckBoxMenuItem("Toggle Combine CpG Bar: " + isCombineCpGBar);
        toggleAverageSizeItem.setSelected(false);
        toggleAverageSizeItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                isCombineCpGBar = !isCombineCpGBar;
            }
        });
        menu.add(toggleAverageSizeItem);

        final JCheckBoxMenuItem toggleBarItem = new JCheckBoxMenuItem("Toggle Bar: " + isShowBar);
        toggleBarItem.setSelected(false);
        toggleBarItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                isShowBar = !isShowBar;
            }
        });
        menu.add(toggleBarItem);

        final JMenuItem circleSizeItem = new JMenuItem("Set Circle Size...");
        circleSizeItem.addActionListener(e -> {
            String t = MessageUtils.showInputDialog("Circle Size", String.valueOf(circleRadius));
            if (t != null) {
                try {
                    int value = Integer.parseInt(t);
                    circleRadius = value;
                    circleMargin = Math.max(circleMargin, circleRadius);
                    repaint();
                } catch (NumberFormatException e1) {
                    MessageUtils.showErrorMessage("Circle size must be an integer", e1);
                }
            }
        });
        menu.add(circleSizeItem);

        final JMenuItem circleMarginItem = new JMenuItem("Set Circle Margin...");
        circleMarginItem.addActionListener(e -> {
            String t = MessageUtils.showInputDialog("Circle Margin", String.valueOf(circleMargin));
            if (t != null) {
                try {
                    int value = Integer.parseInt(t);
                    circleMargin = Math.max(circleRadius, value);
                    repaint();
                } catch (NumberFormatException e1) {
                    MessageUtils.showErrorMessage("Circle margin must be an integer", e1);
                }
            }
        });
        menu.add(circleMarginItem);

        final JMenuItem barHeightItem = new JMenuItem("Set Bar Height...");
        barHeightItem.addActionListener(e -> {
            String t = MessageUtils.showInputDialog("Bar Height", String.valueOf(barHeight));
            if (t != null) {
                try {
                    int value = Integer.parseInt(t);
                    barHeight = value;
                    repaint();
                } catch (NumberFormatException e1) {
                    MessageUtils.showErrorMessage("Bar height must be an integer", e1);
                }
            }
        });
        menu.add(barHeightItem);

        final JMenuItem barWidthItem = new JMenuItem("Set Bar Width...");
        barWidthItem.addActionListener(e -> {
            String t = MessageUtils.showInputDialog("Bar Width", String.valueOf(barWidth));
            if (t != null) {
                try {
                    int value = Integer.parseInt(t);
                    barWidth = value;
                    repaint();
                } catch (NumberFormatException e1) {
                    MessageUtils.showErrorMessage("Bar width must be an integer", e1);
                }
            }
        });
        menu.add(barWidthItem);

        JMenu setColorMenu = new JMenu("Change Track Color");

        JMenuItem posStrandColorItem = new JMenuItem("Change Positive Strand Color");
        posStrandColorItem.addActionListener(e -> {
            PositiveStrandColor = UIUtilities.showColorChooserDialog(
                    "Change Positive Strand Color",
                    PositiveStrandColor);
            repaint();
        });
        setColorMenu.add(posStrandColorItem);

        JMenuItem negStrandColorItem = new JMenuItem("Change Negative Strand Color");
        negStrandColorItem.addActionListener(e -> {
            NegativeStrandColor = UIUtilities.showColorChooserDialog(
                    "Change Negative Strand Color",
                    NegativeStrandColor);
            repaint();
        });
        setColorMenu.add(negStrandColorItem);

        JMenuItem unknownStrandColorItem = new JMenuItem("Change Unknown Strand Color");
        unknownStrandColorItem.addActionListener(e -> {
            UnknownStrandColor = UIUtilities.showColorChooserDialog(
                    "Change Unknown Strand Color",
                    UnknownStrandColor);
            repaint();
        });
        setColorMenu.add(unknownStrandColorItem);


        menu.add(setColorMenu);

        return menu;
    }

    private void repaint() {
        IGV.getMainFrame().repaint();
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

        if (w > 3000) {
            log.info("The view is too large");
            return;
        }

        // Expand the range a bit to avoid missing data.
        final int matchStart = start - 150, matchEnd = end + 150;

        matchHapList = new ArrayList<>();

        if (isHapDataCached) {
            cachedHapData.forEach(e -> {
                if (e.chr.equals(chr) && e.start >= matchStart && e.end <= matchEnd) {
                    matchHapList.add(e);
                }
            });
        } else {
            log.info("Stream request hap data... Starting from:" + matchStart + " to " + matchEnd + " in " + chr);

            TabixReader.Iterator it = tabixReader.query(chr, matchStart, matchEnd);

            while (true) {
                try {
                    String str = it.next();

                    if (str == null) {
                        break;
                    }
                    HapData hapData = CustomUtility.CreateHapFromString(str.split("[\\s\t]+"));
                    matchHapList.add(hapData);

//                    log.info(hapData.chr + " " + hapData.start + " " + hapData.end);
                } catch (IOException exception) {
                    exception.printStackTrace();
                    JOptionPane.showConfirmDialog(null, "Failed to load .tbi file! You should put .tbi file in the same folder.", "Exception", JOptionPane.ERROR_MESSAGE);
                }
            }

            log.info(matchHapList.size() + " files have been loaded to MatchHap");
        }

        log.info("Repaint the frame from " + chr + " : " + matchStart + " - " + matchEnd + "Width: " + (matchEnd - matchStart));
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
                Font f = FontManager.getFont(Font.BOLD, fontSize);
                g.setFont(f);
            }

            double origin = context.getOrigin();
            int scale = Math.max(1, (int) context.getScale());


            Map<Integer, MeanUtility> meanDic = new HashMap<>();

            ArrayList<AlignInfo> alignInfos = new ArrayList<>();

            Map<Integer, Integer> cpgColDic = new HashMap<>();

            if (matchHapList != null) {
                // Draw Dvision
                // 绘制均值条 与 Reads 的分割线
                if (isShowBar) {
                    g.setColor(Color.BLACK);
                    g.drawLine(0, barBeginY, (int) (seq.length / locScale), barBeginY);
                    g.drawLine(0, barBeginY + barHeight, (int) (seq.length / locScale), barBeginY + barHeight);
                }


                int lastVisibleEnd = Math.min(end, seq.length + sequenceStart);

                // Display HapData
                displayableHapList = matchHapList;

                for (HapData hapData : displayableHapList) {
                    int anchor = 0;


                    // Avoid overlapping of reads.
                    // 防止 Read 的重叠显示
                    int strandOffset = 0;

                    if (hapData.strand == Strand.NEGATIVE) {
                        strandOffset = -1;
                    }

                    int overlapDetectStart = hapData.start + strandOffset;
                    int overlapDetectEnd = hapData.end + strandOffset;

                    int readColIndex = (int) alignInfos.stream().filter(x -> (
                            hapData.start >= overlapDetectStart && hapData.start <= overlapDetectEnd) ||
                            (hapData.end >= overlapDetectStart && hapData.end <= overlapDetectEnd) ||
                            (hapData.start <= overlapDetectStart && hapData.end >= overlapDetectEnd) ||
                            (hapData.start >= overlapDetectStart && hapData.end <= overlapDetectEnd)
                    ).count();

                    // Avoid overlapping of cpgs.
                    // 防止在 CPG 位点上的重叠显示
                    if (cpgColDic.containsKey(overlapDetectStart)) {
                        readColIndex = Math.max(cpgColDic.get(overlapDetectStart), readColIndex);
                        cpgColDic.remove(hapData.start + strandOffset);
                    }

                    if (cpgColDic.containsKey(overlapDetectEnd)) {
                        readColIndex = Math.max(cpgColDic.get(overlapDetectEnd), readColIndex);
                        cpgColDic.remove(hapData.end + strandOffset);
                    }

                    readColIndex += 1;

                    cpgColDic.put(overlapDetectStart, readColIndex);
                    cpgColDic.put(overlapDetectEnd, readColIndex);

                    alignInfos.add(new AlignInfo(hapData.start, hapData.end));

                    ArrayList<Integer> circleXList = new ArrayList<>();
                    ArrayList<Integer> circleYList = new ArrayList<>();

                    boolean beginPadding = false;
                    boolean endPadding = false;

                    Color strandColor = GetColorByStrand(g, hapData);
                    g.setColor(strandColor);

                    // Refer the example in the sequence track and it start with start-1 (I don't know why but just do it!)
                    for (int loc = hapData.start - 1; loc <= hapData.end; loc += scale) {
                        int idx = loc - sequenceStart;

                        // Avoid data and line missing and prevent overflow.
                        // 防止 Read 的线无法与未显示的 CPG点相连，做连线补充。
                        if (idx < 0 && hapData.end > sequenceStart) {
                            if (!beginPadding) {
                                beginPadding = true;
                                circleXList.add(0);
                                circleYList.add(yBase + GetBarBottom() + readColIndex * circleMargin + circleRadius);
                            }

                            continue;
                        }

                        if (idx < 0 && hapData.end <= sequenceStart) {
                            continue;
                        }

                        // Avoid data and line missing and prevent overflow.
                        // 防止 Read 的线无法与未显示的 CPG点相连，做连线补充。
                        if (idx >= seq.length - 1) {
                            if (!endPadding) {
                                endPadding = true;
                                circleXList.add((int) (seq.length / locScale));
                                circleYList.add(yBase + GetBarBottom() + readColIndex * circleMargin + circleRadius);
                            }

                            continue;
                        }

                        // Draw different point depending on the strand
                        // 根据正负链，显示在不同的位置上。
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

                            if (hapData.strand == Strand.NEGATIVE && isCombineCpGBar) {
                                drawIdx -= 1;
                            }

                            if (!meanDic.containsKey(drawIdx)) {
                                meanDic.put(drawIdx, new MeanUtility());
                            }

                            meanDic.get(drawIdx).AddValue(state ? 1 : 0);

                            int pX0 = (int) ((drawIdx + sequenceStart - origin) / locScale);

                            circleXList.add(pX0 + dX / 2 - circleRadius / 2);
                            circleYList.add(yBase + GetBarBottom() + readColIndex * circleMargin + circleRadius);

                            if (!state) {
                                // Draw White Circle!
                                if (fontSize >= perferedMinSize) {
                                    drawOval(g, pX0 + dX / 2 - circleRadius / 2, yBase + GetBarBottom() + readColIndex * circleMargin, circleRadius, circleRadius);
                                }
                            } else {
                                // Draw Black Circle!
                                if (fontSize >= perferedMinSize) {
                                    drawFillOval(g, pX0 + dX / 2 - circleRadius / 2, yBase + GetBarBottom() + readColIndex * circleMargin, circleRadius, circleRadius);
                                }
                            }
                            anchor++;
                        }
                    }

                    if (fontSize >= perferedMinSize) {
                        // Draw line to connect every circle with some interval
                        for (int i = 0; i < circleXList.size() - 1; i++) {
                            g.drawLine(
                                    circleXList.get(i) + circleRadius,
                                    circleYList.get(i) - circleRadius / 2,
                                    circleXList.get(i + 1),
                                    circleYList.get(i + 1) - circleRadius / 2
                            );
                        }
                    }
//                    readColIndex += 1;
                }

                g.setColor(Color.BLACK);

                if (isShowBar) {
                    // Draw Mean
                    for (int id : meanDic.keySet()) {
                        int pX0 = (int) ((id + sequenceStart - origin) / locScale);
                        double mean = meanDic.get(id).GetMean();

                        if (fontSize >= perferedMinSize) {
                            String str = "";

                            if (mean == 0) {
                                str = "0";
                            } else if (mean == 1) {
                                str = "1";
                            } else {
                                str = String.format("%.2f", mean);
                            }

                            drawText(g, str.toCharArray(), pX0, 15, dX);
                        }

                        if (mean > 0) {
                            // Add one pixel to the width to make bar better
                            if (isCombineCpGBar) {
                                int pX1 = (int) ((id + 2 + sequenceStart - origin) / locScale);
                                int width = pX1 - pX0;

                                drawRect(g, pX0 + dX / 2 - barWidth / 2, (int) (barBeginY + barHeight * (1 - mean)), width, (int) (1 + barHeight * mean));
                            } else {
                                drawRect(g, pX0 + dX / 2 - barWidth / 2, (int) (barBeginY + barHeight * (1 - mean)), barWidth, (int) (1 + barHeight * mean));
                            }
                        }
                    }
                }
            }
        }
    }

    private Color GetColorByStrand(Graphics2D g, HapData hapData) {
        switch (hapData.strand) {
            case NONE:
                return UnknownStrandColor;
            case POSITIVE:
                return PositiveStrandColor;
            case NEGATIVE:
                return NegativeStrandColor;
        }

        return UnknownStrandColor;
    }

    @Override
    public int getHeight() {
        if (displayableHapList == null) {
            return 300;
        }

        return Math.max(300, GetBarBottom() + displayableHapList.size() * circleMargin);
    }

    private void drawRect(Graphics2D g, int x, int y, int w, int h) {
        g.fillRect(x, y, w, h);
    }

    private void drawOval(Graphics2D g, int x, int y, int w, int h) {
        g.drawOval(x, y, w, h);
    }

    private void drawFillOval(Graphics2D g, int x, int y, int w, int h) {
        g.fillOval(x, y, w, h);
    }

    private void drawText(Graphics2D g, char[] chars, int x, int y, int w) {
        FontMetrics fm = g.getFontMetrics();

        int msg_width = fm.charsWidth(chars, 0, 1);
        int msgX = x + w / 2 - msg_width / 2;
        int msgY = y;

        g.drawChars(chars, 0, chars.length, msgX, msgY);
    }

    private void drawCenteredText(Graphics2D g, char[] chars, int x, int y, int w, int h) {
        FontMetrics fm = g.getFontMetrics();

        int msg_width = fm.charsWidth(chars, 0, 1);
        int ascent = fm.getMaxAscent();
        int descent = fm.getMaxDescent();
        int msgX = x + w / 2 - msg_width / 2;
        int msgY = y + h / 2 + ascent / 2 - descent / 2;

        g.drawChars(chars, 0, chars.length, msgX, msgY);
    }
}
