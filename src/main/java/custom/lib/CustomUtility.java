package custom.lib;

import org.broad.igv.feature.Strand;

public class CustomUtility {
    // Covert from string[] to HapData
    public static HapData CreateHapFromString(String[] args) {
        boolean[] nums = new boolean[args[3].length()];

        for (int i = 0; i < args[3].length(); i++) {
            nums[i] = args[3].charAt(i) == '1';
        }

        Strand strand = Strand.NONE;

        if (args[5].contains("*")) {
            strand = Strand.NONE;
        } else if (args[5].contains("+")) {
            strand = Strand.POSITIVE;
        } else if (args[5].contains("-")) {
            strand = Strand.NEGATIVE;
        }

        HapData hapData = new HapData(
                args[0],
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]),
                nums,
                Integer.parseInt(args[4]),
                strand
        );

        return hapData;
    }

    // Covert from string[] to CorrelationData
    public static CorrelationData CreateCorrelationFromString(String[] args) {
        return new CorrelationData(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Float.parseFloat(args[3]));
    }
}
