package com.tresabhi.gyrocursor;

public class Vector3 {
    static final Vector3 i_hat = new Vector3(1, 0, 0);
    static final Vector3 j_hat = new Vector3(0, 0, 1);
    static final Vector3 k_hat = new Vector3(0, 1, 0);

    double x;
    double y;
    double z;

    public Vector3() {}

    public Vector3(double x, double y, double z) {
        set(x, y, z);
    }

    public Vector3 set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;

        return this;
    }

    public Vector3 set(float[] xyz) {
        x = xyz[0];
        y = xyz[1];
        z = xyz[2];

        return this;
    }

    public Vector3 copy(Vector3 b) {
        return set(b.x, b.y, b.z);
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3 multiply(double c) {
        x *= c;
        y *= c;
        z *= c;

        return this;
    }

    public Vector3 normalize() {
        return multiply(1 / this.magnitude());
    }

    public double dot(Vector3 b) {
        return x * b.x + y * b.y + z * b.z;
    }

    public Vector3 cross(Vector3 b) {
        return new Vector3(
                y * b.z - z * b.y,
                z * b.x - x * b.z,
                x * b.y - y * b.z
        );
    }
}
