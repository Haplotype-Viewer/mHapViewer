package custom.lib;

import htsjdk.tribble.readers.TabixReader;
import org.apache.log4j.Logger;
import org.broad.igv.prefs.IGVPreferences;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.action.MenuAction;
import org.broad.igv.ui.panel.TrackPanel;
import org.broad.igv.ui.panel.TrackPanelScrollPane;
import org.broad.igv.ui.util.FileDialogUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

public class LoadCorMenuAction extends MenuAction {
    static Logger log = Logger.getLogger(LoadHapMenuAction.class);
    IGV igv;

    public LoadCorMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    // Callback when menu is clicked!
    @Override
    public void actionPerformed(ActionEvent e) {
        File file = chooseTrackFile();

        if (file.isFile()) {
            try {
                TabixReader tabixReader = new TabixReader(file.getAbsolutePath());

                TrackPanelScrollPane hapScrollPane = igv.addDataPanel("Correlation Data");
                hapScrollPane.setName("Correlation visualization");

                TrackPanel trackPanel = hapScrollPane.getTrackPanel();

                CorrelationTrack correlationTrack = new CorrelationTrack("Correlation File (Streamed)");
                correlationTrack.tabixReader = tabixReader;

                CorrelationTrack.Instances.add(correlationTrack);
                trackPanel.addTrack(correlationTrack);

                IGV.getMainFrame().repaint();
            } catch (IOException ioException) {
                ioException.printStackTrace();
                JOptionPane.showConfirmDialog(null, "Failed to load *.gz file.", "Exception", JOptionPane.ERROR_MESSAGE);
                log.info("Failed to load gz!");
            }
        }
    }

    private File chooseTrackFile() {

        File lastDirectoryFile = PreferencesManager.getPreferences().getLastTrackDirectory();

        final IGVPreferences prefs = PreferencesManager.getPreferences();

        File trackFile = FileDialogUtils.chooseFile("Select Files", lastDirectoryFile, FileDialogUtils.LOAD);

        if (trackFile != null) {
            prefs.setLastTrackDirectory(trackFile);
        }

        igv.resetStatusMessage();

        return trackFile;
    }
}

