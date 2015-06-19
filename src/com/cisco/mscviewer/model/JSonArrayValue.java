package com.cisco.mscviewer.model;

import java.util.ArrayList;

public class JSonArrayValue implements JSonValue {
    private final ArrayList<JSonValue> value;

    @SuppressWarnings("unchecked")
    public JSonArrayValue(ArrayList<JSonValue> al) {
        value = (ArrayList<JSonValue>) al.clone();
    }

    public JSonArrayValue() {
        value = new ArrayList<JSonValue>();
    }

    public ArrayList<JSonValue> value() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    public int size() {
        return value.size();
    }

}