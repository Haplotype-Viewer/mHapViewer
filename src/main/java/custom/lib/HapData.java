package custom.lib;

import org.broad.igv.feature.Strand;

public class HapData {
    public String chr;
    public int start;
    public int end;
    public boolean[] states;
    public short readCount;
    public Strand strand;

    public HapData(String _chr, int _start, int _end, boolean[] _states, short _readCount, Strand _strand) {
        this.chr = _chr;
        this.start = _start;
        this.end = _end;
        this.states = _states;
        this.readCount = _readCount;
        this.strand = _strand;
    }
}
