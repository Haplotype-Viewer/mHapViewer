/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.sam;


import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.event.AlignmentTrackEvent;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.event.IGVEventObserver;
import org.broad.igv.feature.FeatureUtils;
import org.broad.igv.feature.Locus;
import org.broad.igv.feature.Range;
import org.broad.igv.feature.Strand;
import org.broad.igv.feature.genome.ChromosomeNameComparator;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.lists.GeneList;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.IGVPreferences;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.renderer.GraphicUtils;
import org.broad.igv.sashimi.SashimiPlot;
import org.broad.igv.session.Persistable;
import org.broad.igv.session.Session;
import org.broad.igv.tools.PFMExporter;
import org.broad.igv.track.*;
import org.broad.igv.ui.FontManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.InsertSizeSettingsDialog;
import org.broad.igv.ui.color.ColorTable;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.ui.color.PaletteColorTable;
import org.broad.igv.ui.panel.DataPanel;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.IGVPopupMenu;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.util.Pair;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.StringUtils;
import org.broad.igv.util.blat.BlatClient;
import org.broad.igv.util.collections.CollUtils;
import org.broad.igv.util.extview.ExtendViewClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

import static org.broad.igv.prefs.Constants.*;

/**
 * @author jrobinso
 */

public class AlignmentTrack extends AbstractTrack implements IGVEventObserver {

    private static Logger log = Logger.getLogger(AlignmentTrack.class);

    private static final int GROUP_LABEL_HEIGHT = 10;

    private static final int GROUP_MARGIN = 5;
    private static final int TOP_MARGIN = 20;
    private static final int DS_MARGIN_0 = 2;
    private static final int DOWNAMPLED_ROW_HEIGHT = 3;
    private static final int INSERTION_ROW_HEIGHT = 9;
    private static final int DS_MARGIN_2 = 5;
    private final Genome genome;

    private ExperimentType experimentType;

    private final AlignmentRenderer renderer;

    private boolean removed = false;
    private RenderRollback renderRollback;
    private boolean showGroupLine;
    private Map<ReferenceFrame, List<InsertionInterval>> insertionIntervalsMap;

    private SequenceTrack sequenceTrack;
    private CoverageTrack coverageTrack;
    private SpliceJunctionTrack spliceJunctionTrack;

    RenderOptions renderOptions;

    private int expandedHeight = 14;
    private final int collapsedHeight = 9;
    private final int maxSquishedHeight = 5;
    private int squishedHeight = maxSquishedHeight;

    private final int minHeight = 50;
    private AlignmentDataManager dataManager;
    private Rectangle alignmentsRect;
    private Rectangle downsampleRect;
    private Rectangle insertionRect;
    private ColorTable readNamePalette;

    // Dynamic fields
    // The "DataPanel" containing the track.  This field might be null at any given time.  It is updated each repaint.
    private JComponent dataPanel;
    protected final HashMap<String, Color> selectedReadNames = new HashMap<>();
    private final HashMap<Rectangle, String> groupNames = new HashMap<>();

    public enum ShadeBasesOption {
        NONE, QUALITY
    }

    enum ColorOption {
        INSERT_SIZE, READ_STRAND, FIRST_OF_PAIR_STRAND, PAIR_ORIENTATION, SAMPLE, READ_GROUP, LIBRARY, MOVIE, ZMW,
        BISULFITE, NOMESEQ, TAG, NONE, UNEXPECTED_PAIR, MAPPED_SIZE, LINK_STRAND, YC_TAG
    }

    public enum SortOption {
        START, STRAND, NUCLEOTIDE, QUALITY, SAMPLE, READ_GROUP, INSERT_SIZE, FIRST_OF_PAIR_STRAND, MATE_CHR, TAG,
        SUPPLEMENTARY, NONE, HAPLOTYPE, READ_ORDER
    }

    public enum GroupOption {
        STRAND, SAMPLE, READ_GROUP, LIBRARY, FIRST_OF_PAIR_STRAND, TAG, PAIR_ORIENTATION, MATE_CHROMOSOME, NONE,
        SUPPLEMENTARY, BASE_AT_POS, MOVIE, ZMW, HAPLOTYPE, READ_ORDER
    }

    public enum BisulfiteContext {
        CG, CHH, CHG, HCG, GCH, WCG, NONE
    }

    enum OrientationType {
        RR, LL, RL, LR, UNKNOWN
    }

    private static final Map<BisulfiteContext, String> bisulfiteContextToPubString = new HashMap<>();

    static {
        bisulfiteContextToPubString.put(BisulfiteContext.CG, "CG");
        bisulfiteContextToPubString.put(BisulfiteContext.CHH, "CHH");
        bisulfiteContextToPubString.put(BisulfiteContext.CHG, "CHG");
        bisulfiteContextToPubString.put(BisulfiteContext.HCG, "HCG");
        bisulfiteContextToPubString.put(BisulfiteContext.GCH, "GCH");
        bisulfiteContextToPubString.put(BisulfiteContext.WCG, "WCG");
        bisulfiteContextToPubString.put(BisulfiteContext.NONE, "None");
    }

    private static final Map<BisulfiteContext, Pair<byte[], byte[]>> bisulfiteContextToContextString = new HashMap<>();

    static {
        bisulfiteContextToContextString.put(BisulfiteContext.CG, new Pair<>(new byte[]{}, new byte[]{'G'}));
        bisulfiteContextToContextString.put(BisulfiteContext.CHH, new Pair<>(new byte[]{}, new byte[]{'H', 'H'}));
        bisulfiteContextToContextString.put(BisulfiteContext.CHG, new Pair<>(new byte[]{}, new byte[]{'H', 'G'}));
        bisulfiteContextToContextString.put(BisulfiteContext.HCG, new Pair<>(new byte[]{'H'}, new byte[]{'G'}));
        bisulfiteContextToContextString.put(BisulfiteContext.GCH, new Pair<>(new byte[]{'G'}, new byte[]{'H'}));
        bisulfiteContextToContextString.put(BisulfiteContext.WCG, new Pair<>(new byte[]{'W'}, new byte[]{'G'}));
    }

    static final BisulfiteContext DEFAULT_BISULFITE_CONTEXT = BisulfiteContext.CG;


    /**
     * Create a new alignment track
     *
     * @param locator
     * @param dataManager
     * @param genome
     */
    public AlignmentTrack(ResourceLocator locator, AlignmentDataManager dataManager, Genome genome) {
        super(locator);

        this.dataManager = dataManager;
        this.genome = genome;
        renderer = new AlignmentRenderer(this);
        renderOptions = new RenderOptions();
        dataManager.setAlignmentTrack(this);
        dataManager.subscribe(this);

        IGVPreferences prefs = getPreferences();
        minimumHeight = 50;
        maximumHeight = Integer.MAX_VALUE;
        showGroupLine = prefs.getAsBoolean(SAM_SHOW_GROUP_SEPARATOR);
        try {
            setDisplayMode(DisplayMode.valueOf(prefs.get(SAM_DISPLAY_MODE).toUpperCase()));
        } catch (Exception e) {
            setDisplayMode(DisplayMode.EXPANDED);
        }
        if (prefs.getAsBoolean(SAM_SHOW_REF_SEQ)) {
            sequenceTrack = new SequenceTrack("Reference sequence");
            sequenceTrack.setHeight(14);
        }
        if (renderOptions.colorOption == ColorOption.BISULFITE) {
            setExperimentType(ExperimentType.BISULFITE);
        }
        readNamePalette = new PaletteColorTable(ColorUtilities.getDefaultPalette());
        insertionIntervalsMap = Collections.synchronizedMap(new HashMap<>());

        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
        IGVEventBus.getInstance().subscribe(AlignmentTrackEvent.class, this);
    }


    @Override
    public void receiveEvent(Object event) {

        if (event instanceof FrameManager.ChangeEvent) {
            // Trim insertionInterval map to current frames
            Map<ReferenceFrame, List<InsertionInterval>> newMap = Collections.synchronizedMap(new HashMap<>());
            for (ReferenceFrame frame : ((FrameManager.ChangeEvent) event).getFrames()) {
                if (insertionIntervalsMap.containsKey(frame)) {
                    newMap.put(frame, insertionIntervalsMap.get(frame));
                }
            }
            insertionIntervalsMap = newMap;

        } else if (event instanceof AlignmentTrackEvent) {
            AlignmentTrackEvent e = (AlignmentTrackEvent) event;
            AlignmentTrackEvent.Type eventType = e.getType();
            switch (eventType) {
                case ALLELE_THRESHOLD:
                    dataManager.alleleThresholdChanged();
                    break;
                case RELOAD:
                    clearCaches();
                case REFRESH:
                    renderOptions.refreshDefaults(getExperimentType());
                    refresh();
                    break;
            }

        }
    }

    void setExperimentType(ExperimentType type) {

        if (type != experimentType) {

            experimentType = type;
            renderOptions.refreshDefaults(type);

            boolean showJunction = getPreferences(type).getAsBoolean(Constants.SAM_SHOW_JUNCTION_TRACK);
            if (showJunction != spliceJunctionTrack.isVisible()) {
                spliceJunctionTrack.setVisible(showJunction);
                IGV.getInstance().revalidateTrackPanels();
            }

            boolean showCoverage = getPreferences(type).getAsBoolean(SAM_SHOW_COV_TRACK);
            if (showCoverage != coverageTrack.isVisible()) {
                coverageTrack.setVisible(showCoverage);
                IGV.getInstance().revalidateTrackPanels();
            }

            boolean showAlignments = getPreferences(type).getAsBoolean(SAM_SHOW_ALIGNMENT_TRACK);
            if (showAlignments != isVisible()) {
                setVisible(showAlignments);
                IGV.getInstance().revalidateTrackPanels();
            }
            //ExperimentTypeChangeEvent event = new ExperimentTypeChangeEvent(this, experimentType);
            //IGVEventBus.getInstance().post(event);
        }
    }

    ExperimentType getExperimentType() {
        return experimentType;
    }

    public void setCoverageTrack(CoverageTrack coverageTrack) {
        this.coverageTrack = coverageTrack;
    }

    public CoverageTrack getCoverageTrack() {
        return coverageTrack;
    }

    public void setSpliceJunctionTrack(SpliceJunctionTrack spliceJunctionTrack) {
        this.spliceJunctionTrack = spliceJunctionTrack;
    }

    public SpliceJunctionTrack getSpliceJunctionTrack() {
        return spliceJunctionTrack;
    }


    @Override
    public IGVPopupMenu getPopupMenu(TrackClickEvent te) {
//
//        Alignment alignment = getAlignment(te);
//        if (alignment != null && alignment.getInsertions() != null) {
//            for (AlignmentBlock block : alignment.getInsertions()) {
//                if (block.containsPixel(te.getMouseEvent().getX())) {
//                    return new InsertionMenu(block);
//                }
//            }
//        }
        return new PopupMenu(te);
    }

    @Override
    public void setHeight(int preferredHeight) {
        super.setHeight(preferredHeight);
        minimumHeight = preferredHeight;
    }

    @Override
    public int getHeight() {

        if (dataPanel != null
                && (dataPanel instanceof DataPanel && ((DataPanel) dataPanel).getFrame().getScale() > dataManager.getMinVisibleScale())) {
            return minimumHeight;
        }

        int nGroups = dataManager.getMaxGroupCount();
        int h = Math.max(minHeight, getNLevels() * getRowHeight() + nGroups * GROUP_MARGIN + TOP_MARGIN
                + DS_MARGIN_0 + DOWNAMPLED_ROW_HEIGHT + DS_MARGIN_2);
        //if (insertionRect != null) {   // TODO - replace with expand insertions preference
        h += INSERTION_ROW_HEIGHT + DS_MARGIN_0;
        //}

        h = Math.min(maximumHeight, h);
        return h;
    }

    private int getRowHeight() {
        if (getDisplayMode() == DisplayMode.EXPANDED) {
            return expandedHeight;
        } else if (getDisplayMode() == DisplayMode.COLLAPSED) {
            return collapsedHeight;
        } else {
            return squishedHeight;
        }
    }

    private int getNLevels() {
        return dataManager.getNLevels();
    }

    @Override
    public boolean isReadyToPaint(ReferenceFrame frame) {

        if (frame.getChrName().equals(Globals.CHR_ALL) || frame.getScale() > dataManager.getMinVisibleScale()) {
            return true;   // Nothing to paint
        } else {
            List<InsertionInterval> insertionIntervals = getInsertionIntervals(frame);
            insertionIntervals.clear();
            return dataManager.isLoaded(frame);
        }
    }


    @Override
    public void load(ReferenceFrame referenceFrame) {
        dataManager.load(referenceFrame, renderOptions, true);
    }

    public void render(RenderContext context, Rectangle rect) {

        Graphics2D g = context.getGraphics2D("LABEL");
        g.setFont(FontManager.getFont(GROUP_LABEL_HEIGHT));

        dataPanel = context.getPanel();

        // Split track rectangle into sections.
        int seqHeight = sequenceTrack == null ? 0 : sequenceTrack.getHeight();
        if (seqHeight > 0) {
            Rectangle seqRect = new Rectangle(rect);
            seqRect.height = seqHeight;
            sequenceTrack.render(context, seqRect);
        }

        // Top gap.
        rect.y += DS_MARGIN_0;

        if (context.getScale() > dataManager.getMinVisibleScale()) {
            Rectangle visibleRect = context.getVisibleRect().intersection(rect);
            Graphics2D g2 = context.getGraphic2DForColor(Color.gray);
            GraphicUtils.drawCenteredText("Zoom in to see alignments.", visibleRect, g2);
            return;
        }

        downsampleRect = new Rectangle(rect);
        downsampleRect.height = DOWNAMPLED_ROW_HEIGHT;
        renderDownsampledIntervals(context, downsampleRect);

        if (renderOptions.isDrawInsertionIntervals()) {
            insertionRect = new Rectangle(rect);
            insertionRect.y += DOWNAMPLED_ROW_HEIGHT + DS_MARGIN_0;
            insertionRect.height = INSERTION_ROW_HEIGHT;
            renderInsertionIntervals(context, insertionRect);
            rect.y = insertionRect.y + insertionRect.height;
        }

        alignmentsRect = new Rectangle(rect);
        alignmentsRect.y += 2;
        alignmentsRect.height -= (alignmentsRect.y - rect.y);
        renderAlignments(context, alignmentsRect);
    }

    private void renderDownsampledIntervals(RenderContext context, Rectangle downsampleRect) {

        // Might be offscreen
        if (!context.getVisibleRect().intersects(downsampleRect)) return;

        final AlignmentInterval loadedInterval = dataManager.getLoadedInterval(context.getReferenceFrame());
        if (loadedInterval == null) return;

        Graphics2D g = context.getGraphic2DForColor(Color.black);

        List<DownsampledInterval> intervals = loadedInterval.getDownsampledIntervals();
        for (DownsampledInterval interval : intervals) {
            final double scale = context.getScale();
            final double origin = context.getOrigin();

            int x0 = (int) ((interval.getStart() - origin) / scale);
            int x1 = (int) ((interval.getEnd() - origin) / scale);
            int w = Math.max(1, x1 - x0);
            // If there is room, leave a gap on one side
            if (w > 5) w--;
            // Greyscale from 0 -> 100 downsampled
            //int gray = 200 - interval.getCount();
            //Color color = (gray <= 0 ? Color.black : ColorUtilities.getGrayscaleColor(gray));
            g.fillRect(x0, downsampleRect.y, w, downsampleRect.height);
        }
    }

    private List<InsertionInterval> getInsertionIntervals(ReferenceFrame frame) {
        List<InsertionInterval> insertionIntervals = insertionIntervalsMap.computeIfAbsent(frame, k -> new ArrayList<>());
        return insertionIntervals;
    }

    private void renderInsertionIntervals(RenderContext context, Rectangle rect) {

        // Might be offscreen
        if (!context.getVisibleRect().intersects(rect)) return;

        List<InsertionMarker> intervals = context.getInsertionMarkers();
        if (intervals == null) return;

        InsertionMarker selected = InsertionManager.getInstance().getSelectedInsertion(context.getChr());

        int w = (int) ((1.41 * rect.height) / 2);


        boolean hideSmallIndex = getPreferences().getAsBoolean(SAM_HIDE_SMALL_INDEL);
        int smallIndelThreshold = getPreferences().getAsInt(SAM_SMALL_INDEL_BP_THRESHOLD);

        List<InsertionInterval> insertionIntervals = getInsertionIntervals(context.getReferenceFrame());
        insertionIntervals.clear();
        for (InsertionMarker insertionMarker : intervals) {
            if (hideSmallIndex && insertionMarker.size < smallIndelThreshold) continue;

            final double scale = context.getScale();
            final double origin = context.getOrigin();
            int midpoint = (int) ((insertionMarker.position - origin) / scale);
            int x0 = midpoint - w;
            int x1 = midpoint + w;

            Rectangle iRect = new Rectangle(x0 + context.translateX, rect.y, 2 * w, rect.height);

            insertionIntervals.add(new InsertionInterval(iRect, insertionMarker));

            Color c = (selected != null && selected.position == insertionMarker.position) ? new Color(200, 0, 0, 80) : AlignmentRenderer.purple;
            Graphics2D g = context.getGraphic2DForColor(c);


            g.fillPolygon(new Polygon(new int[]{x0, x1, midpoint},
                    new int[]{rect.y, rect.y, rect.y + rect.height}, 3));
        }
    }


    private void renderAlignments(RenderContext context, Rectangle inputRect) {

        groupNames.clear();


        RenderOptions renderOptions = PreferencesManager.forceDefaults ? new RenderOptions() : this.renderOptions;

        //log.debug("Render features");
        PackedAlignments groups = dataManager.getGroups(context, renderOptions);
        if (groups == null) {
            //Assume we are still loading.
            //This might not always be true
            return;
        }

        // Check for YC tag
        if (renderOptions.colorOption == null && dataManager.hasYCTags()) {
            renderOptions.colorOption = ColorOption.YC_TAG;
        }


        Map<String, PEStats> peStats = dataManager.getPEStats();
        if (peStats != null) {
            renderOptions.peStats = peStats;
        }

        Rectangle visibleRect = context.getVisibleRect();

        maximumHeight = Integer.MAX_VALUE;

        // Divide rectangle into equal height levels
        double y = inputRect.getY();
        double h;
        if (getDisplayMode() == DisplayMode.EXPANDED) {
            h = expandedHeight;
        } else if (getDisplayMode() == DisplayMode.COLLAPSED) {
            h = collapsedHeight;
        } else {

            int visHeight = visibleRect.height;
            int depth = dataManager.getNLevels();
            if (depth == 0) {
                squishedHeight = Math.min(maxSquishedHeight, Math.max(1, expandedHeight));
            } else {
                squishedHeight = Math.min(maxSquishedHeight, Math.max(1, Math.min(expandedHeight, visHeight / depth)));
            }
            h = squishedHeight;
        }

        // Loop through groups
        Graphics2D groupBorderGraphics = context.getGraphic2DForColor(AlignmentRenderer.GROUP_DIVIDER_COLOR);
        int nGroups = groups.size();
        int groupNumber = 0;
        GroupOption groupOption = renderOptions.getGroupByOption();

        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {

            groupNumber++;
            double yGroup = y;  // Remember this for label

            // Loop through the alignment rows for this group
            List<Row> rows = entry.getValue();
            for (Row row : rows) {
                if ((visibleRect != null && y > visibleRect.getMaxY())) {
                    return;
                }

                assert visibleRect != null;
                if (y + h > visibleRect.getY()) {
                    Rectangle rowRectangle = new Rectangle(inputRect.x, (int) y, inputRect.width, (int) h);
                    AlignmentCounts alignmentCounts = dataManager.getLoadedInterval(context.getReferenceFrame()).getCounts();

                    renderer.renderAlignments(row.alignments, alignmentCounts, context, rowRectangle, renderOptions);
                    row.y = y;
                    row.h = h;
                }
                y += h;
            }
            if (groupOption != GroupOption.NONE) {
                // Draw a subtle divider line between groups
                if (showGroupLine) {
                    if (groupNumber < nGroups) {
                        int borderY = (int) y + GROUP_MARGIN / 2;
                        GraphicUtils.drawDottedDashLine(groupBorderGraphics, inputRect.x, borderY, inputRect.width, borderY);
                    }
                }

                // Label the group, if there is room

                double groupHeight = rows.size() * h;
                if (groupHeight > GROUP_LABEL_HEIGHT + 2) {
                    String groupName = entry.getKey();
                    Graphics2D g = context.getGraphics2D("LABEL");
                    FontMetrics fm = g.getFontMetrics();
                    Rectangle2D stringBouds = fm.getStringBounds(groupName, g);
                    Rectangle rect = new Rectangle(inputRect.x, (int) yGroup, (int) stringBouds.getWidth() + 10, (int) stringBouds.getHeight());
                    GraphicUtils.drawVerticallyCenteredText(
                            groupName, 5, rect, context.getGraphics2D("LABEL"), false, true);

                    groupNames.put(new Rectangle(inputRect.x, (int) yGroup, inputRect.width, (int) (y - yGroup)), groupName);
                }

            }
            y += GROUP_MARGIN;


        }

        final int bottom = inputRect.y + inputRect.height;
        groupBorderGraphics.drawLine(inputRect.x, bottom, inputRect.width, bottom);
    }


    public void renderExpandedInsertion(InsertionMarker insertionMarker, RenderContext context, Rectangle inputRect) {


        boolean leaveMargin = getDisplayMode() != DisplayMode.SQUISHED;

        // Insertion interval
        Graphics2D g = context.getGraphic2DForColor(Color.red);
        Rectangle iRect = new Rectangle(inputRect.x, insertionRect.y, inputRect.width, insertionRect.height);
        g.fill(iRect);
        List<InsertionInterval> insertionIntervals = getInsertionIntervals(context.getReferenceFrame());

        iRect.x += context.translateX;
        insertionIntervals.add(new InsertionInterval(iRect, insertionMarker));


        inputRect.y += DS_MARGIN_0 + DOWNAMPLED_ROW_HEIGHT + DS_MARGIN_0 + INSERTION_ROW_HEIGHT + DS_MARGIN_2;

        //log.debug("Render features");
        PackedAlignments groups = dataManager.getGroups(context, renderOptions);
        if (groups == null) {
            //Assume we are still loading.
            //This might not always be true
            return;
        }

        Rectangle visibleRect = context.getVisibleRect();


        maximumHeight = Integer.MAX_VALUE;

        // Divide rectangle into equal height levels
        double y = inputRect.getY() - 3;
        double h;
        if (getDisplayMode() == DisplayMode.EXPANDED) {
            h = expandedHeight;
        } else if (getDisplayMode() == DisplayMode.COLLAPSED) {
            h = collapsedHeight;
        } else {
            int visHeight = visibleRect.height;
            int depth = dataManager.getNLevels();
            if (depth == 0) {
                squishedHeight = Math.min(maxSquishedHeight, Math.max(1, expandedHeight));
            } else {
                squishedHeight = Math.min(maxSquishedHeight, Math.max(1, Math.min(expandedHeight, visHeight / depth)));
            }
            h = squishedHeight;
        }


        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {


            // Loop through the alignment rows for this group
            List<Row> rows = entry.getValue();
            for (Row row : rows) {
                if ((visibleRect != null && y > visibleRect.getMaxY())) {
                    return;
                }

                assert visibleRect != null;
                if (y + h > visibleRect.getY()) {
                    Rectangle rowRectangle = new Rectangle(inputRect.x, (int) y, inputRect.width, (int) h);
                    renderer.renderExpandedInsertion(insertionMarker, row.alignments, context, rowRectangle, leaveMargin);
                    row.y = y;
                    row.h = h;
                }
                y += h;
            }

            y += GROUP_MARGIN;


        }

    }

//
//    public void renderExpandedInsertions(RenderContext context, Rectangle inputRect) {
//
//
//        boolean leaveMargin = getDisplayMode() != DisplayMode.COLLAPSED.SQUISHED;
//
//        inputRect.y += DOWNAMPLED_ROW_HEIGHT + DS_MARGIN_2;
//
//        //log.debug("Render features");
//        PackedAlignments groups = dataManager.getGroups(context, renderOptions);
//        if (groups == null) {
//            //Assume we are still loading.
//            //This might not always be true
//            return;
//        }
//
//        Rectangle visibleRect = context.getVisibleRect();
//
//
//        maximumHeight = Integer.MAX_VALUE;
//
//        // Divide rectangle into equal height levels
//        double y = inputRect.getY();
//        double h;
//        if (getDisplayMode() == DisplayMode.EXPANDED) {
//            h = expandedHeight;
//        } else {
//
//            int visHeight = visibleRect.height;
//            int depth = dataManager.getNLevels();
//            if (depth == 0) {
//                squishedHeight = Math.min(maxSquishedHeight, Math.max(1, expandedHeight));
//            } else {
//                squishedHeight = Math.min(maxSquishedHeight, Math.max(1, Math.min(expandedHeight, visHeight / depth)));
//            }
//            h = squishedHeight;
//        }
//
//
//        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
//
//
//            // Loop through the alignment rows for this group
//            List<Row> rows = entry.getValue();
//            for (Row row : rows) {
//                if ((visibleRect != null && y > visibleRect.getMaxY())) {
//                    return;
//                }
//
//                if (y + h > visibleRect.getY()) {
//                    Rectangle rowRectangle = new Rectangle(inputRect.x, (int) y, inputRect.width, (int) h);
//                    renderer.renderExpandedInsertions(row.alignments, context, rowRectangle, leaveMargin);
//                    row.y = y;
//                    row.h = h;
//                }
//                y += h;
//            }
//
//            y += GROUP_MARGIN;
//
//
//        }
//    }

    /**
     * Sort alignment rows based on alignments that intersect location
     *
     * @return Whether sorting was performed. If data is still loading, this will return false
     */
    public boolean sortRows(SortOption option, ReferenceFrame referenceFrame, double location, String tag) {
        return dataManager.sortRows(option, referenceFrame, location, tag);
    }

    private static void sortAlignmentTracks(SortOption option, String tag) {

        IGV.getInstance().sortAlignmentTracks(option, tag);
        Collection<IGVPreferences> allPrefs = PreferencesManager.getAllPreferences();
        for (IGVPreferences prefs : allPrefs) {
            prefs.put(SAM_SORT_OPTION, option.toString());
            prefs.put(SAM_SORT_BY_TAG, tag);
        }
        refresh();
    }

    /**
     * Visually regroup alignments by the provided {@code GroupOption}.
     *
     * @param option
     * @see AlignmentDataManager#packAlignments
     */
    public void groupAlignments(GroupOption option, String tag, Range pos) {
        if (option == GroupOption.TAG && tag != null) {
            renderOptions.setGroupByTag(tag);
        }
        if (option == GroupOption.BASE_AT_POS && pos != null) {
            renderOptions.setGroupByPos(pos);
        }
        renderOptions.setGroupByOption(option);
        dataManager.packAlignments(renderOptions);
    }

    public void packAlignments() {
        dataManager.packAlignments(renderOptions);
    }

    /**
     * Copy the contents of the popup text to the system clipboard.
     */
    private void copyToClipboard(final TrackClickEvent e, Alignment alignment, double location, int mouseX) {

        if (alignment != null) {
            StringBuilder buf = new StringBuilder();
            buf.append(alignment.getValueString(location, mouseX, null).replace("<br>", "\n"));
            buf.append("\n");
            buf.append("Alignment start position = ").append(alignment.getChr()).append(":").append(alignment.getAlignmentStart() + 1);
            buf.append("\n");
            buf.append(alignment.getReadSequence());
            StringSelection stringSelection = new StringSelection(buf.toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        }

    }

    /**
     * Jump to the mate region
     */
    private void gotoMate(final TrackClickEvent te, Alignment alignment) {


        if (alignment != null) {
            ReadMate mate = alignment.getMate();
            if (mate != null && mate.isMapped()) {

                setSelected(alignment);

                String chr = mate.getChr();
                int start = mate.start - 1;

                // Don't change scale
                double range = te.getFrame().getEnd() - te.getFrame().getOrigin();
                int newStart = (int) Math.max(0, (start + (alignment.getEnd() - alignment.getStart()) / 2 - range / 2));
                int newEnd = newStart + (int) range;
                te.getFrame().jumpTo(chr, newStart, newEnd);
                te.getFrame().recordHistory();
            } else {
                MessageUtils.showMessage("Alignment does not have mate, or it is not mapped.");
            }
        }
    }

    /**
     * Split the screen so the current view and mate region are side by side.
     * Need a better name for this method.
     */
    private void splitScreenMate(final TrackClickEvent te, Alignment alignment) {

        if (alignment != null) {
            ReadMate mate = alignment.getMate();
            if (mate != null && mate.isMapped()) {

                setSelected(alignment);

                String mateChr = mate.getChr();
                int mateStart = mate.start - 1;

                ReferenceFrame frame = te.getFrame();
                String locus1 = frame.getFormattedLocusString();

                // Generate a locus string for the read mate.  Keep the window width (in base pairs) == to the current range
                Range range = frame.getCurrentRange();
                int length = range.getLength();
                int s2 = Math.max(0, mateStart - length / 2);
                int e2 = s2 + length;
                String startStr = NumberFormat.getInstance().format(s2);
                String endStr = NumberFormat.getInstance().format(e2);
                String mateLocus = mateChr + ":" + startStr + "-" + endStr;

                Session currentSession = IGV.getInstance().getSession();

                List<String> loci;
                if (FrameManager.isGeneListMode()) {
                    loci = new ArrayList<>(FrameManager.getFrames().size());
                    for (ReferenceFrame ref : FrameManager.getFrames()) {
                        //If the frame-name is a locus, we use it unaltered
                        //Don't want to reprocess, easy to get off-by-one
                        String name = ref.getName();
                        if (Locus.fromString(name) != null) {
                            loci.add(name);
                        } else {
                            loci.add(ref.getFormattedLocusString());
                        }

                    }
                    loci.add(mateLocus);
                } else {
                    loci = Arrays.asList(locus1, mateLocus);
                }

                StringBuilder listName = new StringBuilder();
                for (String s : loci) {
                    listName.append(s + "   ");
                }

                GeneList geneList = new GeneList(listName.toString(), loci, false);
                currentSession.setCurrentGeneList(geneList);

                Comparator<String> geneListComparator = (n0, n1) -> {
                    ReferenceFrame f0 = FrameManager.getFrame(n0);
                    ReferenceFrame f1 = FrameManager.getFrame(n1);

                    String chr0 = f0 == null ? "" : f0.getChrName();
                    String chr1 = f1 == null ? "" : f1.getChrName();
                    int s0 = f0 == null ? 0 : f0.getCurrentRange().getStart();
                    int s1 = f1 == null ? 0 : f1.getCurrentRange().getStart();

                    int chrComp = ChromosomeNameComparator.get().compare(chr0, chr1);
                    if (chrComp != 0) return chrComp;
                    return s0 - s1;
                };

                //Need to sort the frames by position
                currentSession.sortGeneList(geneListComparator);
                IGV.getInstance().resetFrames();
            } else {
                MessageUtils.showMessage("Alignment does not have mate, or it is not mapped.");
            }
        }
    }

    public boolean isLogNormalized() {
        return false;
    }

    public float getRegionScore(String chr, int start, int end, int zoom, RegionScoreType type, String frameName) {
        return 0.0f;
    }

    public AlignmentDataManager getDataManager() {
        return dataManager;
    }

    public String getValueStringAt(String chr, double position, int mouseX, int mouseY, ReferenceFrame frame) {

        if (downsampleRect != null && mouseY > downsampleRect.y && mouseY <= downsampleRect.y + downsampleRect.height) {
            AlignmentInterval loadedInterval = dataManager.getLoadedInterval(frame);
            if (loadedInterval == null) {
                return null;
            } else {
                List<DownsampledInterval> intervals = loadedInterval.getDownsampledIntervals();
                DownsampledInterval interval = FeatureUtils.getFeatureAt(position, 0, intervals);
                if (interval != null) {
                    return interval.getValueString();
                }
                return null;
            }
        } else {

            InsertionInterval insertionInterval = getInsertionInterval(frame, mouseX, mouseY);
            if (insertionInterval != null) {
                return "Insertions (" + insertionInterval.insertionMarker.size + " bases)";
            } else {
                Alignment feature = getAlignmentAt(position, mouseY, frame);
                if (feature != null) {
                    return feature.getValueString(position, mouseX, getWindowFunction());
                } else {
                    for (Map.Entry<Rectangle, String> groupNameEntry : groupNames.entrySet()) {
                        Rectangle r = groupNameEntry.getKey();
                        if (mouseY >= r.y && mouseY < r.y + r.height) {
                            return groupNameEntry.getValue();
                        }
                    }
                }
            }

        }
        return null;
    }


    private Alignment getAlignment(final TrackClickEvent te) {
        MouseEvent e = te.getMouseEvent();
        final ReferenceFrame frame = te.getFrame();
        if (frame == null) {
            return null;
        }
        final double location = frame.getChromosomePosition(e.getX());
        return getAlignmentAt(location, e.getY(), frame);
    }

    private Alignment getAlignmentAt(double position, int y, ReferenceFrame frame) {

        if (alignmentsRect == null || dataManager == null) {
            return null;   // <= not loaded yet
        }
        PackedAlignments groups = dataManager.getGroupedAlignmentsContaining(position, frame);

        if (groups == null || groups.isEmpty()) {
            return null;
        }

        for (List<Row> rows : groups.values()) {
            for (Row row : rows) {
                if (y >= row.y && y <= row.y + row.h) {
                    List<Alignment> features = row.alignments;

                    // No buffer for alignments,  you must zoom in far enough for them to be visible
                    int buffer = 0;
                    return FeatureUtils.getFeatureAt(position, buffer, features);
                }
            }
        }
        return null;
    }


    /**
     * Get the most "specific" alignment at the specified location.  Specificity refers to the smallest alignemnt
     * in a group that contains the location (i.e. if a group of linked alignments overlap take the smallest one).
     *
     * @param te
     * @return
     */
    private Alignment getSpecficAlignment(TrackClickEvent te) {

        Alignment alignment = getAlignment(te);
        if (alignment != null) {
            final ReferenceFrame frame = te.getFrame();
            MouseEvent e = te.getMouseEvent();
            final double location = frame.getChromosomePosition(e.getX());

            if (alignment instanceof LinkedAlignment) {

                Alignment sa = null;
                for (Alignment a : ((LinkedAlignment) alignment).alignments) {
                    if (a.contains(location)) {
                        if (sa == null || (a.getAlignmentEnd() - a.getAlignmentStart() < sa.getAlignmentEnd() - sa.getAlignmentStart())) {
                            sa = a;
                        }
                    }
                }
                alignment = sa;

            } else if (alignment instanceof PairedAlignment) {
                Alignment sa = null;
                if (((PairedAlignment) alignment).firstAlignment.contains(location)) {
                    sa = ((PairedAlignment) alignment).firstAlignment;
                } else if (((PairedAlignment) alignment).secondAlignment.contains(location)) {
                    sa = ((PairedAlignment) alignment).secondAlignment;
                }
                alignment = sa;
            }
        }
        return alignment;
    }


    @Override
    public boolean handleDataClick(TrackClickEvent te) {

        MouseEvent e = te.getMouseEvent();
        if (Globals.IS_MAC && e.isMetaDown() || (!Globals.IS_MAC && e.isControlDown())) {
            // Selection
            final ReferenceFrame frame = te.getFrame();
            if (frame != null) {
                selectAlignment(e, frame);
                if (dataPanel != null) {
                    dataPanel.repaint();
                }
                return true;
            }
        }

        InsertionInterval insertionInterval = getInsertionInterval(te.getFrame(), te.getMouseEvent().getX(), te.getMouseEvent().getY());
        if (insertionInterval != null) {

            final String chrName = te.getFrame().getChrName();
            InsertionMarker currentSelection = InsertionManager.getInstance().getSelectedInsertion(chrName);
            if (currentSelection != null && currentSelection.position == insertionInterval.insertionMarker.position) {
                InsertionManager.getInstance().clearSelected();
            } else {
                InsertionManager.getInstance().setSelected(chrName, insertionInterval.insertionMarker.position);
            }

            IGVEventBus.getInstance().post(new InsertionSelectionEvent(insertionInterval.insertionMarker));

            return true;
        }


        if (IGV.getInstance().isShowDetailsOnClick()) {
            openTooltipWindow(te);
            return true;
        }

        return false;
    }

    private void selectAlignment(MouseEvent e, ReferenceFrame frame) {
        double location = frame.getChromosomePosition(e.getX());
        Alignment alignment = this.getAlignmentAt(location, e.getY(), frame);
        if (alignment != null) {
            if (selectedReadNames.containsKey(alignment.getReadName())) {
                selectedReadNames.remove(alignment.getReadName());
            } else {
                setSelected(alignment);
            }

        }
    }

    private InsertionInterval getInsertionInterval(ReferenceFrame frame, int x, int y) {
        List<InsertionInterval> insertionIntervals = getInsertionIntervals(frame);
        for (InsertionInterval i : insertionIntervals) {
            if (i.rect.contains(x, y)) return i;
        }

        return null;
    }

    private void setSelected(Alignment alignment) {
        Color c = readNamePalette.get(alignment.getReadName());
        selectedReadNames.put(alignment.getReadName(), c);
    }

    private void clearCaches() {
        if (dataManager != null) dataManager.clear();
        if (spliceJunctionTrack != null) spliceJunctionTrack.clear();
    }

    private static void refresh() {
        IGV.getInstance().getContentPane().getMainPanel().invalidate();
        IGV.getInstance().revalidateTrackPanels();
    }

    public static boolean isBisulfiteColorType(ColorOption o) {
        return (o.equals(ColorOption.BISULFITE) || o.equals(ColorOption.NOMESEQ));
    }

    private static String getBisulfiteContextPubStr(BisulfiteContext item) {
        return bisulfiteContextToPubString.get(item);
    }

    public static byte[] getBisulfiteContextPreContext(BisulfiteContext item) {
        Pair<byte[], byte[]> pair = AlignmentTrack.bisulfiteContextToContextString.get(item);
        return pair.getFirst();
    }

    public static byte[] getBisulfiteContextPostContext(BisulfiteContext item) {
        Pair<byte[], byte[]> pair = AlignmentTrack.bisulfiteContextToContextString.get(item);
        return pair.getSecond();
    }


    public void setViewAsPairs(boolean vAP) {
        // TODO -- generalize this test to all incompatible pairings
        if (vAP && renderOptions.groupByOption == GroupOption.STRAND) {
            boolean ungroup = MessageUtils.confirm("\"View as pairs\" is incompatible with \"Group by strand\". Ungroup?");
            if (ungroup) {
                renderOptions.setGroupByOption(null);
            } else {
                return;
            }
        }

        dataManager.setViewAsPairs(vAP, renderOptions);
        refresh();
    }

    public enum ExperimentType {OTHER, RNA, BISULFITE, THIRD_GEN}


    class RenderRollback {
        final ColorOption colorOption;
        final GroupOption groupByOption;
        final String groupByTag;
        final String colorByTag;
        final String linkByTag;
        final DisplayMode displayMode;
        final int expandedHeight;
        final boolean showGroupLine;

        RenderRollback(RenderOptions renderOptions, DisplayMode displayMode) {
            this.colorOption = renderOptions.colorOption;
            this.groupByOption = renderOptions.groupByOption;
            this.colorByTag = renderOptions.colorByTag;
            this.groupByTag = renderOptions.groupByTag;
            this.displayMode = displayMode;
            this.expandedHeight = AlignmentTrack.this.expandedHeight;
            this.showGroupLine = AlignmentTrack.this.showGroupLine;
            this.linkByTag = renderOptions.linkByTag;
        }

        void restore(RenderOptions renderOptions) {
            renderOptions.colorOption = this.colorOption;
            renderOptions.groupByOption = this.groupByOption;
            renderOptions.colorByTag = this.colorByTag;
            renderOptions.groupByTag = this.groupByTag;
            renderOptions.linkByTag = this.linkByTag;
            AlignmentTrack.this.expandedHeight = this.expandedHeight;
            AlignmentTrack.this.showGroupLine = this.showGroupLine;
            AlignmentTrack.this.setDisplayMode(this.displayMode);
        }
    }


    public boolean isRemoved() {
        return removed;
    }

    IGVPreferences getPreferences() {
        return getPreferences(experimentType);
    }

    private static IGVPreferences getPreferences(ExperimentType type) {

        try {
            // Disable experimentType preferences for 2.4
            if (Globals.VERSION.contains("2.4")) {
                return PreferencesManager.getPreferences(NULL_CATEGORY);
            } else {
                String prefKey = Constants.NULL_CATEGORY;
                if (type == ExperimentType.THIRD_GEN) {
                    prefKey = Constants.THIRD_GEN;
                } else if (type == ExperimentType.RNA) {
                    prefKey = Constants.RNA;
                }
                return PreferencesManager.getPreferences(prefKey);
            }
        } catch (NullPointerException e) {
            String prefKey = Constants.NULL_CATEGORY;
            if (type == ExperimentType.THIRD_GEN) {
                prefKey = Constants.THIRD_GEN;
            } else if (type == ExperimentType.RNA) {
                prefKey = Constants.RNA;
            }
            return PreferencesManager.getPreferences(prefKey);
        }
    }


    @Override
    public void dispose() {
        super.dispose();
        clearCaches();
        if (dataManager != null) {
            dataManager.unsubscribe(this);
        }
        dataManager = null;
        removed = true;
        setVisible(false);
    }

    private boolean isLinkedReads() {
        return renderOptions.isLinkedReads();
    }

    private void setLinkedReads(boolean linkedReads, String tag) {

        renderOptions.setLinkedReads(linkedReads);
        if (linkedReads) {

            if (renderRollback == null) renderRollback = new RenderRollback(renderOptions, getDisplayMode());

            renderOptions.setLinkByTag(tag);

            if ("READNAME".equals(tag)) {
                renderOptions.setColorOption(ColorOption.LINK_STRAND);
            } else {
                // TenX -- ditto
                renderOptions.setColorOption(ColorOption.TAG);
                renderOptions.setColorByTag(tag);

                if (dataManager.isPhased()) {
                    renderOptions.setGroupByOption(GroupOption.TAG);
                    renderOptions.setGroupByTag("HP");
                }
                expandedHeight = 10;
                showGroupLine = false;
                setDisplayMode(DisplayMode.SQUISHED);
            }
        } else {
            if (this.renderRollback != null) {
                this.renderRollback.restore(renderOptions);
                this.renderRollback = null;
            }
        }

        dataManager.packAlignments(renderOptions);
        refresh();
    }

    /**
     * Listener for deselecting one component when another is selected
     */
    private static class Deselector implements ActionListener {

        private final JMenuItem toDeselect;
        private final JMenuItem parent;

        Deselector(JMenuItem parent, JMenuItem toDeselect) {
            this.parent = parent;
            this.toDeselect = toDeselect;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (this.parent.isSelected()) {
                this.toDeselect.setSelected(false);
            }
        }
    }

    private static class InsertionInterval {

        final Rectangle rect;
        final InsertionMarker insertionMarker;

        InsertionInterval(Rectangle rect, InsertionMarker insertionMarker) {
            this.rect = rect;
            this.insertionMarker = insertionMarker;
        }
    }

    private static int nClusters = 2;

    class PopupMenu extends IGVPopupMenu {


        PopupMenu(final TrackClickEvent e) {

            final MouseEvent me = e.getMouseEvent();
            ReferenceFrame frame = e.getFrame();
            Alignment clickedAlignment = null;

            if (frame != null) {
                double location = frame.getChromosomePosition(me.getX());
                clickedAlignment = getAlignmentAt(location, me.getY(), frame);
            }


            Collection<Track> tracks = new ArrayList();
            tracks.add(AlignmentTrack.this);

            JLabel popupTitle = new JLabel("  " + AlignmentTrack.this.getName(), JLabel.CENTER);

            Font newFont = getFont().deriveFont(Font.BOLD, 12);
            popupTitle.setFont(newFont);
            add(popupTitle);
            addSeparator();
            add(TrackMenuUtils.getTrackRenameItem(tracks));
            addCopyToClipboardItem(e, clickedAlignment);

            //         addSeparator();
            //          addExpandInsertions();

            addSeparator();
            addExperimentTypeMenuItem();

            if (experimentType == ExperimentType.THIRD_GEN) {
                addHaplotype(e);
            }


            if (dataManager.isTenX()) {
                addTenXItems();
            } else {
                addSupplItems();     // Are SA tags mutually exlcusive with 10X?
            }

            addSeparator();
            addGroupMenuItem(e);
            addSortMenuItem();
            addColorByMenuItem();
            //addFilterMenuItem();
            addPackMenuItem();

            addSeparator();
            addShadeBaseByMenuItem();
            JMenuItem misMatchesItem = addShowMismatchesMenuItem();
            JMenuItem showAllItem = addShowAllBasesMenuItem();

            misMatchesItem.addActionListener(new Deselector(misMatchesItem, showAllItem));
            showAllItem.addActionListener(new Deselector(showAllItem, misMatchesItem));

            addQuickConsensusModeItem();

            addSeparator();
            addViewAsPairsMenuItem();

            if (clickedAlignment != null) {
                addGoToMate(e, clickedAlignment);
                showMateRegion(e, clickedAlignment);
            }

            addInsertSizeMenuItem();

            addSeparator();
            TrackMenuUtils.addDisplayModeItems(tracks, this);

            addSeparator();
            addSelectByNameItem();
            addClearSelectionsMenuItem();

            addSeparator();
            addCopySequenceItem(e);

            addBlatItem(e);
            addBlatClippingItems(e);
            addConsensusSequence(e);

            AlignmentBlock insertion = getInsertion(clickedAlignment, e.getMouseEvent().getX());
            if (insertion != null) {
                addSeparator();
                addInsertionItems(insertion);
            }

            addSeparator();
            JMenuItem sashimi = new JMenuItem("Sashimi Plot");
            sashimi.addActionListener(e1 -> SashimiPlot.getSashimiPlot(null));
            add(sashimi);

            addSeparator();
            addShowItems();


            if (getPreferences().get(Constants.EXTVIEW_URL) != null) {
                addSeparator();
                addExtViewItem(e);
            }

        }


        private void addHaplotype(TrackClickEvent e) {

            JMenuItem item = new JMenuItem("Cluster (phase) alignments");

            final ReferenceFrame frame;
            if (e.getFrame() == null && FrameManager.getFrames().size() == 1) {
                frame = FrameManager.getFrames().get(0);
            } else {
                frame = e.getFrame();
            }

            item.setEnabled(frame != null);
            add(item);

            item.addActionListener(ae -> {
                //This shouldn't ever be true, but just in case it's more user-friendly
                if (frame == null) {
                    MessageUtils.showMessage("Unknown region bounds");
                    return;
                }

                String nString = MessageUtils.showInputDialog("Enter the number of clusters", String.valueOf(AlignmentTrack.nClusters));
                if (nString == null) {
                    return;
                }
                try {
                    AlignmentTrack.nClusters = Integer.parseInt(nString);
                } catch (NumberFormatException e1) {
                    MessageUtils.showMessage("Clusters size must be an integer");
                    return;
                }

                final int start = (int) frame.getOrigin();
                final int end = (int) frame.getEnd();

                AlignmentInterval interval = dataManager.getLoadedInterval(frame);
                HaplotypeUtils haplotypeUtils = new HaplotypeUtils(interval, AlignmentTrack.this.genome);
                boolean success = haplotypeUtils.clusterAlignments(frame.getChrName(), start, end, AlignmentTrack.nClusters);

                if (success) {
                    AlignmentTrack.this.groupAlignments(GroupOption.HAPLOTYPE, null, null);
                    AlignmentTrack.refresh();
                }

                //dataManager.sortRows(SortOption.HAPLOTYPE, frame, (end + start) / 2, null);
                //AlignmentTrack.refresh();

            });


        }


        public JMenuItem addExpandInsertions() {

            final JMenuItem item = new JCheckBoxMenuItem("Expand insertions");
            final Session session = IGV.getInstance().getSession();
            item.setSelected(session.expandInsertions);

            item.addActionListener(aEvt -> {
                session.expandInsertions = !session.expandInsertions;
                refresh();
            });
            add(item);
            return item;
        }

        /**
         * Item for exporting "consensus" sequence of region, based
         * on loaded alignments.
         *
         * @param e
         */
        private void addConsensusSequence(TrackClickEvent e) {
            //Export consensus sequence
            JMenuItem item = new JMenuItem("Copy consensus sequence");


            final ReferenceFrame frame;
            if (e.getFrame() == null && FrameManager.getFrames().size() == 1) {
                frame = FrameManager.getFrames().get(0);
            } else {
                frame = e.getFrame();
            }

            item.setEnabled(frame != null);
            add(item);

            item.addActionListener(ae -> {
                //This shouldn't ever be true, but just in case it's more user-friendly
                if (frame == null) {
                    MessageUtils.showMessage("Unknown region bounds, cannot export consensus");
                    return;
                }
                final int start = (int) frame.getOrigin();
                final int end = (int) frame.getEnd();
                if ((end - start) > 1000000) {
                    MessageUtils.showMessage("Cannot export region more than 1 Megabase");
                    return;
                }
                AlignmentInterval interval = dataManager.getLoadedInterval(frame);
                AlignmentCounts counts = interval.getCounts();
                String text = PFMExporter.createPFMText(counts, frame.getChrName(), start, end);
                StringUtils.copyTextToClipboard(text);
            });


        }

        private JMenu getBisulfiteContextMenuItem(ButtonGroup group) {
            // Change track height by attribute
            //JMenu bisulfiteContextMenu = new JMenu("Bisulfite Contexts");
            JMenu bisulfiteContextMenu = new JMenu("bisulfite mode");


            JRadioButtonMenuItem nomeESeqOption = null;
            boolean showNomeESeq = getPreferences().getAsBoolean(SAM_NOMESEQ_ENABLED);
            if (showNomeESeq) {
                nomeESeqOption = new JRadioButtonMenuItem("NOMe-seq bisulfite mode");
                nomeESeqOption.setSelected(renderOptions.getColorOption() == ColorOption.NOMESEQ);
                nomeESeqOption.addActionListener(aEvt -> {
                    setColorOption(ColorOption.NOMESEQ);
                    refresh();
                });
                group.add(nomeESeqOption);
            }

            for (final BisulfiteContext item : BisulfiteContext.values()) {

                String optionStr = getBisulfiteContextPubStr(item);
                JRadioButtonMenuItem m1 = new JRadioButtonMenuItem(optionStr);
                m1.setSelected(renderOptions.bisulfiteContext == item);
                m1.addActionListener(aEvt -> {
                    setColorOption(ColorOption.BISULFITE);
                    setBisulfiteContext(item);
                    refresh();
                });
                bisulfiteContextMenu.add(m1);
                group.add(m1);
            }

            if (nomeESeqOption != null) {
                bisulfiteContextMenu.add(nomeESeqOption);
            }

            return bisulfiteContextMenu;

        }

        void addSelectByNameItem() {
            // Change track height by attribute
            JMenuItem item = new JMenuItem("Select by name...");
            item.addActionListener(aEvt -> {
                String val = MessageUtils.showInputDialog("Enter read name: ");
                if (val != null && val.trim().length() > 0) {
                    selectedReadNames.put(val, readNamePalette.get(val));
                    refresh();
                }
            });
            add(item);
        }

        void addExperimentTypeMenuItem() {
            Map<String, ExperimentType> mappings = new LinkedHashMap<>();
            mappings.put("Other", ExperimentType.OTHER);
            mappings.put("RNA", ExperimentType.RNA);
            mappings.put("3rd Gen", ExperimentType.THIRD_GEN);
            //mappings.put("Bisulfite", ExperimentType.BISULFITE);
            JMenu groupMenu = new JMenu("Experiment Type");
            ButtonGroup group = new ButtonGroup();
            for (Map.Entry<String, ExperimentType> el : mappings.entrySet()) {
                JCheckBoxMenuItem mi = getExperimentTypeMenuItem(el.getKey(), el.getValue());
                groupMenu.add(mi);
                group.add(mi);
            }
            add(groupMenu);
        }

        private JCheckBoxMenuItem getExperimentTypeMenuItem(String label, final ExperimentType option) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(label);
            mi.setSelected(AlignmentTrack.this.getExperimentType() == option);
            mi.addActionListener(aEvt -> AlignmentTrack.this.setExperimentType(option));
            return mi;
        }

        void addGroupMenuItem(final TrackClickEvent te) {//ReferenceFrame frame) {
            final MouseEvent me = te.getMouseEvent();
            ReferenceFrame frame = te.getFrame();
            if (frame == null) {
                frame = FrameManager.getDefaultFrame();  // Clicked over name panel, not a specific frame
            }
            final Range range = frame.getCurrentRange();
            final String chrom = range.getChr();
            final int chromStart = (int) frame.getChromosomePosition(me.getX());
            // Change track height by attribute
            JMenu groupMenu = new JMenu("Group alignments by");
            ButtonGroup group = new ButtonGroup();

            Map<String, GroupOption> mappings = new LinkedHashMap<>();
            mappings.put("none", GroupOption.NONE);
            mappings.put("read strand", GroupOption.STRAND);
            mappings.put("first-in-pair strand", GroupOption.FIRST_OF_PAIR_STRAND);
            mappings.put("sample", GroupOption.SAMPLE);
            mappings.put("library", GroupOption.LIBRARY);
            mappings.put("read group", GroupOption.READ_GROUP);
            mappings.put("chromosome of mate", GroupOption.MATE_CHROMOSOME);
            mappings.put("pair orientation", GroupOption.PAIR_ORIENTATION);
            mappings.put("supplementary flag", GroupOption.SUPPLEMENTARY);
            mappings.put("movie", GroupOption.MOVIE);
            mappings.put("ZMW", GroupOption.ZMW);
            mappings.put("read order", GroupOption.READ_ORDER);


            for (Map.Entry<String, GroupOption> el : mappings.entrySet()) {
                JCheckBoxMenuItem mi = getGroupMenuItem(el.getKey(), el.getValue());
                groupMenu.add(mi);
                group.add(mi);
            }

            JCheckBoxMenuItem tagOption = new JCheckBoxMenuItem("tag");
            tagOption.addActionListener(aEvt -> {
                String tag = MessageUtils.showInputDialog("Enter tag", renderOptions.getGroupByTag());
                if (tag != null && tag.trim().length() > 0) {
                    IGV.getInstance().groupAlignmentTracks(GroupOption.TAG, tag, null);
                    refresh();
                }

            });
            tagOption.setSelected(renderOptions.getGroupByOption() == GroupOption.TAG);
            groupMenu.add(tagOption);
            group.add(tagOption);

            Range oldGroupByPos = renderOptions.getGroupByPos();
            if (renderOptions.getGroupByOption() == GroupOption.BASE_AT_POS) { // already sorted by the base at a position
                JCheckBoxMenuItem oldGroupByPosOption = new JCheckBoxMenuItem("base at " + oldGroupByPos.getChr() +
                        ":" + Globals.DECIMAL_FORMAT.format(1 + oldGroupByPos.getStart()));
                groupMenu.add(oldGroupByPosOption);
                oldGroupByPosOption.setSelected(true);
            }

            if (renderOptions.getGroupByOption() != GroupOption.BASE_AT_POS || oldGroupByPos == null ||
                    !oldGroupByPos.getChr().equals(chrom) || oldGroupByPos.getStart() != chromStart) { // not already sorted by this position
                JCheckBoxMenuItem newGroupByPosOption = new JCheckBoxMenuItem("base at " + chrom +
                        ":" + Globals.DECIMAL_FORMAT.format(1 + chromStart));
                newGroupByPosOption.addActionListener(aEvt -> {
                    Range groupByPos = new Range(chrom, chromStart, chromStart + 1);
                    IGV.getInstance().groupAlignmentTracks(GroupOption.BASE_AT_POS, null, groupByPos);
                    refresh();
                });
                groupMenu.add(newGroupByPosOption);
                group.add(newGroupByPosOption);
            }

            add(groupMenu);
        }

        private JCheckBoxMenuItem getGroupMenuItem(String label, final GroupOption option) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(label);
            mi.setSelected(renderOptions.getGroupByOption() == option);
            mi.addActionListener(aEvt -> {
                IGV.getInstance().groupAlignmentTracks(option, null, null);
                refresh();

            });
            return mi;
        }

        private JMenuItem getSortMenuItem(String label, final SortOption option) {
            JMenuItem mi = new JMenuItem(label);
            mi.addActionListener(aEvt -> sortAlignmentTracks(option, null));
            return mi;
        }

        /**
         * Sort menu
         */
        void addSortMenuItem() {


            JMenu sortMenu = new JMenu("Sort alignments by");
            //LinkedHashMap is supposed to preserve order of insertion for iteration
            Map<String, SortOption> mappings = new LinkedHashMap<>();

            mappings.put("start location", SortOption.START);
            mappings.put("read strand", SortOption.STRAND);
            mappings.put("first-of-pair strand", SortOption.FIRST_OF_PAIR_STRAND);
            mappings.put("base", SortOption.NUCLEOTIDE);
            mappings.put("mapping quality", SortOption.QUALITY);
            mappings.put("sample", SortOption.SAMPLE);
            mappings.put("read group", SortOption.READ_GROUP);
            mappings.put("read order", SortOption.READ_ORDER);

            if (dataManager.isPairedEnd()) {
                mappings.put("insert size", SortOption.INSERT_SIZE);
                mappings.put("chromosome of mate", SortOption.MATE_CHR);
            }
            // mappings.put("supplementary flag", SortOption.SUPPLEMENTARY);

            for (Map.Entry<String, SortOption> el : mappings.entrySet()) {
                sortMenu.add(getSortMenuItem(el.getKey(), el.getValue()));
            }


            JMenuItem tagOption = new JMenuItem("tag");
            tagOption.addActionListener(aEvt -> {
                String tag = MessageUtils.showInputDialog("Enter tag", renderOptions.getSortByTag());
                if (tag != null && tag.trim().length() > 0) {
                    renderOptions.setSortByTag(tag);
                    sortAlignmentTracks(SortOption.TAG, tag);
                }
            });
            sortMenu.add(tagOption);


            add(sortMenu);
        }

        public void addFilterMenuItem() {
            JMenu filterMenu = new JMenu("Filter alignments by");
            JMenuItem mi = new JMenuItem("mapping quality");
            mi.addActionListener(aEvt -> {
                // TODO -- use current value for default
                String defString = PreferencesManager.getPreferences().get(SAM_QUALITY_THRESHOLD);
                if (defString == null) defString = "";
                String mqString = MessageUtils.showInputDialog("Minimum mapping quality: ", defString);
                try {
                    int mq = Integer.parseInt(mqString);
                    // TODO do something with this
                    System.out.println(mq);
                } catch (NumberFormatException e) {
                    MessageUtils.showMessage("Mapping quality must be an integer");
                }
            });
            filterMenu.add(mi);
            add(filterMenu);
        }


        private void setBisulfiteContext(BisulfiteContext option) {
            renderOptions.bisulfiteContext = option;
            getPreferences().put(SAM_BISULFITE_CONTEXT, option.toString());
        }

        private void setColorOption(ColorOption option) {
            renderOptions.setColorOption(option);
            getPreferences().put(SAM_COLOR_BY, option.toString());
        }

        private JRadioButtonMenuItem getColorMenuItem(String label, final ColorOption option) {
            JRadioButtonMenuItem mi = new JRadioButtonMenuItem(label);
            mi.setSelected(renderOptions.getColorOption() == option);
            mi.addActionListener(aEvt -> {
                setColorOption(option);
                refresh();
            });

            return mi;
        }

        void addColorByMenuItem() {
            // Change track height by attribute
            JMenu colorMenu = new JMenu("Color alignments by");

            ButtonGroup group = new ButtonGroup();

            Map<String, ColorOption> mappings = new LinkedHashMap<>();

            mappings.put("no color", ColorOption.NONE);

            if (dataManager.hasYCTags()) {
                mappings.put("YC tag", ColorOption.YC_TAG);
            }

            if (dataManager.isPairedEnd()) {

                mappings.put("insert size", ColorOption.INSERT_SIZE);
                mappings.put("pair orientation", ColorOption.PAIR_ORIENTATION);
                mappings.put("insert size and pair orientation", ColorOption.UNEXPECTED_PAIR);

            }

            mappings.put("read strand", ColorOption.READ_STRAND);

            if (dataManager.isPairedEnd()) {
                mappings.put("first-of-pair strand", ColorOption.FIRST_OF_PAIR_STRAND);
            }

            mappings.put("read group", ColorOption.READ_GROUP);
            mappings.put("sample", ColorOption.SAMPLE);
            mappings.put("library", ColorOption.LIBRARY);
            mappings.put("movie", ColorOption.MOVIE);
            mappings.put("ZMW", ColorOption.ZMW);

            for (Map.Entry<String, ColorOption> el : mappings.entrySet()) {
                JRadioButtonMenuItem mi = getColorMenuItem(el.getKey(), el.getValue());
                colorMenu.add(mi);
                group.add(mi);
            }

            JRadioButtonMenuItem tagOption = new JRadioButtonMenuItem("tag");
            tagOption.setSelected(renderOptions.getColorOption() == ColorOption.TAG);
            tagOption.addActionListener(aEvt -> {
                setColorOption(ColorOption.TAG);
                String tag = MessageUtils.showInputDialog("Enter tag", renderOptions.getColorByTag());
                if (tag != null && tag.trim().length() > 0) {
                    renderOptions.setColorByTag(tag);
                    getPreferences(experimentType).put(SAM_COLOR_BY_TAG, tag);
                    refresh();
                }
            });
            colorMenu.add(tagOption);
            group.add(tagOption);


            colorMenu.add(getBisulfiteContextMenuItem(group));


            add(colorMenu);

        }

        void addPackMenuItem() {
            // Change track height by attribute
            JMenuItem item = new JMenuItem("Re-pack alignments");
            item.addActionListener(aEvt -> UIUtilities.invokeOnEventThread(() -> {
                IGV.getInstance().packAlignmentTracks();
                refresh();
            }));

            add(item);
        }

        void addCopyToClipboardItem(final TrackClickEvent te, Alignment alignment) {

            final MouseEvent me = te.getMouseEvent();
            JMenuItem item = new JMenuItem("Copy read details to clipboard");

            final ReferenceFrame frame = te.getFrame();
            if (frame == null) {
                item.setEnabled(false);
            } else {
                final double location = frame.getChromosomePosition(me.getX());

                // Change track height by attribute
                item.addActionListener(aEvt -> copyToClipboard(te, alignment, location, me.getX()));
                if (alignment == null) {
                    item.setEnabled(false);
                }
            }

            add(item);
        }


        void addViewAsPairsMenuItem() {
            final JMenuItem item = new JCheckBoxMenuItem("View as pairs");
            item.setSelected(renderOptions.isViewPairs());
            item.addActionListener(aEvt -> {
                boolean viewAsPairs = item.isSelected();
                setViewAsPairs(viewAsPairs);
            });
            item.setEnabled(dataManager.isPairedEnd());
            add(item);
        }

        void addGoToMate(final TrackClickEvent te, Alignment alignment) {
            // Change track height by attribute
            JMenuItem item = new JMenuItem("Go to mate");
            MouseEvent e = te.getMouseEvent();

            final ReferenceFrame frame = te.getFrame();
            if (frame == null) {
                item.setEnabled(false);
            } else {
                item.addActionListener(aEvt -> gotoMate(te, alignment));
                if (alignment == null || !alignment.isPaired() || !alignment.getMate().isMapped()) {
                    item.setEnabled(false);
                }
            }
            add(item);
        }

        void showMateRegion(final TrackClickEvent te, Alignment clickedAlignment) {
            // Change track height by attribute
            JMenuItem item = new JMenuItem("View mate region in split screen");
            MouseEvent e = te.getMouseEvent();

            final ReferenceFrame frame = te.getFrame();
            if (frame == null) {
                item.setEnabled(false);
            } else {
                double location = frame.getChromosomePosition(e.getX());

                if (clickedAlignment instanceof PairedAlignment) {
                    Alignment first = ((PairedAlignment) clickedAlignment).getFirstAlignment();
                    Alignment second = ((PairedAlignment) clickedAlignment).getSecondAlignment();
                    if (first.contains(location)) {
                        clickedAlignment = first;

                    } else if (second.contains(location)) {
                        clickedAlignment = second;

                    } else {
                        clickedAlignment = null;

                    }
                }

                final Alignment alignment = clickedAlignment;
                item.addActionListener(aEvt -> splitScreenMate(te, alignment));
                if (alignment == null || !alignment.isPaired() || !alignment.getMate().isMapped()) {
                    item.setEnabled(false);
                }
            }
            add(item);
        }

        void addClearSelectionsMenuItem() {
            // Change track height by attribute
            JMenuItem item = new JMenuItem("Clear selections");
            item.addActionListener(aEvt -> {
                selectedReadNames.clear();
                refresh();
            });
            add(item);
        }

        JMenuItem addShowAllBasesMenuItem() {
            // Change track height by attribute
            final JMenuItem item = new JCheckBoxMenuItem("Show all bases");

            if (renderOptions.getColorOption() == ColorOption.BISULFITE || renderOptions.getColorOption() == ColorOption.NOMESEQ) {
                //    item.setEnabled(false);
            } else {
                item.setSelected(renderOptions.isShowAllBases());
            }
            item.addActionListener(aEvt -> {
                renderOptions.setShowAllBases(item.isSelected());
                refresh();
            });
            add(item);
            return item;
        }

        void addQuickConsensusModeItem() {
            // Change track height by attribute
            final JMenuItem item = new JCheckBoxMenuItem("Quick consensus mode");
            item.setSelected(renderOptions.isQuickConsensusMode());

            item.addActionListener(aEvt -> {
                renderOptions.setQuickConsensusMode(item.isSelected());
                refresh();
            });
            add(item);
        }

        JMenuItem addShowMismatchesMenuItem() {
            // Change track height by attribute
            final JMenuItem item = new JCheckBoxMenuItem("Show mismatched bases");


            item.setSelected(renderOptions.isShowMismatches());
            item.addActionListener(aEvt -> {
                renderOptions.setShowMismatches(item.isSelected());
                refresh();
            });
            add(item);
            return item;
        }


        void addInsertSizeMenuItem() {
            // Change track height by attribute
            final JMenuItem item = new JCheckBoxMenuItem("Set insert size options ...");
            item.addActionListener(aEvt -> {

                InsertSizeSettingsDialog dlg = new InsertSizeSettingsDialog(IGV.getMainFrame(), renderOptions);
                dlg.setModal(true);
                dlg.setVisible(true);
                if (!dlg.isCanceled()) {
                    renderOptions.setComputeIsizes(dlg.isComputeIsize());
                    renderOptions.setMinInsertSizePercentile(dlg.getMinPercentile());
                    renderOptions.setMaxInsertSizePercentile(dlg.getMaxPercentile());
                    if (renderOptions.computeIsizes) {
                        dataManager.updatePEStats(renderOptions);
                    }

                    renderOptions.setMinInsertSize(dlg.getMinThreshold());
                    renderOptions.setMaxInsertSize(dlg.getMaxThreshold());
                    refresh();
                }
            });


            item.setEnabled(dataManager.isPairedEnd());
            add(item);
        }

        void addShadeBaseByMenuItem() {

            final JMenuItem item = new JCheckBoxMenuItem("Shade base by quality");
            item.setSelected(renderOptions.getShadeBasesOption());
            item.addActionListener(aEvt -> UIUtilities.invokeOnEventThread(() -> {
                renderOptions.setShadeBasesOption(item.isSelected());
                refresh();
            }));
            add(item);
        }

        void addShowItems() {

            if (AlignmentTrack.this.coverageTrack != null) {
                final JMenuItem item = new JCheckBoxMenuItem("Show Coverage Track");
                item.setSelected(AlignmentTrack.this.coverageTrack.isVisible());
                item.setEnabled(!AlignmentTrack.this.coverageTrack.isRemoved());
                item.addActionListener(aEvt -> UIUtilities.invokeOnEventThread(() -> {
                    if (getCoverageTrack() != null) {
                        getCoverageTrack().setVisible(item.isSelected());
                        IGV.getInstance().getMainPanel().revalidate();
                    }
                }));
                add(item);
            }

            if (AlignmentTrack.this.spliceJunctionTrack != null) {
                final JMenuItem item = new JCheckBoxMenuItem("Show Splice Junction Track");
                item.setSelected(AlignmentTrack.this.spliceJunctionTrack.isVisible());
                item.setEnabled(!AlignmentTrack.this.spliceJunctionTrack.isRemoved());
                item.addActionListener(aEvt -> UIUtilities.invokeOnEventThread(() -> {
                    if (AlignmentTrack.this.spliceJunctionTrack != null) {
                        AlignmentTrack.this.spliceJunctionTrack.setVisible(item.isSelected());
                    }
                }));
                add(item);
            }

            final JMenuItem alignmentItem = new JMenuItem("Hide Alignment Track");
            alignmentItem.setEnabled(!AlignmentTrack.this.isRemoved());
            alignmentItem.addActionListener(e -> AlignmentTrack.this.setVisible(false));
            add(alignmentItem);
        }


        void addCopySequenceItem(final TrackClickEvent te) {
            // Change track height by attribute
            final JMenuItem item = new JMenuItem("Copy read sequence");
            add(item);

            final Alignment alignment = getSpecficAlignment(te);
            if (alignment == null) {
                item.setEnabled(false);
                return;
            }

            final String seq = alignment.getReadSequence();
            if (seq == null) {
                item.setEnabled(false);
                return;

            }

            item.addActionListener(aEvt -> StringUtils.copyTextToClipboard(seq));

        }


        void addBlatItem(final TrackClickEvent te) {
            // Change track height by attribute
            final JMenuItem item = new JMenuItem("Blat read sequence");
            add(item);

            final Alignment alignment = getSpecficAlignment(te);
            if (alignment == null) {
                item.setEnabled(false);
                return;
            }

            final String seq = alignment.getReadSequence();
            if (seq == null) {
                item.setEnabled(false);
                return;

            }

            item.addActionListener(aEvt -> {
                String blatSeq = alignment.getReadStrand() == Strand.NEGATIVE ?
                        SequenceTrack.getReverseComplement(seq) : seq;
                BlatClient.doBlatQuery(blatSeq, alignment.getReadName());
            });

        }

        void addBlatClippingItems(final TrackClickEvent te) {
            final Alignment alignment = getSpecficAlignment(te);
            if (alignment == null) {
                return;
            }

            int clippingThreshold = BlatClient.MINIMUM_BLAT_LENGTH;

            int[] clipping = SAMAlignment.getClipping(alignment.getCigarString());
            /* Add a "BLAT left clipped sequence" item if there is significant left clipping. */
            if (clipping[1] > clippingThreshold) {
                final JMenuItem lcItem = new JMenuItem("Blat left-clipped sequence");
                add(lcItem);

                lcItem.addActionListener(aEvt -> {
                    String lcSeq = alignment.getReadSequence().substring(0, clipping[1]);
                    if (alignment.getReadStrand() == Strand.NEGATIVE) {
                        lcSeq = SequenceTrack.getReverseComplement(lcSeq);
                    }
                    BlatClient.doBlatQuery(lcSeq, alignment.getReadName() + " - left clip");
                });
            }
            /* Add a "BLAT right clipped sequence" item if there is significant right clipping. */
            if (clipping[3] > clippingThreshold) {
                final JMenuItem lcItem = new JMenuItem("Blat right-clipped sequence");
                add(lcItem);

                lcItem.addActionListener(aEvt -> {
                    String seq = alignment.getReadSequence();
                    int seqLength = seq.length();

                    String rcSeq = seq.substring(seqLength - clipping[3], seqLength);
                    if (alignment.getReadStrand() == Strand.NEGATIVE) {
                        rcSeq = SequenceTrack.getReverseComplement(rcSeq);
                    }
                    BlatClient.doBlatQuery(rcSeq, alignment.getReadName() + " - right clip");
                });
            }
        }

        void addExtViewItem(final TrackClickEvent te) {
            // Change track height by attribute
            final JMenuItem item = new JMenuItem("ExtView");
            add(item);

            final Alignment alignment = getAlignment(te);
            if (alignment == null) {
                item.setEnabled(false);
                return;
            }

            final String seq = alignment.getReadSequence();
            if (seq == null) {
                item.setEnabled(false);
                return;

            }

            item.addActionListener(aEvt -> ExtendViewClient.postExtendView(alignment));

        }

        void addTenXItems() {

            addSeparator();

            final JMenuItem bxItem = new JCheckBoxMenuItem("View linked reads (BX)");
            final JMenuItem miItem = new JCheckBoxMenuItem("View linked reads (MI)");

            if (isLinkedReads()) {
                bxItem.setSelected("BX".equals(renderOptions.getLinkByTag()));
                miItem.setSelected("MI".equals(renderOptions.getLinkByTag()));
            } else {
                bxItem.setSelected(false);
                miItem.setSelected(false);
            }

            bxItem.addActionListener(aEvt -> {
                boolean linkedReads = bxItem.isSelected();
                setLinkedReads(linkedReads, "BX");
            });
            add(bxItem);

            miItem.addActionListener(aEvt -> {
                boolean linkedReads = miItem.isSelected();
                setLinkedReads(linkedReads, "MI");
            });
            add(miItem);

        }

        void addSupplItems() {


            addSeparator();

            final JMenuItem bxItem = new JCheckBoxMenuItem("Link supplementary alignments");

            if (isLinkedReads()) {
                bxItem.setSelected("READNAME".equals(renderOptions.getLinkByTag()));
            } else {
                bxItem.setSelected(false);
            }

            bxItem.addActionListener(aEvt -> {
                boolean linkedReads = bxItem.isSelected();
                setLinkedReads(linkedReads, "READNAME");
            });
            add(bxItem);
        }


        private void addInsertionItems(AlignmentBlock insertion) {

            final JMenuItem item = new JMenuItem("Copy insert sequence");
            add(item);
            item.addActionListener(aEvt -> StringUtils.copyTextToClipboard(new String(insertion.getBases())));

            if (insertion.getBases() != null && insertion.getBases().length >= 10) {
                final JMenuItem blatItem = new JMenuItem("Blat insert sequence");
                add(blatItem);
                blatItem.addActionListener(aEvt -> {
                    String blatSeq = new String(insertion.getBases());
                    BlatClient.doBlatQuery(blatSeq);
                });
            }
        }


    }


    private AlignmentBlock getInsertion(Alignment alignment, int pixelX) {
        if (alignment != null && alignment.getInsertions() != null) {
            for (AlignmentBlock block : alignment.getInsertions()) {
                if (block.containsPixel(pixelX)) {
                    return block;
                }
            }
        }
        return null;
    }

    static class InsertionMenu extends IGVPopupMenu {

        final AlignmentBlock insertion;

        InsertionMenu(AlignmentBlock insertion) {

            this.insertion = insertion;

            addCopySequenceItem();

            if (insertion.getBases() != null && insertion.getBases().length > 10) {
                addBlatItem();
            }
        }


        void addCopySequenceItem() {
            // Change track height by attribute
            final JMenuItem item = new JMenuItem("Copy insert sequence");
            add(item);
            item.addActionListener(aEvt -> StringUtils.copyTextToClipboard(new String(insertion.getBases())));
        }


        void addBlatItem() {
            // Change track height by attribute
            final JMenuItem item = new JMenuItem("Blat insert sequence");
            add(item);
            item.addActionListener(aEvt -> {
                String blatSeq = new String(insertion.getBases());
                BlatClient.doBlatQuery(blatSeq);
            });
            item.setEnabled(insertion.getBases() != null && insertion.getBases().length >= 10);
        }

        @Override
        public boolean includeStandardItems() {
            return false;
        }
    }


    public static class RenderOptions implements Cloneable, Persistable {

        public static final String NAME = "RenderOptions";

        private Boolean shadeBasesOption;
        private Boolean shadeCenters;
        private Boolean flagUnmappedPairs;
        private Boolean showAllBases;
        private Integer minInsertSize;
        private Integer maxInsertSize;
        private AlignmentTrack.ColorOption colorOption;
        private AlignmentTrack.GroupOption groupByOption;
        private Boolean viewPairs;
        private String colorByTag;
        private String groupByTag;
        private String sortByTag;
        private String linkByTag;
        private Boolean linkedReads;
        private Boolean quickConsensusMode;
        private Boolean showMismatches;
        private Boolean computeIsizes;
        private Double minInsertSizePercentile;
        private Double maxInsertSizePercentile;
        private Boolean pairedArcView;
        private Boolean flagZeroQualityAlignments;
        private Range groupByPos;
        private Boolean drawInsertionIntervals;


        AlignmentTrack.BisulfiteContext bisulfiteContext = BisulfiteContext.CG;
        Map<String, PEStats> peStats;

        DefaultValues defaultValues;
        private IGVPreferences prefs;

        public RenderOptions() {
            this(ExperimentType.OTHER);
        }

        RenderOptions(ExperimentType experimentType) {

            this.prefs = getPreferences(experimentType);
            //updateColorScale();
            peStats = new HashMap<>();
            defaultValues = new DefaultValues(prefs);
        }

        private <T extends Enum<T>> T getFromMap(Map<String, String> attributes, String key, Class<T> clazz, T defaultValue) {
            String value = attributes.get(key);
            if (value == null) {
                return defaultValue;
            }
            return CollUtils.valueOf(clazz, value, defaultValue);
        }

        private String getFromMap(Map<String, String> attributes, String key, String defaultValue) {
            String value = attributes.get(key);
            if (value == null) {
                return defaultValue;
            }
            return value;
        }

        void setShowAllBases(boolean showAllBases) {
            this.showAllBases = showAllBases;
        }

        void setShowMismatches(boolean showMismatches) {
            this.showMismatches = showMismatches;
        }

        void setMinInsertSize(int minInsertSize) {
            this.minInsertSize = minInsertSize;
            //updateColorScale();
        }

        public void setViewPairs(boolean viewPairs) {
            this.viewPairs = viewPairs;
        }

        void setComputeIsizes(boolean computeIsizes) {
            this.computeIsizes = computeIsizes;
        }


        void setMaxInsertSizePercentile(double maxInsertSizePercentile) {
            this.maxInsertSizePercentile = maxInsertSizePercentile;
        }

        void setMaxInsertSize(int maxInsertSize) {
            this.maxInsertSize = maxInsertSize;
        }

        void setMinInsertSizePercentile(double minInsertSizePercentile) {
            this.minInsertSizePercentile = minInsertSizePercentile;
        }

        void setColorByTag(String colorByTag) {
            this.colorByTag = colorByTag;
        }

        void setColorOption(AlignmentTrack.ColorOption colorOption) {
            this.colorOption = colorOption;
        }

        void setSortByTag(String sortByTag) {
            this.sortByTag = sortByTag;
        }

        void setGroupByTag(String groupByTag) {
            this.groupByTag = groupByTag;
        }

        void setGroupByPos(Range groupByPos) {
            this.groupByPos = groupByPos;
        }

        void setLinkByTag(String linkByTag) {
            this.linkByTag = linkByTag;
        }

        void setQuickConsensusMode(boolean quickConsensusMode) {
            this.quickConsensusMode = quickConsensusMode;
        }

        public void setGroupByOption(AlignmentTrack.GroupOption groupByOption) {
            this.groupByOption = (groupByOption == null) ? AlignmentTrack.GroupOption.NONE : groupByOption;
        }

        void setShadeBasesOption(boolean shadeBasesOption) {
            this.shadeBasesOption = shadeBasesOption;
        }

        void setLinkedReads(boolean linkedReads) {
            this.linkedReads = linkedReads;
        }

        public void setDrawInsertionIntervals(boolean drawInsertionIntervals) {
            this.drawInsertionIntervals = drawInsertionIntervals;
        }


        // getters
        public int getMinInsertSize() {
            return minInsertSize == null ? prefs.getAsInt(SAM_MIN_INSERT_SIZE_THRESHOLD) : minInsertSize;
        }

        public int getMaxInsertSize() {
            return maxInsertSize == null ? prefs.getAsInt(SAM_MAX_INSERT_SIZE_THRESHOLD) : maxInsertSize;
        }

        public boolean isFlagUnmappedPairs() {
            return flagUnmappedPairs == null ? prefs.getAsBoolean(SAM_FLAG_UNMAPPED_PAIR) : flagUnmappedPairs;
        }

        public boolean getShadeBasesOption() {
            return shadeBasesOption == null ? prefs.getAsBoolean(SAM_SHADE_BASES) : shadeBasesOption;
        }

        public boolean isShowMismatches() {
            return showMismatches == null ? prefs.getAsBoolean(SAM_SHOW_MISMATCHES) : showMismatches;
        }

        public boolean isShowAllBases() {
            return showAllBases == null ? prefs.getAsBoolean(SAM_SHOW_ALL_BASES) : showAllBases;
        }

        public boolean isShadeCenters() {
            return shadeCenters == null ? prefs.getAsBoolean(SAM_SHADE_CENTER) : shadeCenters;
        }

        boolean isDrawInsertionIntervals() {
            return drawInsertionIntervals == null ? prefs.getAsBoolean(SAM_SHOW_INSERTION_MARKERS) : drawInsertionIntervals;
        }

        public boolean isFlagZeroQualityAlignments() {
            return flagZeroQualityAlignments == null ? prefs.getAsBoolean(SAM_FLAG_ZERO_QUALITY) : flagZeroQualityAlignments;
        }

        public boolean isViewPairs() {
            return viewPairs == null ? defaultValues.viewPairs : viewPairs;
        }

        public boolean isComputeIsizes() {
            return computeIsizes == null ? prefs.getAsBoolean(SAM_COMPUTE_ISIZES) : computeIsizes;
        }

        public double getMinInsertSizePercentile() {
            return minInsertSizePercentile == null ? prefs.getAsFloat(SAM_MIN_INSERT_SIZE_PERCENTILE) : minInsertSizePercentile;
        }

        public double getMaxInsertSizePercentile() {
            return maxInsertSizePercentile == null ? prefs.getAsFloat(SAM_MAX_INSERT_SIZE_PERCENTILE) : maxInsertSizePercentile;
        }

        public AlignmentTrack.ColorOption getColorOption() {
            return colorOption == null ?
                    CollUtils.valueOf(AlignmentTrack.ColorOption.class, prefs.get(SAM_COLOR_BY), AlignmentTrack.ColorOption.NONE) :
                    colorOption;
        }

        public String getColorByTag() {
            return colorByTag == null ? prefs.get(SAM_COLOR_BY_TAG) : colorByTag;
        }

        String getSortByTag() {
            return sortByTag == null ? prefs.get(SAM_SORT_BY_TAG) : sortByTag;
        }

        public String getGroupByTag() {
            return groupByTag == null ? prefs.get(SAM_GROUP_BY_TAG) : groupByTag;
        }

        public Range getGroupByPos() {
            return groupByPos == null ? defaultValues.groupByPos : groupByPos;
        }

        public String getLinkByTag() {
            return linkByTag == null ? prefs.get(SAM_LINK_TAG) : linkByTag;
        }

        public AlignmentTrack.GroupOption getGroupByOption() {
            AlignmentTrack.GroupOption gbo = groupByOption;
            // Interpret null as the default option.
            gbo = (gbo == null) ?
                    CollUtils.valueOf(AlignmentTrack.GroupOption.class, prefs.get(SAM_GROUP_OPTION), AlignmentTrack.GroupOption.NONE) :
                    gbo;
            // Add a second check for null in case defaultValues.groupByOption == null
            gbo = (gbo == null) ? AlignmentTrack.GroupOption.NONE : gbo;

            return gbo;
        }

        public boolean isLinkedReads() {
            return linkedReads == null ? prefs.getAsBoolean(SAM_LINK_READS) : linkedReads;
        }

        public boolean isQuickConsensusMode() {
            return quickConsensusMode == null ? prefs.getAsBoolean(SAM_QUICK_CONSENSUS_MODE) : quickConsensusMode;
        }

        void refreshDefaults(ExperimentType experimentType) {
            this.prefs = getPreferences(experimentType);
            defaultValues = new DefaultValues(this.prefs);
        }

        @Override
        public void marshalXML(Document document, Element element) {

            if (shadeBasesOption != null) {
                element.setAttribute("shadeBasesOption", shadeBasesOption.toString());
            }
            if (shadeCenters != null) {
                element.setAttribute("shadeCenters", shadeCenters.toString());
            }
            if (flagUnmappedPairs != null) {
                element.setAttribute("flagUnmappedPairs", flagUnmappedPairs.toString());
            }
            if (showAllBases != null) {
                element.setAttribute("showAllBases", showAllBases.toString());
            }
            if (minInsertSize != null) {
                element.setAttribute("minInsertSize", minInsertSize.toString());
            }
            if (maxInsertSize != null) {
                element.setAttribute("maxInsertSize", maxInsertSize.toString());
            }
            if (colorOption != null) {
                element.setAttribute("colorOption", colorOption.toString());
            }
            if (groupByOption != null) {
                element.setAttribute("groupByOption", groupByOption.toString());
            }
            if (viewPairs != null) {
                element.setAttribute("viewPairs", viewPairs.toString());
            }
            if (colorByTag != null) {
                element.setAttribute("colorByTag", colorByTag);
            }
            if (groupByTag != null) {
                element.setAttribute("groupByTag", groupByTag);
            }
            if (sortByTag != null) {
                element.setAttribute("sortByTag", sortByTag);
            }
            if (linkByTag != null) {
                element.setAttribute("linkByTag", linkByTag);
            }
            if (linkedReads != null) {
                element.setAttribute("linkedReads", linkedReads.toString());
            }
            if (quickConsensusMode != null) {
                element.setAttribute("quickConsensusMode", quickConsensusMode.toString());
            }
            if (showMismatches != null) {
                element.setAttribute("showMismatches", showMismatches.toString());
            }
            if (computeIsizes != null) {
                element.setAttribute("computeIsizes", computeIsizes.toString());
            }
            if (minInsertSizePercentile != null) {
                element.setAttribute("minInsertSizePercentile", minInsertSizePercentile.toString());
            }
            if (maxInsertSizePercentile != null) {
                element.setAttribute("maxInsertSizePercentile", maxInsertSizePercentile.toString());
            }
            if (pairedArcView != null) {
                element.setAttribute("pairedArcView", pairedArcView.toString());
            }
            if (flagZeroQualityAlignments != null) {
                element.setAttribute("flagZeroQualityAlignments", flagZeroQualityAlignments.toString());
            }
            if (groupByPos != null) {
                element.setAttribute("groupByPos", groupByPos.toString());
            }
        }


        @Override
        public void unmarshalXML(Element element, Integer version) {
            if (element.hasAttribute("shadeBasesOption")) {
                String v = element.getAttribute("shadeBasesOption");
                if (v != null) {
                    shadeBasesOption = v.equalsIgnoreCase("quality") || v.equalsIgnoreCase("true");
                }
            }
            if (element.hasAttribute("shadeCenters")) {
                shadeCenters = Boolean.parseBoolean(element.getAttribute("shadeCenters"));
            }
            if (element.hasAttribute("showAllBases")) {
                showAllBases = Boolean.parseBoolean(element.getAttribute("showAllBases"));
            }
            if (element.hasAttribute("flagUnmappedPairs")) {
                flagUnmappedPairs = Boolean.parseBoolean(element.getAttribute("flagUnmappedPairs"));
            }

            if (element.hasAttribute("minInsertSize")) {
                minInsertSize = Integer.parseInt(element.getAttribute("minInsertSize"));
            }
            if (element.hasAttribute("maxInsertSize")) {
                maxInsertSize = Integer.parseInt(element.getAttribute("maxInsertSize"));
            }
            if (element.hasAttribute("colorOption")) {
                colorOption = ColorOption.valueOf(element.getAttribute("colorOption"));
            }
            if (element.hasAttribute("groupByOption")) {
                groupByOption = GroupOption.valueOf(element.getAttribute("groupByOption"));
            }
            if (element.hasAttribute("viewPairs")) {
                viewPairs = Boolean.parseBoolean(element.getAttribute("viewPairs"));
            }
            if (element.hasAttribute("colorByTag")) {
                colorByTag = element.getAttribute("colorByTag");
            }
            if (element.hasAttribute("groupByTag")) {
                groupByTag = element.getAttribute("groupByTag");
            }
            if (element.hasAttribute("sortByTag")) {
                sortByTag = element.getAttribute("sortByTag");
            }
            if (element.hasAttribute("linkByTag")) {
                linkByTag = element.getAttribute("linkByTag");
            }
            if (element.hasAttribute("linkedReads")) {
                linkedReads = Boolean.parseBoolean(element.getAttribute("linkedReads"));
            }
            if (element.hasAttribute("quickConsensusMode")) {
                quickConsensusMode = Boolean.parseBoolean(element.getAttribute("quickConsensusMode"));
            }
            if (element.hasAttribute("showMismatches")) {
                showMismatches = Boolean.parseBoolean(element.getAttribute("showMismatches"));
            }
            if (element.hasAttribute("computeIsizes")) {
                computeIsizes = Boolean.parseBoolean(element.getAttribute("computeIsizes"));
            }
            if (element.hasAttribute("minInsertSizePercentile")) {
                minInsertSizePercentile = Double.parseDouble(element.getAttribute("minInsertSizePercentile"));
            }
            if (element.hasAttribute("maxInsertSizePercentile")) {
                maxInsertSizePercentile = Double.parseDouble(element.getAttribute("maxInsertSizePercentile"));
            }
            if (element.hasAttribute("pairedArcView")) {
                pairedArcView = Boolean.parseBoolean(element.getAttribute("pairedArcView"));
            }
            if (element.hasAttribute("flagZeroQualityAlignments")) {
                flagZeroQualityAlignments = Boolean.parseBoolean(element.getAttribute("flagZeroQualityAlignments"));
            }
            if (element.hasAttribute("groupByPos")) {
                groupByPos = Range.fromString(element.getAttribute("groupByPos"));
            }
        }

        static class DefaultValues {
            final boolean viewPairs;
            final boolean pairedArcView;
            Range groupByPos;

            DefaultValues(IGVPreferences prefs) {
                String pos = prefs.get(SAM_GROUP_BY_POS);
                viewPairs = false;
                pairedArcView = false;
                if (pos != null) {
                    String[] posParts = pos.split(" ");
                    if (posParts.length != 2) {
                        this.groupByPos = null;
                    } else {
                        int posChromStart = Integer.parseInt(posParts[1]);
                        this.groupByPos = new Range(posParts[0], posChromStart, posChromStart + 1);
                    }
                }
            }
        }
    }

    @Override
    public void unmarshalXML(Element element, Integer version) {

        super.unmarshalXML(element, version);

        if (element.hasAttribute("experimentType")) {
            experimentType = ExperimentType.valueOf(element.getAttribute("experimentType"));
        }

        NodeList tmp = element.getElementsByTagName("RenderOptions");
        if (tmp.getLength() > 0) {
            Element renderElement = (Element) tmp.item(0);
            renderOptions = new RenderOptions();
            renderOptions.unmarshalXML(renderElement, version);
        }
    }


    @Override
    public void marshalXML(Document document, Element element) {

        super.marshalXML(document, element);

        if (experimentType != null) {
            element.setAttribute("experimentType", experimentType.toString());
        }

        Element sourceElement = document.createElement("RenderOptions");
        renderOptions.marshalXML(document, sourceElement);
        element.appendChild(sourceElement);


    }

}
