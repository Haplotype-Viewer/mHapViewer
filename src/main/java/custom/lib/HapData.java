package custom.lib;

import org.broad.igv.feature.Strand;

public class HapData {
    public String chr;
    public int start;
    public int end;
    public int[] states;
    public int index;
    public Strand strand;

    public HapData(String _chr, int _start, int _end, int[] _states,int _index, Strand _strand) {
        this.chr = _chr;
        this.start = _start;
        this.end = _end;
        this.states = _states;
        this.index = _index;
        this.strand = _strand;
    }
}
