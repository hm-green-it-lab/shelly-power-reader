package edu.hm.greenit.tools.shelly;

public class Meter {
    /**
     * {
     *             			"power": 70.24,
     *             			"is_valid": true,
     *             			"timestamp": 1739294619,
     *             			"counters": [
     *             				71.380,
     *             				72.397,
     *             				71.324
     *             			],
     *             			"total": 18013
     *             		                    }
     */
    private double power;
    private double overpower;
    private boolean is_valid;
    private long timestamp;
    private long total;

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = power;
    }

    public boolean isIs_valid() {
        return is_valid;
    }

    public void setIs_valid(boolean is_valid) {
        this.is_valid = is_valid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

}
