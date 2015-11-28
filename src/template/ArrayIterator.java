package template;

import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Iterates over an int array from min to max given the tasks of a vehicle
 */
class ArrayIterator implements Iterator<Object> {

    private List<Integer> l;
    private HashSet<Integer> tasks = new HashSet<Integer>();

    ArrayIterator(Integer[] time, HashSet<Integer> t) {
        l = new ArrayList<Integer>(Collections.nCopies(time.length, -1));
        for (Integer i: t) {
            tasks.add(new Integer(i));
        }
        for(Integer ti: tasks) {
            l.set(ti, time[ti]);
            l.set(ti + time.length/2, time[ti + time.length/2]);
        }
    }

    @Override
    public boolean hasNext() {
        for (Integer i: tasks) {
            if(l.get(i) != -1 || l.get(i+l.size()/2) != -1) return true;
        }
        return false;
    }

    @Override
    public Integer next() {
        int min = Integer.MAX_VALUE;
        Integer index = -1;

        for(Integer t: tasks) {
            if(l.get(t) != -1 && l.get(t) < min) {
                min = l.get(t);
                index = t;
            }
            if(l.get(t+l.size()/2) != -1 && l.get(t+l.size()/2) < min) {
                min = l.get(t+l.size()/2);
                index = t+l.size()/2;
            }
        }

        l.set(index, -1); //remove
        return min;
    }

    @Override
    public void remove() {

    }
}
