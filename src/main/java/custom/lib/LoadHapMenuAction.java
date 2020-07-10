package custom.lib;

import org.apache.log4j.Logger;
import org.broad.igv.feature.Strand;
import org.broad.igv.prefs.IGVPreferences;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.action.MenuAction;
import org.broad.igv.ui.panel.TrackPanel;
import org.broad.igv.ui.panel.TrackPanelScrollPane;
import org.broad.igv.ui.util.FileDialogUtils;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

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
        final File[] files = chooseTrackFiles();

        for (File file : files) {
            if (file.isFile()) {
                log.info("Load file from:" + file);

                // Record import time for benchmarking

                long startTime = System.currentTimeMillis();

                TSVReader tsvReader;

                if (file.getAbsolutePath().contains(".hap")) {
                    try {
                        tsvReader = new TSVReader(file);
                    } catch (FileNotFoundException fileNotFoundException) {
                        fileNotFoundException.printStackTrace();
                        log.info("Failed to load hap!");
                        continue;
                    }

                    String[] nextToken;

                    ArrayList<HapData> hapDataArrayList = new ArrayList<>();

                    do {
                        nextToken = tsvReader.nextTokens();

                        if (nextToken != null) {
                            int[] nums = new int[nextToken[3].length()];

                            for (int i = 0; i < nextToken[3].length(); i++) {
                                nums[i] = Integer.parseInt(String.valueOf(nextToken[3].charAt(i)));
                            }

                            Strand strand = Strand.NONE;

                            if (nextToken[5] == "*") {
                                strand = Strand.NONE;
                            } else if (nextToken[5] == "+") {
                                strand = Strand.POSITIVE;
                            } else if (nextToken[5] == "-") {
                                strand = Strand.NEGATIVE;
                            }

                            HapData hapData = new HapData(nextToken[0], Integer.parseInt(nextToken[1]), Integer.parseInt(nextToken[2]), nums, Integer.parseInt(nextToken[4]), strand);
                            hapDataArrayList.add(hapData);
                        }
                    }
                    while (nextToken != null);

                    TrackPanelScrollPane hapScrollPane = igv.addDataPanel("Hap Data");
                    hapScrollPane.setName("Hap visualization");

                    TrackPanel trackPanel = hapScrollPane.getTrackPanel();

                    HapTrack hapTrack = new HapTrack("Haplotype");
                    hapTrack.hapData = hapDataArrayList;
                    hapTrack.allCached = true;

                    HapTrack.Instances.add(hapTrack);


                    trackPanel.addTrack(hapTrack);

                    long endTime = System.currentTimeMillis();

                    log.info("Take: " + String.valueOf((endTime - startTime) * 0.001) + " s to load " + file.getAbsolutePath());
                } else {
                    log.info("Unsupported version");
                }
            }
        }


//        trackPanel.addTrack(GenomeManager.getInstance().getCurrentGenome().getSequence());

        // Re-do the layout
//        List<Map<TrackPanelScrollPane, Integer>>  trackPanelAttrs = IGV.getInstance().getTrackPanelAttrs();
//        igv.resetPanelHeights(trackPanelAttrs.get(0), trackPanelAttrs.get(1));
    }

    private File[] chooseTrackFiles() {

        File lastDirectoryFile = PreferencesManager.getPreferences().getLastTrackDirectory();

        // Get Track Files
        final IGVPreferences prefs = PreferencesManager.getPreferences();

        // Tracks.  Simulates multi-file select
        File[] trackFiles = FileDialogUtils.chooseMultiple("Select Files", lastDirectoryFile, null);

        if (trackFiles != null && trackFiles.length > 0) {

            File lastFile = trackFiles[0];
            if (lastFile != null) {
                PreferencesManager.getPreferences().setLastTrackDirectory(lastFile);
            }
        }
        igv.resetStatusMessage();
        return trackFiles;
    }
}
