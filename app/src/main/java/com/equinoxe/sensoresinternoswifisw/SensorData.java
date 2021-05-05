package com.equinoxe.sensoresinternoswifisw;

public class SensorData {
    public static final int BYTES = Long.BYTES + 3*Float.BYTES;

    private long []timeStamp = new long[1];
    private float v1;
    private float v2;
    private float v3;
    private double dModule;
    private byte []bytes = new byte[BYTES];

    public void setData(long timeStamp, float []values) {
        this.timeStamp[0] = timeStamp;
        this.v1 = values[0];
        // Se tiene solo un valor si es el HR. Se ponen los otros dos a 0
        if (values.length > 1) {
            this.v2 = values[1];
            this.v3 = values[2];
        } else {
            this.v2 = 0.0f;
            this.v3 = 0.0f;
        }

        System.arraycopy(longToByteArray(timeStamp), 0, bytes, 0, Long.BYTES);
        System.arraycopy(floatToByteArray(v1), 0, bytes, Long.BYTES, Float.BYTES);
        System.arraycopy(floatToByteArray(v2), 0, bytes, Long.BYTES + Float.BYTES, Float.BYTES);
        System.arraycopy(floatToByteArray(v3), 0, bytes, Long.BYTES + 2*Float.BYTES, Float.BYTES);
    }

    private static byte[] floatToByteArray(float value) {
        int intBits =  Float.floatToIntBits(value);
        return new byte[] {
                (byte) (intBits >> 24), (byte) (intBits >> 16), (byte) (intBits >> 8), (byte) (intBits) };
    }

    private static byte[] longToByteArray(long value) {
        return new byte[] {
                (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) (value) };
    }


    public double calculateModuleGravity() {
        double v1G = v1 / 9.8;
        double v2G = v2 / 9.8;
        double v3G = v3 / 9.8;
        dModule = Math.sqrt(v1G*v1G + v2G*v2G + v3G*v3G);

        return dModule;
    }

    public double calculateModule() {
        dModule = Math.sqrt(v1*v1 + v2*v2 + v3*v3);

        return dModule;
    }

    public long getTimeStamp() {
        return timeStamp[0];
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

    public byte[] getBytes() {
        return bytes;
    }
}
