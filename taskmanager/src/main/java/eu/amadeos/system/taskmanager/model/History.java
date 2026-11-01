package eu.amadeos.system.taskmanager.model;

/** The last {@link #SIZE} values of one series, oldest first; empty places are 0. */
public final class History {
    /** One minute at one sample per second. */
    public static final int SIZE = 61;

    private final double[] values = new double[SIZE];
    private int count;

    public void add(double value) {
        System.arraycopy(values, 1, values, 0, SIZE - 1);
        values[SIZE - 1] = value;
        if (count < SIZE) count++;
    }

    /** The values, oldest first; the last one is the newest. */
    public double[] values() {
        return values.clone();
    }

    /** How many values were added, at most {@link #SIZE}. */
    public int count() {
        return count;
    }

    public double latest() {
        return values[SIZE - 1];
    }

    /** The largest value of the series, 0 if empty. */
    public double max() {
        double m = 0;
        for (double v : values) m = Math.max(m, v);
        return m;
    }
}
