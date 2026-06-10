package com.bus.predictor.traffic.model;

public class KalmanFilter {

    private double x;
    private double p;
    private double q;
    private double r;
    private boolean initialized;

    public KalmanFilter() {
        this(0.01, 0.1);
    }

    public KalmanFilter(double processNoise, double measurementNoise) {
        this.q = processNoise;
        this.r = measurementNoise;
        this.p = 1.0;
        this.x = 0.0;
        this.initialized = false;
    }

    public double update(double measurement) {
        if (!initialized) {
            x = measurement;
            p = 1.0;
            initialized = true;
            return x;
        }

        p = p + q;

        double k = p / (p + r);

        x = x + k * (measurement - x);

        p = (1 - k) * p;

        return x;
    }

    public double getState() {
        return x;
    }

    public double getCovariance() {
        return p;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void reset() {
        x = 0.0;
        p = 1.0;
        initialized = false;
    }

    public void setProcessNoise(double q) {
        this.q = q;
    }

    public void setMeasurementNoise(double r) {
        this.r = r;
    }
}
