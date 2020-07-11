package custom.lib;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.tabix.TabixIndex;
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
import java.io.*;
import java.util.ArrayList;
import java.util.List;

// Custom Hap Menu
public class LoadHapMenuAction extends MenuAction {
    static Logger log = Logger.getLogger(LoadHapMenuAction.class);
    IGV igv;

    public LoadHapMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    // Callback when menu is clicked!
    @Override
    public void actionPerformed(ActionEvent e) {
        JOptionPane.showConfirmDialog(null, "The software support *.hap and *.tbi format files. When you select the .tbi index file,you will need to select source file then.", "Tip", JOptionPane.OK_OPTION);

        File file = chooseTrackFile();

        if (file.isFile()) {
            log.info("Load file from:" + file);

            // Record import time for benchmarking
            long startTime = System.currentTimeMillis();

            // load file with index (Stream loading)
            if (file.getAbsolutePath().contains(".tbi")) {
                try {
                    // when open the tbi then find the source file
                    file = chooseTrackFile();

                    if (file.length() >= 1) {
                        TabixIndex tabixIndex = new TabixIndex(file);

                        TrackPanelScrollPane hapScrollPane = igv.addDataPanel("Hap Data");
                        hapScrollPane.setName("Hap visualization");

                        TrackPanel trackPanel = hapScrollPane.getTrackPanel();

                        HapTrack hapTrack = new HapTrack("Haplotype");
                        hapTrack.isHapDataCached = false;
                        hapTrack.tabixIndex = tabixIndex;
                        hapTrack.sourceFile = file;

                        HapTrack.Instances.add(hapTrack);
                        trackPanel.addTrack(hapTrack);

                    } else {
                        JOptionPane.showConfirmDialog(null, "Failed to load source files. Please locate the source file after selecting index file.", "Exception", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                    JOptionPane.showConfirmDialog(null, "Failed to load tbi file.", "Exception", JOptionPane.ERROR_MESSAGE);
                    log.info("Failed to load tbi!");
                }
            }
            // load all files into the cache
            else if (file.getAbsolutePath().contains(".hap")) {
                TSVReader tsvReader;

                try {
                    tsvReader = new TSVReader(file);
                } catch (FileNotFoundException fileNotFoundException) {
                    fileNotFoundException.printStackTrace();
                    JOptionPane.showConfirmDialog(null, "Failed to load hap file.", "Exception", JOptionPane.ERROR_MESSAGE);
                    log.info("Failed to load hap!");
                    return;
                }

                String[] nextToken;

                ArrayList<HapData> hapDataArrayList = new ArrayList<>();

                do {
                    nextToken = tsvReader.nextTokens();

                    if (nextToken != null) {
                        HapData hapData = CustomUtility.CreateHapFromString(nextToken);
                        hapDataArrayList.add(hapData);
                    }
                }
                while (nextToken != null);

                TrackPanelScrollPane hapScrollPane = igv.addDataPanel("Hap Data");
                hapScrollPane.setName("Hap visualization");

                TrackPanel trackPanel = hapScrollPane.getTrackPanel();

                HapTrack hapTrack = new HapTrack("Haplotype");
                hapTrack.cachedHapData = hapDataArrayList;
                hapTrack.isHapDataCached = true;

                HapTrack.Instances.add(hapTrack);

                trackPanel.addTrack(hapTrack);

                long endTime = System.currentTimeMillis();

                log.info("Take: " + String.valueOf((endTime - startTime) * 0.001) + " s to load " + file.getAbsolutePath());
            } else {
                JOptionPane.showConfirmDialog(null, "Unsupported format.", "Exception", JOptionPane.ERROR_MESSAGE);
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
