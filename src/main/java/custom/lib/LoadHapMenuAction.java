package custom.lib;

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.prefs.IGVPreferences;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.SequenceTrack;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.action.LoadFilesMenuAction;
import org.broad.igv.ui.action.MenuAction;
import org.broad.igv.ui.panel.TrackPanel;
import org.broad.igv.ui.panel.TrackPanelScrollPane;
import org.broad.igv.ui.util.FileDialogUtils;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Custom Hap Menu
public class LoadHapMenuAction extends MenuAction {
    static Logger log = Logger.getLogger(LoadHapMenuAction.class);
    IGV igv;

    public LoadHapMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        log.info("Event performed");

        TrackPanelScrollPane hapScrollPane = igv.addDataPanel("Hap Data");

        hapScrollPane.setName("Hap visualization");

        TrackPanel trackPanel = hapScrollPane.getTrackPanel();

        HapTrack hapTrack = new HapTrack("Haplotype");
        HapTrack.Instance = hapTrack;

        trackPanel.addTrack(hapTrack);

//        trackPanel.addTrack(GenomeManager.getInstance().getCurrentGenome().getSequence());

        // Re-do the layout
//        List<Map<TrackPanelScrollPane, Integer>>  trackPanelAttrs = IGV.getInstance().getTrackPanelAttrs();
//        igv.resetPanelHeights(trackPanelAttrs.get(0), trackPanelAttrs.get(1));
    }
}
