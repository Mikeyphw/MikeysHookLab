package com.mikeyphw.hooklab.chatgpt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;

/**
 * P05: read-only, ephemeral association between a verified CopyButtonImpl
 * composition, a callback constructed during it, and a synchronous callback
 * invocation. It never stores strings, uses equals(), or claims block identity.
 */
public final class CopyButtonCausalityTracker {
    public interface Clock { long nowMs(); }
    public enum Event {
        P03_NULL_CALLBACK, P03_NULL_SOURCE, P03_NOT_FUNCTION0,
        P03_BIND_AMBIGUOUS, P03_REUSED_CLASS, P03_CAP_REACHED,
        P03_INVOKE_MISSING, P03_INVOKE_RETURN_MISMATCH,
        P03_HOOK_INSTALLED, P03_HOOK_FAILED, P03_BIND_FAILED,
        P03_FRAME_MISSING, P03_CALLBACK_INVOKED,
        BUTTON_CONSTRUCTOR_BOUND, BUTTON_HANDLER_INVOKED,
        BUTTON_CLIPBOARD_MATCH, BUTTON_CLIPBOARD_NO_MATCH, BUTTON_CLIPBOARD_AMBIGUOUS
    }
    private static final long MAX_AGE_MS=120000L;
    private static final int MAX_BINDINGS=96;
    private static final int MAX_LOGS_PER_EVENT_ROLE=4;
    private final Clock clock;
    private final ArrayList<Binding> bindings=new ArrayList<>();
    private final ThreadLocal<RenderFrame> renderFrame=new ThreadLocal<>();
    private final ThreadLocal<InvocationFrame> invocationFrame=new ThreadLocal<>();
    private final EnumMap<Event,int[]> eventCounts=new EnumMap<>(Event.class);
    private long nextId=1;
    private volatile boolean maybeBound;

    private static final class Binding {
        final WeakReference<Object> callback;
        final long id, renderId, seenMs;
        Binding(Object value,long id,long renderId,long seenMs) {
            callback=new WeakReference<>(value); this.id=id; this.renderId=renderId;this.seenMs=seenMs;
        }
    }
    public static final class RenderFrame {
        private final Thread owner=Thread.currentThread();
        private final RenderFrame previous;
        public final long renderId;
        private RenderFrame(long renderId,RenderFrame previous) {
            this.renderId=renderId;this.previous=previous;
        }
    }
    public static final class InvocationFrame {
        private final Thread owner=Thread.currentThread();
        private final InvocationFrame previous;
        public final long bindingId, renderId;
        private InvocationFrame(long bindingId,long renderId,InvocationFrame previous) {
            this.bindingId=bindingId;this.renderId=renderId;this.previous=previous;
        }
    }
    public static final class Mark {
        public final long bindingId, renderId;
        private Mark(long bindingId,long renderId) {this.bindingId=bindingId;this.renderId=renderId;}
    }
    public CopyButtonCausalityTracker(Clock clock) {
        if(clock==null)throw new IllegalArgumentException("clock required");
        this.clock=clock;
    }
    public RenderFrame beginRender() {
        final long id;
        synchronized(this) {id=nextId++;}
        RenderFrame frame=new RenderFrame(id,renderFrame.get());
        renderFrame.set(frame);
        return frame;
    }
    public void endRender(RenderFrame frame) {
        if(frame==null || frame.owner!=Thread.currentThread() || renderFrame.get()!=frame)
            throw new IllegalStateException("Render frame mismatch");
        if(frame.previous==null)renderFrame.remove();else renderFrame.set(frame.previous);
    }
    private void prune(long now) {
        Iterator<Binding> it=bindings.iterator();
        while(it.hasNext()) {
            Binding b=it.next();
            if(b.callback.get()==null || now<b.seenMs || now-b.seenMs>MAX_AGE_MS)it.remove();
        }
        maybeBound=!bindings.isEmpty();
    }
    public boolean inRender() {return renderFrame.get()!=null;}
    public boolean hasCandidates() {return maybeBound;}
    /** Called ONLY after the exact seven-argument twl constructor succeeds. */
    public synchronized Mark constructed(Object callback) {
        RenderFrame current=renderFrame.get();
        if(current==null || callback==null)return null;
        long now=clock.nowMs();prune(now);
        for(Binding b:bindings)if(b.callback.get()==callback)return null;
        while(bindings.size()>=MAX_BINDINGS)bindings.remove(0);
        Binding b=new Binding(callback,nextId++,current.renderId,now);
        bindings.add(b);
        maybeBound=true;
        return new Mark(b.id,b.renderId);
    }
    public synchronized InvocationFrame enterHandler(Object callback) {
        if(callback==null)return null;
        prune(clock.nowMs());
        for(Binding b:bindings)if(b.callback.get()==callback) {
            InvocationFrame frame=new InvocationFrame(b.id,b.renderId,invocationFrame.get());
            invocationFrame.set(frame);
            return frame;
        }
        return null;
    }
    public InvocationFrame currentHandler() {return invocationFrame.get();}
    public void exitHandler(InvocationFrame frame) {
        if(frame==null || frame.owner!=Thread.currentThread() || invocationFrame.get()!=frame)
            throw new IllegalStateException("Invocation frame mismatch");
        if(frame.previous==null)invocationFrame.remove();else invocationFrame.set(frame.previous);
    }
    public synchronized int bindingCount() {prune(clock.nowMs());return bindings.size();}
    /** 0 means suppressed; only fixed enum codes and three reviewed roles are possible. */
    public synchronized int logCount(Event event,int role) {
        if(event==null)throw new IllegalArgumentException("event required");
        int slot=(role==10?1:role==14?2:0);
        int[] counts=eventCounts.get(event);
        if(counts==null) {counts=new int[3];eventCounts.put(event,counts);}
        int value=counts[slot];
        if(value<Integer.MAX_VALUE)counts[slot]=++value;
        return value<=MAX_LOGS_PER_EVENT_ROLE?value:0;
    }
    public synchronized int occurrenceCount(Event event,int role) {
        int[] counts=eventCounts.get(event);
        return counts==null?0:counts[role==10?1:role==14?2:0];
    }
}
