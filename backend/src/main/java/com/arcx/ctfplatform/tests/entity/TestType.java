package com.arcx.ctfplatform.tests.entity;

public enum TestType {
    CONTROL_POINT,
    LECTURE,
    KT;

    public boolean isControlPoint() {
        return this == CONTROL_POINT || this == KT;
    }
}
