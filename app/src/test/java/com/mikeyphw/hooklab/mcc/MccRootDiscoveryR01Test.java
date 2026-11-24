package com.mikeyphw.hooklab.mcc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Execute the generated read-only POSIX shell probe, not a mocked Java reimplementation. */
public final class MccRootDiscoveryR01Test {
    private static final String DATA="/data/user/0/br.art.code.meucronogramacapilar";
    @Rule public final TemporaryFolder temp=new TemporaryFolder();

    private static final class Result {
        final int exit; final String output;
        Result(int exit,String output){this.exit=exit;this.output=output;}
    }
    private Result probe(Path primary,Path user0,Path alias) throws Exception {
        return probe(primary,user0,alias,0);
    }
    private Result probe(Path primary,Path user0,Path alias,int userId) throws Exception {
        String script=MccRefillCommand.script(DATA,true);
        assertTrue("expected pinned production data path",script.contains("data='"+DATA+"'"));
        assertTrue("expected pinned user detection",script.contains("case \"$data\" in /data/data/*) user=0 ;; "));
        // Change only the test subprocess's injected filesystem roots and UID;
        // the actual scan/validation logic is the production generated script.
        script=script.replace("data='"+DATA+"'","data='"+primary+"'");
        script=script.replace("[ \"$(id -u)\" = '0' ]", "[ \"0\" = '0' ]");
        script=script.replace("case \"$data\" in /data/data/*) user=0 ;; "
            + "/data/user/*) user=${data#/data/user/}; user=${user%%/*} ;; esac", "user="+userId);
        script=script.replace("/data/user/0/$pkg",user0.toString());
        script=script.replace("/data/data/$pkg",alias.toString());
        Process proc=new ProcessBuilder("sh","-c",script).redirectErrorStream(true).start();
        assertTrue("bounded probe",proc.waitFor(10,TimeUnit.SECONDS));
        String output=new String(proc.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        return new Result(proc.exitValue(),output);
    }
    private Path isolated(String name)throws Exception{
        Path p=temp.newFolder(name).toPath();
        return p;
    }
    private static Path balance(Path base,String prefix,String value)throws Exception{
        Path dir=base.resolve("databases"); Files.createDirectories(dir);
        Path path=dir.resolve(prefix+"-DiamondBalance");
        Files.write(path,value.getBytes(StandardCharsets.US_ASCII));
        return path;
    }
    @Test public void primaryKnownBalanceIsReadyWithoutAliasScan()throws Exception{
        Path primary=isolated("primary"); Path alias0=isolated("alias0"); Path alias1=isolated("alias1");
        Path file=balance(primary,"accountuser123","7");
        Result r=probe(primary,alias0,alias1);
        assertEquals(r.output,0,r.exit);
        assertTrue(r.output,r.output.contains("MCC_STORAGE_READY"));
        assertFalse(r.output,r.output.contains("MCC_STORAGE_FALLBACK_USED"));
        assertTrue(r.output,r.output.contains("MCC_SCAN_VISIBLE_PRIMARY=1"));
        assertTrue(r.output,r.output.contains("MCC_SCAN_VISIBLE_ALIAS=0"));
        assertEquals("7",Files.readString(file));
    }
    @Test public void fallbackAfterPrimaryEmptyIsClearlyIdentified()throws Exception{
        Path primary=isolated("primary"); Path user0=isolated("user0"); Path alias=isolated("alias");
        Files.createDirectories(primary.resolve("databases"));
        Path file=balance(user0,"accountuser123","3");
        Result r=probe(primary,user0,alias);
        assertEquals(r.output,0,r.exit);
        assertTrue(r.output,r.output.contains("MCC_STORAGE_FALLBACK_USED"));
        assertTrue(r.output,r.output.contains("MCC_SCAN_VISIBLE_PRIMARY=1"));
        assertTrue(r.output,r.output.contains("MCC_SCAN_VISIBLE_ALIAS=1"));
        assertTrue(r.output,r.output.contains("MCC_STORAGE_READY"));
        assertEquals("3",Files.readString(file));
    }
    @Test public void missingAllStorageDirectoriesIsNotMisreportedAsMissingFile()throws Exception{
        Result r=probe(isolated("none"),isolated("none2"),isolated("none3"));
        assertEquals(r.output,58,r.exit);
        assertTrue(r.output,r.output.contains("MCC_STORAGE_DIRECTORIES_UNAVAILABLE"));
        assertFalse(r.output,r.output.contains("MCC_BALANCE_FILE_NOT_FOUND"));
    }
    @Test public void existingEmptyDirectoryIsGenuineNoCandidate()throws Exception{
        Path primary=isolated("empty");
        Files.createDirectories(primary.resolve("databases"));
        Result r=probe(primary,isolated("none2"),isolated("none3"));
        assertEquals(r.output,51,r.exit);
        assertTrue(r.output,r.output.contains("MCC_BALANCE_FILE_NOT_FOUND"));
    }
    @Test public void incompatibleFilenameRefusesWithoutAliasFallback()throws Exception{
        Path primary=isolated("bad");
        balance(primary,"invalid.dot","2");
        Result r=probe(primary,isolated("none2"),isolated("none3"));
        assertEquals(r.output,56,r.exit);
        assertTrue(r.output,r.output.contains("MCC_NAME_UNRECOGNIZED"));
    }
    @Test public void twoDistinctAccountFilesStillRefuse()throws Exception{
        Path primary=isolated("two");
        balance(primary,"accountuser123","2");
        balance(primary,"accountuser456","8");
        Result r=probe(primary,isolated("none2"),isolated("none3"));
        assertEquals(r.output,53,r.exit);
        assertTrue(r.output,r.output.contains("MCC_MULTIPLE_ACCOUNTS"));
    }
    @Test public void otherAndroidUserNeverUsesUserZeroFallback()throws Exception{
        Path primary=isolated("user10");Path user0=isolated("user0");Path alias=isolated("alias");
        Files.createDirectories(primary.resolve("databases"));
        balance(user0,"accountuser123","3");
        Result r=probe(primary,user0,alias,10);
        assertEquals(r.output,51,r.exit);
        assertFalse(r.output,r.output.contains("MCC_STORAGE_FALLBACK_USED"));
    }
    @Test public void symbolicLinkDirectoryRefuses()throws Exception{
        Path primary=isolated("symlink");Path outside=isolated("outside");
        Files.createSymbolicLink(primary.resolve("databases"),outside);
        Result r=probe(primary,isolated("none2"),isolated("none3"));
        assertEquals(r.output,52,r.exit);
        assertTrue(r.output,r.output.contains("MCC_STORAGE_UNSAFE"));
    }
}
