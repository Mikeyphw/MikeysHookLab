package com.mikeyphw.hooklab.mcc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Root executes only under HookLab's UID. R02 inspection adds read-only context evidence. */
public final class MccRefillWorker {
    private static final int TIMEOUT_SECONDS=20;
    private MccRefillWorker(){}
    @SuppressWarnings("deprecation")
    private static String installedDataDir(Context context){
        try{ApplicationInfo info=context.getPackageManager().getApplicationInfo(MccIds.PACKAGE,0);return MccRefillCommand.checkedDataDir(info.dataDir);}
        catch(PackageManager.NameNotFoundException|SecurityException|IllegalArgumentException ex){return null;}
    }
    private static String relaunch(Context context){
        try{
            if(Build.VERSION.SDK_INT>=33)context.getPackageManager().getLaunchIntentSenderForPackage(MccIds.PACKAGE).sendIntent(context,0,null,null,null);
            else{Intent intent=context.getPackageManager().getLaunchIntentForPackage(MccIds.PACKAGE);if(intent==null)return "RELAUNCH_UNAVAILABLE";intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
            return "RELAUNCH_REQUESTED_NOT_VERIFIED";
        }catch(Throwable ex){return "RELAUNCH_FAILED_"+ex.getClass().getSimpleName();}
    }
    private static String failure(int code){switch(code){
        case 51:return "BALANCE_FILE_NOT_FOUND";
        case 52:return "UNSAFE_STORAGE_REFUSED";
        case 53:return "MULTIPLE_BALANCE_FILES_REFUSED";
        case 54:case 55:return "UNKNOWN_BALANCE_FORMAT_REFUSED";
        case 56:return "UNRECOGNIZED_ACCOUNT_FILE_REFUSED";
        case 57:return "BALANCE_CHANGED_DURING_OPERATION";
        case 58:return "STORAGE_DIRECTORY_UNAVAILABLE_FROM_HOOKLAB_ROOT";
        case 59:return "WRITE_VERIFICATION_FAILED";
        case 70:return "ROOT_NOT_GRANTED";
        default:return "ROOT_COMMAND_EXIT_"+code;
    }}
    /** Consume every byte concurrently; retain only fixed, short, non-sensitive markers. */
    static final class Output implements Runnable{
        private final InputStream in;
        boolean ready,written,already,fallback,failed,contextAmbiguous;
        int primaryVisible=-1,aliasVisible=-1;
        String context;
        Output(InputStream in){this.in=in;}
        private void line(String s){
            if(s.equals("MCC_STORAGE_READY"))ready=true;
            else if(s.equals("MCC_BALANCE_WRITE_VERIFIED"))written=true;
            else if(s.equals("MCC_BALANCE_ALREADY_NINE"))already=true;
            else if(s.equals("MCC_STORAGE_FALLBACK_USED"))fallback=true;
            else if(s.matches("MCC_SCAN_VISIBLE_PRIMARY=[0-3]"))primaryVisible=s.charAt(s.length()-1)-'0';
            else if(s.matches("MCC_SCAN_VISIBLE_ALIAS=[0-6]"))aliasVisible=s.charAt(s.length()-1)-'0';
            else{String c=MccRootContextProbe.parse(s);if(c!=null){if(context!=null)contextAmbiguous=true;else context=c;}}
        }
        @Override public void run(){
            StringBuilder b=new StringBuilder();boolean overflow=false;
            try(InputStream stream=in){byte[] bytes=new byte[1024];int n;while((n=stream.read(bytes))!=-1)for(int i=0;i<n;i++){
                int v=bytes[i]&255;if(v==10){if(!overflow)line(b.toString());b.setLength(0);overflow=false;}else if(b.length()<256&&!overflow&&v>=32&&v<=126)b.append((char)v);else overflow=true;
            }if(!overflow&&b.length()>0)line(b.toString());}
            catch(Exception ex){failed=true;}
        }
        String contextSuffix(){return "_R02_"+(context==null||contextAmbiguous?"CONTEXT_UNVERIFIED":context);}
    }
    private static String run(Context context,boolean probe){
        String dir=installedDataDir(context);if(dir==null)return "MCC_DATA_DIRECTORY_UNVERIFIED";
        Process process=null;Thread drain=null;
        try{
            String script=(probe?MccRootContextProbe.script(dir,android.os.Process.myPid()):"")+MccRefillCommand.script(dir,probe);
            process=new ProcessBuilder("su","-c",script).redirectErrorStream(true).start();
            Output output=new Output(process.getInputStream());drain=new Thread(output,"hooklab-mcc-root-output");drain.setDaemon(true);drain.start();
            if(!process.waitFor(TIMEOUT_SECONDS,TimeUnit.SECONDS)){process.destroyForcibly();return "ROOT_COMMAND_TIMEOUT";}
            int exit=process.exitValue();drain.join(2000L);
            if(drain.isAlive()||output.failed)return "ROOT_OUTPUT_UNVERIFIED";
            String suffix=probe?output.contextSuffix():"";
            if(exit!=0){String code=failure(exit);if((exit==51||exit==58)&&output.primaryVisible>=0&&output.aliasVisible>=0)code+="_P"+output.primaryVisible+"_A"+output.aliasVisible;return code+suffix;}
            if(probe)return (output.ready?(output.fallback?"BALANCE_FILE_READY_USER0_ALIAS":"BALANCE_FILE_READY"):"ROOT_PROBE_UNVERIFIED")+suffix;
            if(output.already)return "ALREADY_9_NO_RESTART";
            if(output.written)return "WRITE_9_VERIFIED_"+(output.fallback?"USER0_ALIAS_":"")+relaunch(context);
            return "ROOT_RESULT_UNVERIFIED";
        }catch(InterruptedException ex){Thread.currentThread().interrupt();return "ROOT_INTERRUPTED";}
        catch(Exception ex){return "ROOT_UNAVAILABLE_"+ex.getClass().getSimpleName();}
        finally{if(process!=null){process.destroy();try{process.getInputStream().close();}catch(Exception ignored){}}}
    }
    public static synchronized String inspect(Context context){String result=run(context,true);if(result.matches("[A-Za-z0-9_:.-]{1,160}"))android.util.Log.i("HookLabMCC","MCC_R02_RESULT="+result);return result;}
    public static synchronized String setNine(Context context){return run(context,false);}
}
