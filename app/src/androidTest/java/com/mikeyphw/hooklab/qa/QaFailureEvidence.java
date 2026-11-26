package com.mikeyphw.hooklab.qa;

import android.content.Context;
import com.mikeyphw.hooklab.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Instrumentation-only structured failure evidence; never exports raw messages or UI dumps. */
public final class QaFailureEvidence {
    private QaFailureEvidence() { }
    private static final String[] CHECKS={"TouchTargetSizeCheck","TextContrastCheck","DuplicateSpeakableTextCheck","SpeakableTextPresentCheck","RedundantDescriptionCheck","EditableContentDescCheck","ClickableSpanCheck","ImageContrastCheck","TraversalOrderCheck","AccessibilityViewCheckException"};
    private static final Set<String> TESTS=new LinkedHashSet<>(java.util.Arrays.asList("adaptiveJourney","boundedTimelinePerformance","connectedCommandsStaleCallbackAndClock"));
    public static void record(Context context,String test,String phase,Throwable failure){
        try{
            if(!TESTS.contains(test)||!phase.matches("[A-Z0-9_]{1,64}"))return;
            String source=context.getString(R.string.hooklab_build_source);if(!source.matches("[0-9a-f]{40}"))return;
            Set<String> categories=new LinkedHashSet<>(),checks=new LinkedHashSet<>(),frames=new LinkedHashSet<>();
            Throwable cause=failure;
            for(int i=0;i<8&&cause!=null;i++,cause=cause.getCause()){
                String name=cause.getClass().getName();
                if(name.matches("(?:java\\.lang|androidx\\.test\\.espresso(?:\\.[a-z]+)*|com\\.google\\.android\\.apps\\.common\\.testing\\.accessibility\\.framework(?:\\.[a-z]+)*)\\.[A-Za-z0-9_$]+"))categories.add(name);
                String message=cause.getMessage();if(message!=null)for(String check:CHECKS)if(message.contains(check))checks.add(check);
                for(StackTraceElement frame:cause.getStackTrace()){
                    String c=frame.getClassName(),m=frame.getMethodName(),f=frame.getFileName();int line=frame.getLineNumber();
                    if(frames.size()<32&&c.matches("com\\.mikeyphw\\.hooklab\\.(?:qa|ui)\\.[A-Za-z0-9_$]+")&&m.matches("[A-Za-z0-9_$]+")&&f!=null&&f.matches("[A-Za-z0-9_]+\\.java")&&line>0&&line<=10000)frames.add(c+"."+m+"("+f+":"+line+")");
                }
                if(cause.getCause()==cause)break;
            }
            JSONObject row=new JSONObject().put("schema","hooklab.qa.failure.v1").put("source",source).put("test",test).put("phase",phase)
                .put("exception_classes",new JSONArray(categories)).put("accessibility_checks",new JSONArray(checks)).put("source_frames",new JSONArray(frames));
            File root=new File(context.getFilesDir(),"uxr04-adaptive");if(!root.mkdirs()&&!root.isDirectory())return;
            File tmp=new File(root,"failure-detail.tmp"),out=new File(root,"failure-detail.json");
            Files.write(tmp.toPath(),row.toString().getBytes(StandardCharsets.UTF_8));Files.move(tmp.toPath(),out.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ignored){ /* Original assertion must remain primary. */ }
    }
}
