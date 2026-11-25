package com.mikeyphw.hooklab.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Bounded navigation history independent of Android lifecycle and persistence. */
final class RouteTrail<T> {
    private static final int LIMIT=32;
    private final ArrayDeque<T> frames=new ArrayDeque<>();
    void push(T frame){if(frame==null)throw new IllegalArgumentException("null route");if(frames.size()==LIMIT)frames.removeFirst();frames.addLast(frame);}
    void clear(){frames.clear();}
    void removeMatching(Predicate<T> same){frames.removeIf(same);}
    T pop(Predicate<T> valid){while(!frames.isEmpty()){T frame=frames.removeLast();if(valid.test(frame))return frame;}return null;}
    T rewind(Predicate<T> exact){
        java.util.Iterator<T> cursor=frames.descendingIterator();
        T match=null;while(cursor.hasNext()){T frame=cursor.next();if(exact.test(frame)){match=frame;break;}}
        if(match==null)return null;
        while(frames.removeLast()!=match){/* discard intervening route frames */}
        return match;
    }
    List<T> snapshot(){return new ArrayList<>(frames);}
}
