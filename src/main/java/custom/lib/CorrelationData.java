package custom.lib;

public class CorrelationData {
    public String chr;
    public int start;
    public int end;
    public float cor;

    public CorrelationData(String chr, int start, int end, float cor) {
        this.chr = chr;
        this.start = start;
        this.end = end;
        this.cor = cor;
    }
}
