package com.cisco.mscviewer.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import com.cisco.mscviewer.model.SimpleInterval;
import com.cisco.mscviewer.tree.Interval;

public class GraphData {
    private ArrayList<Point> data = new ArrayList<Point>();
    private float miny = Float.MAX_VALUE;
    private float maxy = Float.MIN_VALUE;
    private String xType = "", yType = "";
    private String name;
    
    public GraphData(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void add(int x, float y) {
        add(x, y, null);
    }

    public void add(long x, float y, Object o) {
        data.add(new Point(x, y, o));
        if (y < miny)
            miny = y;
        if (y > maxy)
            maxy = y;
    }
    
    public boolean isEmpty() {
        return data.isEmpty();
    }
    
    public Iterator<Point> getIterator() {
        return data.iterator();
    }

    public Interval getInterval(long x0, long x1) {
        Point p = new Point(x0, 0f, null);
        int i = Collections.binarySearch(data, p);
        int firstIdx = (i>=0) ? i: -(i+1);
        p.x = x1;
        i = Collections.binarySearch(data, p);
        int lastIdx = (i>=0) ? i+1: -(i+1);
        return new SimpleInterval(firstIdx, lastIdx);
    }
    

    public int size() {
        return data.size();
    }

    public long minX() {
        return data.size() != 0 ? data.get(0).x : 0;
    }

    public float minY() {
        return miny;
    }

    public long maxX() {
        if (data.size() == 0)
            return 0;
        return data.get(data.size()-1).x;
    }

    public float maxY() {
        return maxy;
    }

    public Point point(int idx) {
        return idx < data.size() ? data.get(idx) : data.get(data.size()-1);
    }


    public void setXType(String l) {
        xType = l;
    }

    public String getXType() {
        return xType;
    }
    
    public void setYType(String l) {
        yType = l;
    }
    
    public String getYType() {
        return yType;
    }
    
    public int xToIndex(long x) {
        Point p = new Point(x, 0f, null);
        int i = Collections.binarySearch(data, p);
        return (i<0) ? -1: i; 
    }

    public int insertionIndex(long x) {
        Point p = new Point(x, 0f, null);
        int i = Collections.binarySearch(data, p);
        if (i>=0) {
            // if x is same, binarySearch() does not guarantee which one
            // is found. we want deterministic behavior, so we return
            // always the rightmost index
            for(i++; i<data.size() && data.get(i).x == x; i++)
                ;
            return i-1;
        }
        return -(i+1); 
    }

    public int closestIndex(long x) {
        Point p = new Point(x, 0f, null);
        int i = Collections.binarySearch(data, p);
        if (i >= 0)
            return i;
        else {
            i = -(i+1);
            if (i == data.size()) 
                return data.size()-1;
            if (i == 0)
                return 0;
            return (x - data.get(i-1).x < data.get(i).x - x)? i-1 : i;
        }    
    }

    public float y(long x) throws IllegalArgumentException {
        int i = xToIndex(x);
        return data.get(i).y;
    }

}
