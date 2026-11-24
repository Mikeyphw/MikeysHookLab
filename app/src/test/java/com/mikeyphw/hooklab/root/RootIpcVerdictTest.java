package com.mikeyphw.hooklab.root;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RootIpcVerdictTest {
 @Test public void transportReturnIsNotHealthSuccess(){for(String s:new String[]{"ROOT_PLATFORM_IPC_TRANSACT_REFUSED","ROOT_PLATFORM_IPC_SECURITY_REFUSED","ROOT_PLATFORM_IPC_REMOTE_FAILURE","ROOT_PLATFORM_IPC_BAD_RESPONSE","ROOT_PLATFORM_NOT_ROOT"}){assertEquals(s,RootContextCode.checked(s));assertFalse(RootContextCode.isContext(s));}}
 @Test public void validatesOnlyRedactedContext(){String s="ROOT_PLATFORM_UR_GR_KN_ID_CS_SM";assertTrue(RootContextCode.isContext(s));assertFalse(RootContextCode.isContext(s+" raw"));assertEquals("ROOT_PLATFORM_RESULT_UNVERIFIED",RootContextCode.checked(s+" raw"));assertFalse(RootContextCode.isContext(null));}
}
