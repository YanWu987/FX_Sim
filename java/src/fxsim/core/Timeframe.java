package fxsim.core;

/** 图表周期 */
public enum Timeframe {
    M1(1, "1分", "1M"),
    M5(5, "5分", "5M"),
    M15(15, "15分", "15M"),
    H1(60, "1时", "1H"),
    H4(240, "4时", "4H"),
    D1(1440, "日线", "1D");

    public final int minutes;
    public final String label;
    public final String shortLabel;

    Timeframe(int minutes, String label, String shortLabel) {
        this.minutes = minutes;
        this.label = label;
        this.shortLabel = shortLabel;
    }
}
