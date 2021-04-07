package com.equinoxe.sensoresinternoswifisw;

public class SensorData {
    private long timeStamp;
    private float v1;
    private float v2;
    private float v3;
    private double dModule;

    public void setData(long timeStamp, float v1, float v2, float v3) {
        this.timeStamp = timeStamp;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public double calculateModule() {
        dModule = Math.sqrt(v1*v1 + v2*v2 + v3*v3);

        return dModule;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public float getV1() {
        return v1;
    }

    public float getV2() {
        return v2;
    }

    public float getV3() {
        return v3;
    }

    public double getModule() {
        return dModule;
    }
}
