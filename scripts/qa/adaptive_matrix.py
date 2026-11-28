#!/usr/bin/env python3
"""Extended, hosted-only production-Activity matrix. No approval or device claims."""
from __future__ import annotations
import argparse, hashlib, json, math, os, re, shutil, subprocess, sys, time, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
import native_smoke as n
import secrets
import rotation_bridge
CASES={
 'api29-compact':(29,320,700,2.0,'light'),
 'api29-standard':(29,390,844,1.0,'dark'),
 'api36-compact':(36,320,700,2.0,'dark'),
 'api36-360':(36,360,800,1.6,'light'),
 'api36-390':(36,390,844,1.0,'dark'),
 'api36-393':(36,393,852,2.0,'amoled'),
 'api36-600':(36,600,1000,1.6,'dynamic'),
 'api36-840':(36,840,1100,2.0,'light'),
 'api36-landscape':(36,844,390,1.6,'system'),
 'api36-tablet-landscape':(36,1100,840,1.0,'dark'),
}
ROUTES=['home','hooks','group','hook','diagnostics','settings','scope','about','help','privacy','mcc']
JOURNEY='com.mikeyphw.hooklab.qa.AdaptiveUiTest#adaptiveJourney'
PERF='com.mikeyphw.hooklab.qa.AdaptiveUiTest#boundedTimelinePerformance'
# The baseline fixtures provide real Activity state/negative paths; execute again on
# the primary adaptive case, not simply reuse a different run's result.
FIXTURES=[n.FIXTURE,n.FRAMEWORK_TEST,n.TERMUX_TEST]
DENSITY=320

def require(ok,msg):n.require(ok,msg)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,x):p.write_text(json.dumps(x,sort_keys=True,indent=2)+'\n')
def roster(case):return [JOURNEY,PERF]+(FIXTURES if case=='api36-390' else [])
def config(case):
 api,w,h,font,theme=CASES[case];return dict(api=api,width_px=w*2,height_px=h*2,density_dpi=DENSITY,font_scale=font,theme=theme)
# Only fixed phase IDs, selected framework exception classes and source-frame
# locations are exported. Raw Espresso view dumps and test output remain private.
PHASES=frozenset(['SETUP','VIEWPORT_READY','LAUNCH_HOME','HOME_SCROLL','HOME_SHORTCUT_VISIBLE','OPEN_HOOKS','SEARCH_OPEN','SEARCH_TYPE','SEARCH_CLOSE_IME','SEARCH_RECREATE','SEARCH_ROTATE','SEARCH_ROTATE_REQUEST','SEARCH_ROTATE_SETTLE','SEARCH_ROTATE_RETURN','SEARCH_ROTATE_RETURN_SETTLE','SEARCH_CLEAR','CATALOG_HIT_TARGETS','OPEN_GROUP','OPEN_HOOK','OPEN_DIAGNOSTICS','IMPORT_OPEN','IMPORT_DIALOG_READY','IMPORT_CANCEL','IMPORT_DISMISSED','OPEN_SETTINGS','THEME_CANCEL','SETTINGS_KEYBOARD_FOCUS','OPEN_SCOPE','SOURCE_PROVENANCE','OPEN_HELP','PRIVACY_RECREATE','HELP_RETURN_SETTLE','MCC_CANCEL','PERF_IMPORT','PERF_LAUNCH','PERF_PAGE','PERF_NATIVE_FRAMES','PERF_WRITE']+['CAPTURE_'+x.upper() for x in ROUTES])
EXCEPTIONS=frozenset(['java.lang.AssertionError','java.lang.IllegalStateException','androidx.test.espresso.PerformException','androidx.test.espresso.NoMatchingViewException','androidx.test.espresso.AmbiguousViewMatcherException','androidx.test.espresso.base.RootViewPicker$RootViewWithoutFocusException','androidx.test.espresso.base.DefaultFailureHandler$AssertionFailedWithCauseError','com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckException','androidx.test.espresso.accessibility.AccessibilityViewCheckException'])
def failure_info(output,test,rc,reason=None):
 if isinstance(output,bytes):output=output.decode('utf-8','replace')
 output=output or ''
 phases=[];frames=[];codes=[];categories=[];termination=[]
 for line in output.splitlines():
  clean=line.strip()
  marker={'INSTRUMENTATION_RESULT: shortMsg=Process crashed.':'PROCESS_CRASH_REPORTED',
          'INSTRUMENTATION_RESULT: shortMsg=Process killed.':'PROCESS_KILLED_REPORTED',
          'INSTRUMENTATION_ABORTED':'RUNNER_ABORTED',
          'INSTRUMENTATION_FAILED':'RUNNER_FAILED'}.get(clean)
  if marker and marker not in termination:termination.append(marker)
  m=re.fullmatch(r'(?:INSTRUMENTATION_STATUS: stack=|Caused by: )?java\.lang\.AssertionError: ADAPTIVE_PHASE_([A-Z0-9_]+)',clean)
  if m and m[1] in PHASES and m[1] not in phases:phases.append(m[1])
  m=re.fullmatch(r'at (com\.mikeyphw\.hooklab\.[\w.$]+\([\w]+\.java:\d+\))',clean)
  if m and m[1] not in frames and len(frames)<32:frames.append(m[1])
  m=re.fullmatch(r'INSTRUMENTATION_(STATUS_CODE|CODE): (-?\d+)',clean)
  if m and len(codes)<32 and -1000<=int(m[2])<=1000:codes.append([m[1],int(m[2])])
  m=re.match(r'^(?:INSTRUMENTATION_STATUS: stack=|Caused by: )?([\w.$]+)(?::|$)',clean)
  if m and m[1] in EXCEPTIONS and m[1] not in categories:categories.append(m[1])
 terminal=any(kind=='CODE' for kind,_ in codes)
 if reason is None:reason='TRANSPORT_FAILED' if rc!=0 else 'TEST_FAILED' if terminal else 'TERMINAL_RESULT_MISSING'
 return {'schema':'hooklab.uxr04.instrumentation-failure.v1','test':test,'transport_exit':rc,'reason':reason,'terminal_result_present':terminal,'termination_markers':termination,'phases':phases,'exception_classes':categories,'status_codes':codes,'source_frames':frames}
def checkpoint_phase(source,test):
 if test not in (JOURNEY,PERF):return None
 try:
  data=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/phase.json')
  if not 0<len(data)<=4096:return None
  row=json.loads(data)
  if set(row)=={'schema','phase','test','source'} and row['schema']=='hooklab.uxr04.phase.v1' and row['source']==source and row['test']==test.split('#')[1] and row['phase'] in PHASES:return row['phase']
 except (OSError,ValueError,TypeError,subprocess.SubprocessError):pass
 return None

def home_geometry_checkpoint(source):
 try:
  raw=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/home-targets.json')
  if not 0<len(raw)<=65536:return None
  row=json.loads(raw)
  if set(row)!={'schema','source','targets'} or row['schema']!='hooklab.uxr04.home-targets.v1' or row['source']!=source:return None
  targets=row['targets'];keys={'id','class','width_px','height_px','visible_width_px','visible_height_px','minimum_px'}
  if not isinstance(targets,list) or not 0<len(targets)<=128:return None
  for target in targets:
   if not isinstance(target,dict) or set(target)!=keys or not isinstance(target['class'],str) or not re.fullmatch(r'(?:android|androidx|com\.android\.internal|com\.google\.android\.material|com\.mikeyphw\.hooklab)\.[A-Za-z0-9_.$]{1,180}',target['class']):return None
   if type(target['id']) is not int or not -1<=target['id']<2**31 or any(type(target[k]) is not int or not 0<=target[k]<=10000 for k in keys-{'class','id'}):return None
   if target['minimum_px']!=96:return None
  return row
 except (OSError,ValueError,TypeError,KeyError,subprocess.SubprocessError):return None

def viewport_failure(source,test):
 try:
  raw=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/viewport-failure.json');require(0<len(raw)<=4096,'viewport failure receipt size')
  row=json.loads(raw);keys={'schema','source','test','width_px','height_px','rotation','density_dpi','width_dp','height_dp','font_scale'}
  if not isinstance(row,dict) or set(row)!=keys or row['schema']!='hooklab.uxr04.viewport-failure.v1' or row['source']!=source or row['test']!=test.split('#')[-1]:return None
  if any(type(row[k]) is not int or not 0<row[k]<=10000 for k in ('width_px','height_px','density_dpi','width_dp','height_dp')):return None
  if type(row['rotation']) is not int or row['rotation'] not in range(4) or not finite(row['font_scale']) or not 0<row['font_scale']<=5:return None
  return row
 except (OSError,ValueError,TypeError,KeyError,subprocess.SubprocessError):return None

def catalog_readiness(source,test,phase):
 if test!=JOURNEY or phase not in ('OPEN_HOOKS','SEARCH_OPEN'):return None
 try:
  raw=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/catalog-readiness.json')
  if not 0<len(raw)<=4096:return None
  row=json.loads(raw);flags={'window_focused','hooks_selected','hooks_title','search_attached','search_laid_out'}
  if set(row)!=flags|{'schema','source','test','phase'} or row['schema']!='hooklab.uxr04.catalog-readiness.v1' or row['source']!=source or row['test']!='adaptiveJourney' or row['phase']!=phase:return None
  if not all(type(row[k]) is bool for k in flags) or all(row[k] for k in flags):return None
  return row
 except (OSError,ValueError,TypeError,KeyError,subprocess.SubprocessError):return None

def rotation_readiness(source,phase):
 if phase not in ('SEARCH_ROTATE_SETTLE','SEARCH_ROTATE_RETURN_SETTLE'):return None
 try:
  raw=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/rotation-readiness.json');require(0<len(raw)<=4096,'rotation readiness size')
  row=json.loads(raw);flags={'single_resumed_main','window_ready','display_rotation','display_size','configuration_orientation','search_attached','search_focused','search_text_matches','search_visible','ime_hidden','hooks_navigation','stable_samples'}
  if set(row)!=flags|{'schema','source','phase','test'} or row['schema']!='hooklab.uxr04.rotation-readiness.v1' or row['source']!=source or row['phase']!=phase or row['test']!='adaptiveJourney':return None
  if not all(type(row[k]) is bool for k in flags-{'stable_samples'}) or type(row['stable_samples']) is not int or not 0<=row['stable_samples']<2:return None
  return row
 except (OSError,ValueError,TypeError,KeyError,subprocess.SubprocessError):return None

def help_readiness(source,test,phase):
 if test!=JOURNEY or phase!='HELP_RETURN_SETTLE':return None
 try:
  raw=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/help-readiness.json');require(0<len(raw)<=4096,'help readiness size')
  row=json.loads(raw);flags={'window_focused','help_title','mcc_action_attached','mcc_action_ready'}
  if set(row)!=flags|{'schema','source','test','phase','stable_samples'} or row['schema']!='hooklab.uxr04.help-readiness.v1' or row['source']!=source or row['phase']!=phase or row['test']!='adaptiveJourney':return None
  if not all(type(row[k]) is bool for k in flags) or type(row['stable_samples']) is not int or not 0<=row['stable_samples']<2:return None
  return row
 except (OSError,ValueError,TypeError,KeyError,subprocess.SubprocessError):return None

ROTATION_OBSERVATIONS=40
ROTATION_REASSERT_AT=10
ROTATION_POLL_SECONDS=.25

def lock_matrix_rotation(cfg):
 rotation='1' if cfg['width_px']>cfg['height_px'] else '0'
 # Android's settings and WindowManager readbacks may settle on different
 # ticks after wm size/density changes or a preceding UiAutomation session.
 # Reassert only this exact requested lock once; never rerun instrumentation,
 # select a different orientation, accept "free", or swallow ADB errors.
 def request():
  n.adb('shell','settings','put','system','accelerometer_rotation','0')
  n.adb('shell','settings','put','system','user_rotation',rotation)
  if cfg['api']>=36:n.adb('shell','wm','user-rotation','lock',rotation)
 request();stable=0;wm_ok=False;settings_ok=False
 for poll in range(ROTATION_OBSERVATIONS):
  if poll==ROTATION_REASSERT_AT:request();stable=0
  wm_ok=cfg['api']<36 or n.txt('shell','wm','user-rotation').strip()=='lock '+rotation
  sensor=n.txt('shell','settings','get','system','accelerometer_rotation').strip()
  user=n.txt('shell','settings','get','system','user_rotation').strip()
  settings_ok=sensor=='0' and user==rotation
  stable=stable+1 if wm_ok and settings_ok else 0
  if stable==2:return
  if poll+1<ROTATION_OBSERVATIONS:time.sleep(ROTATION_POLL_SECONDS)
 # Fixed booleans only; raw platform output never becomes exported evidence.
 print('ROTATION_LOCK_REFUSED '+json.dumps({'api':cfg['api'],'requested':int(rotation),'wm_confirmed':wm_ok,'settings_confirmed':settings_ok,'stable_samples':stable,'observations':ROTATION_OBSERVATIONS},sort_keys=True),flush=True)
 require(wm_ok and settings_ok and stable==2,'matrix rotation lock/settings not stably confirmed')

def run_instrumentation(test,cfg,source):
 nonce=secrets.token_hex(16)
 bridge=rotation_bridge.Bridge(cfg,source,nonce,lock_matrix_rotation) if test==JOURNEY else None
 args=['adb','-s',n.SERIAL,'shell','am','instrument','-w','-r','-e','class',test,'-e','uxr04_theme',cfg['theme'],'-e','uxr04_source',source,'-e','uxr04_width_px',str(cfg['width_px']),'-e','uxr04_height_px',str(cfg['height_px']),'-e','uxr04_font_scale',str(cfg['font_scale']),'-e','uxr04_rotation_nonce',nonce,n.PKG+'.test/androidx.test.runner.AndroidJUnitRunner']
 if bridge:bridge.start()
 try:
  result=subprocess.run(args,capture_output=True,text=True,timeout=900)
 except BaseException:
  if bridge:bridge.finish(False)
  raise
 if bridge:
  # A runner failure still retains the original status/partial checkpoint.
  try:n.parse_instrumentation(result.stdout,test);complete=result.returncode==0
  except ValueError:complete=False
  if not bridge.finish(complete):
   result.rotation_bridge_failed=True
  result.rotation_bridge_diagnostic=bridge.diagnostic()
 return result

def instrument(test,cfg,source,out):
 lock_matrix_rotation(cfg)
 # A checkpoint from a different invocation must never be accepted as current.
 n.clear_failure_checkpoint()
 if test in (JOURNEY,PERF):n.adb('shell','run-as',n.PKG,'rm','-f','files/uxr04-adaptive/phase.json','files/uxr04-adaptive/phase.tmp','files/uxr04-adaptive/home-targets.json','files/uxr04-adaptive/viewport-failure.json','files/uxr04-adaptive/catalog-readiness.json','files/uxr04-adaptive/rotation-request.json','files/uxr04-adaptive/rotation-request.tmp','files/uxr04-adaptive/rotation-readiness.json','files/uxr04-adaptive/help-readiness.json')
 output='';rc=None;reason=None;result=None
 try:
  result=run_instrumentation(test,cfg,source)
  rc=result.returncode;output=result.stdout
  require(rc==0,'instrument transport failed: '+test)
  n.parse_instrumentation(output,test)
  if getattr(result,'rotation_bridge_failed',False):reason='ROTATION_BRIDGE_REFUSED'
 except subprocess.TimeoutExpired as error:
  output=error.stdout;reason='TIMEOUT'
 except OSError:
  reason='SPAWN_FAILED'
 except ValueError:
  reason=failure_info(output,test,rc)['reason']
 if reason is not None:
  failure=failure_info(output,test,rc,reason);failure['checkpoint_phase']=checkpoint_phase(source,test);failure['cause_checkpoint']=n.failure_checkpoint(source,test,PHASES|n.FAILURE_STAGES)
  # A test assertion and host-bridge failure are independent observations.
  if result is not None and hasattr(result,'rotation_bridge_diagnostic'):
   failure['rotation_bridge']=result.rotation_bridge_diagnostic
  if test==JOURNEY and failure['checkpoint_phase'] in ('SEARCH_ROTATE_SETTLE','SEARCH_ROTATE_RETURN_SETTLE'):
   failure['rotation_readiness']=rotation_readiness(source,failure['checkpoint_phase'])
  if test==JOURNEY and failure['checkpoint_phase']=='HOME_SCROLL':failure['home_target_geometry']=home_geometry_checkpoint(source)
  if failure['checkpoint_phase']=='HELP_RETURN_SETTLE':failure['help_readiness']=help_readiness(source,test,failure['checkpoint_phase'])
  if failure['checkpoint_phase']=='VIEWPORT_READY':failure['viewport_geometry']=viewport_failure(source,test)
  if failure['checkpoint_phase'] in ('OPEN_HOOKS','SEARCH_OPEN'):failure['catalog_readiness']=catalog_readiness(source,test,failure['checkpoint_phase'])
  dump(out/'failure.json',failure)
  print('ADAPTIVE_FAILURE '+json.dumps(failure,sort_keys=True),flush=True)
  raise ValueError('instrumentation failed: '+test) from None
 print('ADAPTIVE_TEST_PASS '+test,flush=True)
def pull(out,name):
 b=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/'+name);require(0<len(b)<=16*1024*1024,'missing or oversized case evidence');(out/name).write_bytes(b)
def hierarchy(xml,cfg):
 # No text or content-desc bytes survive the projection. Only boolean semantics
 # and actual visible screen bounds are exported from the clean test process.
 root=ET.fromstring(xml);require(root.tag=='hierarchy','unexpected UIAutomator hierarchy')
 nodes=[]
 for row in root.iter('node'):
  if row.get('package')!=n.PKG:continue
  b=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',row.get('bounds',''));require(b is not None,'invalid hierarchy bounds')
  x,y,r,t=map(int,b.groups());require(0<=x<=r<=cfg['width_px'] and 0<=y<=t<=cfg['height_px'],'hierarchy outside viewport')
  if x==r or y==t:continue
  nodes.append({'bounds':[x,y,r,t],'clickable':row.get('clickable')=='true','enabled':row.get('enabled')=='true','focusable':row.get('focusable')=='true','checked':row.get('checked')=='true','labelled':bool(row.get('text') or row.get('content-desc'))})
 require(nodes and any(x['clickable'] and x['enabled'] for x in nodes),'empty actionable hierarchy')
 return nodes

def capture_hierarchy(out,route,cfg):
 raw=n.raw('exec-out','run-as',n.PKG,'cat','files/uxr04-adaptive/'+route+'.xml')
 require(0<len(raw)<=4*1024*1024,'hierarchy size refused');dump(out/(route+'.hierarchy.json'),hierarchy(raw,cfg))
 n.adb('shell','run-as',n.PKG,'rm','files/uxr04-adaptive/'+route+'.xml')

def finite(x):return type(x) in (int,float) and math.isfinite(x) and x>=0
def p95(xs):require(len(xs)>0 and all(finite(x) for x in xs),'invalid performance measurements');return sorted(xs)[math.ceil(.95*len(xs))-1]
def live_xml():
 # Only a disposable offline emulator with the verified APK is eligible.
 path='/data/local/tmp/hooklab-uxr04-window.xml';n.adb('shell','uiautomator','dump',path)
 try:
  raw=n.raw('exec-out','cat',path);require(0<len(raw)<=4*1024*1024,'UI dump missing');return ET.fromstring(raw)
 finally:n.adb('shell','rm','-f',path)

# Fixed resource identities for actual app controls. System dialog rows may
# expose a text child and an actionable ancestor; deduplicate that same owner.
CONTROL_IDS={k:n.PKG+':id/hooklab_'+v for k,v in {
 'primary_navigation':'primary_navigation','capture_tab':'capture_tab','capture_target':'capture_target',
 'capture_research':'capture_research','capture_module':'capture_module',
 'capture_package':'capture_package','capture_stop':'capture_stop','capture_mark':'capture_mark',
 'capture_interrupted':'capture_interrupted'}.items()}

def bounds(node):
 m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
 if not m:return None
 x,y,r,b=map(int,m.groups());return (x,y,r,b) if x<r and y<b else None

def control_nodes(root,resource,*,action=False):
 return [node for node in root.iter('node') if node.get('package')==n.PKG and node.get('resource-id')==CONTROL_IDS[resource] and bounds(node) is not None and (not action or node.get('enabled')=='true' and node.get('clickable')=='true')]

def action_nodes(root,label,*,scope=None):
 if scope is not None:
  containers=control_nodes(root,scope);require(len(containers)==1,'process-death control scope missing/ambiguous: '+scope);root=containers[0]
 parents={child:parent for parent in root.iter() for child in parent};owners=set()
 for node in root.iter('node'):
  if node.get('package') not in (n.PKG,'android') or label not in (node.get('text'),node.get('content-desc')):continue
  owner=node
  while owner is not None:
   # Never turn a heading or arbitrary nonactionable label into a tap on
   # its enclosing scroll container. Require an actual enabled click owner.
   if owner.get('scrollable')=='true':break
   if owner.get('clickable')=='true' and owner.get('enabled')=='true' and owner.get('package') in (n.PKG,'android') and bounds(owner):owners.add(owner);break
   owner=parents.get(owner)
 return sorted([bounds(x) for x in owners])

def tap_control(selector,cfg,*,resource=None,scope=None,scroll=False):
 for _ in range(8):
  root=live_xml()
  matches=[bounds(x) for x in control_nodes(root,resource,action=True)] if resource else action_nodes(root,selector,scope=scope)
  if len(matches)==1:
   x,y,r,b=matches[0];require(0<=x<r<=cfg['width_px'] and 0<=y<b<=cfg['height_px'],'process-death control outside viewport: '+selector)
   n.adb('shell','input','tap',str((x+r)//2),str((y+b)//2));return
  require(len(matches)==0,'ambiguous process-death UI control: '+selector)
  if not scroll:break
  scroll_capture(cfg,root=root)
 raise ValueError('process-death control unavailable: '+selector)

def scroll_capture(cfg,*,root=None):
 w,h=cfg['width_px'],cfg['height_px']
 # Scroll the actual dialog/list viewport rather than tapping/swiping outside
 # it. The app may expose nested scroll containers; prefer the smallest visible
 # enabled scroll owner, which is the active dialog when it is present.
 candidates=[] if root is None else [bounds(x) for x in root.iter('node') if x.get('package')==n.PKG and x.get('scrollable')=='true' and x.get('enabled')=='true' and bounds(x)]
 if candidates:
  l,t,r,b=min(candidates,key=lambda x:(x[2]-x[0])*(x[3]-x[1]));require(0<=l<r<=w and 0<=t<b<=h,'capture scroll owner outside viewport');x=(l+r)//2;y0=t+(b-t)*3//4;y1=t+(b-t)//4
 else:x=w//2;y0=h*3//4;y1=h//3
 n.adb('shell','input','swipe',str(x),str(y0),str(x),str(y1),'400')

def launch():
 text=n.txt('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',n.PKG+'/.ui.MainActivity')
 require('Status: ok' in text,'Activity launch refused');return text

def process_death(cfg):
 # Start a real import-only capture through the normal app UI, kill its actual
 # background process (not ActivityScenario.recreate), and restore its task.
 n.adb('shell','am','force-stop',n.PKG);launch()
 tap_control('Diagnostics',cfg,scope='primary_navigation')
 tap_control('Guided capture',cfg,resource='capture_tab',scroll=True)
 tap_control('Choose target',cfg,resource='capture_target',scroll=True)
 tap_control('Unregistered package (research only)',cfg,resource='capture_research',scroll=True)
 # Verify the specific real dialog input before using shell input.
 field=control_nodes(live_xml(),'capture_package');require(len(field)==1,'capture package input missing/ambiguous')
 x,y,r,b=bounds(field[0]);n.adb('shell','input','tap',str((x+r)//2),str((y+b)//2));n.adb('shell','input','text','com.example.fixture');tap_control('OK',cfg)
 tap_control('LSPosed module log',cfg,resource='capture_module',scroll=True)
 for _ in range(8):
  root=live_xml()
  if control_nodes(root,'capture_stop',action=True) or control_nodes(root,'capture_mark',action=True):break
  scroll_capture(cfg)
 else:raise ValueError('capture did not start')
 before=n.txt('shell','pidof',n.PKG);require(re.fullmatch('[1-9][0-9]*',before) is not None,'process identity ambiguous')
 n.adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(2);n.adb('shell','am','kill',n.PKG)
 for _ in range(15):
  q=subprocess.run(['adb','-s',n.SERIAL,'shell','pidof',n.PKG],capture_output=True,text=True,timeout=10)
  if q.returncode==1 and not q.stdout.strip():break
  time.sleep(1)
 else:raise ValueError('background process did not die')
 launch();after=n.txt('shell','pidof',n.PKG);require(re.fullmatch('[1-9][0-9]*',after) and before!=after,'process restart not proven')
 # A task restored to the capture route must report interruption, not an
 # invented live or completed capture. Do not accept a fresh Home screen.
 for _ in range(8):
  root=live_xml()
  if len(control_nodes(root,'capture_interrupted'))==1:break
  w,h=cfg['width_px'],cfg['height_px'];n.adb('shell','input','swipe',str(w//2),str(h*3//4),str(w//2),str(h//3),'400')
 else:raise ValueError('restored capture interruption not visible')
 require(not control_nodes(root,'capture_stop') and not control_nodes(root,'capture_mark'),'interrupted capture not represented truthfully')
 return {'old_pid':int(before),'new_pid':int(after),'background_process_killed':True,'capture_interrupted':True,'active_controls_absent':True}

def frame_durations(evidence):
 require(isinstance(evidence,dict) and set(evidence)=={'schema','workload','requested_scroll_steps','completed_scroll_steps','dropped_callbacks','samples'},'native frame evidence schema drift')
 require(evidence['schema']=='hooklab.uxr04.native-frames.v1' and evidence['workload']=='timeline_scroll_4000_events','native frame workload drift')
 require(type(evidence['requested_scroll_steps']) is int and evidence['requested_scroll_steps']==80 and type(evidence['completed_scroll_steps']) is int and evidence['completed_scroll_steps']==80,'native frame workload incomplete')
 require(type(evidence['dropped_callbacks']) is int and evidence['dropped_callbacks']>=0,'invalid native callback drop count')
 rows=evidence['samples'];require(isinstance(rows,list) and 20<=len(rows)<=512,'insufficient completed native frame samples')
 frames=[];previous=0
 for row in rows:
  require(isinstance(row,dict) and set(row)=={'intended_vsync_ns','duration_ns'},'native frame record drift')
  stamp,duration=row['intended_vsync_ns'],row['duration_ns']
  require(type(stamp) is int and previous<stamp<2**63-1 and type(duration) is int and 0<duration<2**63-1,'invalid or duplicate completed native frame')
  frames.append(duration/1_000_000);previous=stamp
 return frames

def await_cold_process_absence():
 # am force-stop may return while the old PID is still observable on API29.
 # Poll only for absence: do not issue another force-stop or launch attempt.
 # Two consecutive clean negative samples are required; malformed output and
 # transport errors fail immediately instead of being treated as absence.
 stable=0
 for _ in range(20):
  q=subprocess.run(['adb','-s',n.SERIAL,'shell','pidof',n.PKG],capture_output=True,text=True,timeout=10)
  value=q.stdout.strip()
  require(not q.stderr.strip(),'cold launch process query transport failure')
  if q.returncode==1 and not value:
   stable+=1
   if stable==2:return
  else:
   require(q.returncode==0 and re.fullmatch(r'[1-9][0-9]*(?:\s+[1-9][0-9]*)*',value) is not None,'cold launch process query invalid')
   stable=0
  time.sleep(.25)
 raise ValueError('cold launch preexisting process')

def launch_performance(perf):
 starts=[];pids=[];frames=frame_durations(perf['native_frames'])
 for _ in range(5):
  n.adb('shell','am','force-stop',n.PKG)
  await_cold_process_absence()
  text=launch();m=re.search(r'(?m)^TotalTime: ([1-9][0-9]*)$',text);require(m is not None,'cold launch duration missing');starts.append(int(m[1]))
  pid=n.txt('shell','pidof',n.PKG);require(re.fullmatch('[1-9][0-9]*',pid) is not None and int(pid) not in pids,'cold launch process identity drift');pids.append(int(pid))
 return {'cold_launch_ms':starts,'cold_launch_pids':pids,'cold_launch_p95_ms':p95(starts),'frame_ms':frames,'frame_p95_ms':p95(frames),'frame_workload':perf['native_frames']['workload'],'budget_enforced':False}

APK_FILES={'app':'app.apk','test':'android-test.apk'}
APK_SCHEMA='hooklab.uxr04.shared-apks.v1'

def apk_identity(expected):
 return {'schema':APK_SCHEMA,**{k:expected[k] for k in ('source_sha','source_tree','repository','run_id','run_attempt')}}

def apk_file(path):
 require(path.is_file() and not path.is_symlink() and 0<path.stat().st_size<=100*1024*1024,'shared APK missing/unsafe')
 with zipfile.ZipFile(path) as archive:
  require('AndroidManifest.xml' in archive.namelist() and 'classes.dex' in archive.namelist(),'invalid shared APK contents')
 return {'sha256':sha(path),'size':path.stat().st_size}

def shared_apks(folder,expected):
 require(folder is not None and folder.is_dir() and not folder.is_symlink(),'shared APK directory required')
 require(set(x.name for x in folder.iterdir())==set(APK_FILES.values())|{'apk-build.json'},'shared APK file roster drift')
 receipt=folder/'apk-build.json';require(receipt.is_file() and not receipt.is_symlink() and 0<receipt.stat().st_size<=16384,'shared APK manifest unsafe')
 data=json.loads(receipt.read_text());actual={**apk_identity(expected),'apks':{name:apk_file(folder/file) for name,file in APK_FILES.items()}}
 require(data==actual,'shared APK build identity/digest drift');return actual

def publish_apks(folder,expected):
 require(not folder.exists(),'fresh shared APK output required');folder.mkdir(parents=True)
 for name,pattern in [('app','app/build/outputs/apk/debug/*.apk'),('test','app/build/outputs/apk/androidTest/debug/*.apk')]:
  source=n.one(pattern);apk_file(source);shutil.copyfile(source,folder/APK_FILES[name])
 dump(folder/'apk-build.json',{**apk_identity(expected),'apks':{name:apk_file(folder/file) for name,file in APK_FILES.items()}})
 return shared_apks(folder,expected)

def validate(out,case,expected,build=None):
 from PIL import Image
 r=json.loads((out/'receipt.json').read_text());cfg=config(case)
 require(all(r.get(k)==v for k,v in expected.items()) and r.get('case')==case and r.get('configuration')==cfg,'case identity mismatch')
 require(r.get('status')=='PASS_EXTENDED_NOT_DEVICE_APPROVAL' and r.get('completed_tests')==roster(case),'case roster incomplete')
 for k in ('app_apk_sha256','android_test_apk_sha256','installed_apk_sha256','installed_test_apk_sha256'):
  require(re.fullmatch('[a-f0-9]{64}',r.get(k,'')) is not None,'missing APK identity')
 require(r['app_apk_sha256']==r['installed_apk_sha256'] and r['android_test_apk_sha256']==r['installed_test_apk_sha256'],'installed APK drift')
 require(build is not None and r.get('apk_build')==build and r['app_apk_sha256']==build['apks']['app']['sha256'] and r['android_test_apk_sha256']==build['apks']['test']['sha256'],'case shared APK identity drift')
 names={'journey.json','performance.json','launch-performance.json'}|({'process-death.json'} if case=='api36-390' else set())|{x+s for x in ROUTES for s in ('.png','.hierarchy.json')}
 require(set(p.name for p in out.iterdir())==names|{'receipt.json'} and all(p.is_file() and not p.is_symlink() for p in out.iterdir()),'case file roster drift')
 require(r.get('files')=={x:sha(out/x) for x in sorted(names)},'case file digest drift')
 j=json.loads((out/'journey.json').read_text());require(j.get('schema')=='hooklab.uxr04.adaptive.v1' and j.get('api')==cfg['api'] and j.get('theme')==cfg['theme'] and j.get('source')==expected['source_sha'] and j.get('framework_disconnected') is True and j.get('physical_device_qualified') is False,'journey identity drift')
 require([x.get('route') for x in j.get('routes',[])]==ROUTES,'journey route roster drift')
 require(all(j.get(k) is True for k in ('ime_observed','orientation_round_trip','separate_catalog_targets')),'interaction evidence incomplete')
 for row in j['routes']:
  require(abs(row['font_scale']-cfg['font_scale'])<.01 and cfg['width_px']/2-80<=row['width_dp']<=cfg['width_px']/2 and cfg['height_px']/2-100<=row['height_dp']<=cfg['height_px']/2 and row['orientation']==(2 if cfg['width_px']>cfg['height_px'] else 1),'configuration not observed in actual Activity')
  nodes=json.loads((out/(row['route']+'.hierarchy.json')).read_text());require(isinstance(nodes,list) and 0<len(nodes)<=20000,'invalid sanitized hierarchy')
  for node in nodes:
   require(isinstance(node,dict) and set(node)=={'bounds','clickable','enabled','focusable','checked','labelled'} and all(type(node[k]) is bool for k in ('clickable','enabled','focusable','checked','labelled')),'invalid hierarchy node')
   b=node['bounds'];require(isinstance(b,list) and len(b)==4 and all(type(x) is int for x in b) and 0<=b[0]<b[2]<=cfg['width_px'] and 0<=b[1]<b[3]<=cfg['height_px'],'invalid hierarchy node bounds')
  require(any(x['clickable'] and x['enabled'] for x in nodes),'no visible action in hierarchy')
  require(all(finite(row[k]) for k in ('width_dp','height_dp','font_scale','headings','actions','focusable','visible_actions','text_contrast_min','primary_contrast')),'invalid geometry measurements')
  if row['route'] in ('home','hooks','diagnostics'):require(row.get('rail') is (row['width_dp']>=760),'navigation configuration mismatch')
  require(row['headings']>0 and row['actions']>0 and row['focusable']>0 and row['visible_actions']>0 and 4.5<=row['text_contrast_min']<=21 and 4.5<=row['primary_contrast']<=21,'accessibility evidence incomplete')
  with Image.open(out/(row['route']+'.png')) as im:require(im.format=='PNG' and im.size==(cfg['width_px'],cfg['height_px']) and any(lo!=hi for lo,hi in im.convert('RGB').getextrema()),'blank or wrong-sized screenshot')
 perf=json.loads((out/'performance.json').read_text());require(perf.get('schema')=='hooklab.uxr04.performance.v2' and perf.get('events')==4000 and perf.get('dropped')==1 and perf.get('page_size')==30 and perf.get('budget_enforced') is False and finite(perf.get('import_ms')),'performance fixture drift')
 require(len(perf['page_action_ms'])==len(perf['sampled_pss_kb'])==5 and all(finite(x) and x>0 for x in perf['sampled_pss_kb']),'performance sample roster drift')
 launch_perf=json.loads((out/'launch-performance.json').read_text());frames=frame_durations(perf['native_frames'])
 require(len(launch_perf['cold_launch_ms'])==5 and all(type(x) is int and x>0 for x in launch_perf['cold_launch_ms']) and launch_perf['cold_launch_p95_ms']==p95(launch_perf['cold_launch_ms']),'cold launch evidence drift')
 pids=launch_perf['cold_launch_pids'];require(isinstance(pids,list) and len(pids)==5 and all(type(x) is int and x>0 for x in pids) and len(set(pids))==5,'cold launch PID evidence drift')
 require(launch_perf['frame_ms']==frames and launch_perf['frame_p95_ms']==p95(frames) and launch_perf['frame_workload']==perf['native_frames']['workload'] and launch_perf['budget_enforced'] is False,'launch/frame evidence drift')
 if case=='api36-390':
  d=json.loads((out/'process-death.json').read_text());require(type(d['old_pid']) is int and type(d['new_pid']) is int and d['old_pid']>0 and d['new_pid']>0 and d['old_pid']!=d['new_pid'] and all(d.get(k) is True for k in ('background_process_killed','capture_interrupted','active_controls_absent')),'real process-death evidence missing')
 require(finite(j['scenario_launch_ms']),'invalid launch sample')
 require(r['performance_summary']=={'scenario_launch_ms':j['scenario_launch_ms'],'page_action_p95_ms':p95(perf['page_action_ms']),'sampled_peak_pss_kb':max(perf['sampled_pss_kb']),'cold_launch_p95_ms':launch_perf['cold_launch_p95_ms'],'frame_p95_ms':launch_perf['frame_p95_ms'],'budget_enforced':False},'performance summary drift')
 return r

def run(out,case,apks):
 cfg=config(case);ident=n.expected_identity();ident['schema']='hooklab.uxr04.case.v1';require(not out.exists(),'fresh output required');out.mkdir(parents=True)
 receipt={**ident,'case':case,'configuration':cfg,'status':'IN_PROGRESS','completed_tests':[]};dump(out/'receipt.json',receipt)
 n.guard(cfg['api']);require(not any(os.environ.get(k) for k in ('HOOKLAB_SIGNING_KEYSTORE_PATH','HOOKLAB_SIGNING_STORE_PASSWORD','HOOKLAB_SIGNING_KEY_ALIAS','HOOKLAB_SIGNING_KEY_PASSWORD')),'release credentials refused')
 for pkg in (n.PKG,n.PKG+'.test'):
  q=subprocess.run(['adb','-s',n.SERIAL,'shell','pm','path',pkg],capture_output=True,text=True,timeout=20);require(not q.stdout.strip(),'preexisting APK refused')
 build=shared_apks(apks,ident);receipt['apk_build']=build;dump(out/'receipt.json',receipt)
 app=apks/APK_FILES['app'];test=apks/APK_FILES['test']
 for p in (app,test):n.install_apk(p)
 ah,th=sha(app),sha(test);receipt.update(app_apk_sha256=ah,android_test_apk_sha256=th,installed_apk_sha256=n.installed_digest(n.PKG,ah),installed_test_apk_sha256=n.installed_digest(n.PKG+'.test',th))
 # Override natural portrait size, then rotate the real display for landscape.
 natural=f"{min(cfg['width_px'],cfg['height_px'])}x{max(cfg['width_px'],cfg['height_px'])}"
 n.adb('shell','settings','put','system','accelerometer_rotation','0');n.adb('shell','settings','put','system','user_rotation','1' if cfg['width_px']>cfg['height_px'] else '0')
 n.adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1');n.adb('shell','wm','size',natural);n.adb('shell','wm','density',str(DENSITY));n.adb('shell','settings','put','system','font_scale',str(cfg['font_scale']));n.adb('shell','cmd','uimode','night','yes')
 for name in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):n.adb('shell','settings','put','global',name,'0')
 require(n.txt('shell','wm','size').splitlines()[-1]==f'Override size: {natural}' and n.txt('shell','wm','density').splitlines()[-1]==f'Override density: {DENSITY}' and abs(float(n.txt('shell','settings','get','system','font_scale'))-cfg['font_scale'])<.01,'viewport readback mismatch')
 for method in roster(case):
  try:instrument(method,cfg,ident['source_sha'],out)
  except (ValueError,subprocess.SubprocessError):
   receipt.update(status='FAIL_INSTRUMENTATION',failed_test=method);dump(out/'receipt.json',receipt);raise
  receipt['completed_tests'].append(method);dump(out/'receipt.json',receipt)
  if method==JOURNEY:
   for name in ['journey.json']+[x+'.png' for x in ROUTES]:pull(out,name)
   for route in ROUTES:capture_hierarchy(out,route,cfg)
  elif method==PERF:pull(out,'performance.json')
 # Performance sampling is deliberately separate from screenshot capture.
 lock_matrix_rotation(cfg)
 launch_perf=launch_performance(json.loads((out/'performance.json').read_text()));dump(out/'launch-performance.json',launch_perf)
 if case=='api36-390':dump(out/'process-death.json',process_death(cfg))
 j=json.loads((out/'journey.json').read_text());p=json.loads((out/'performance.json').read_text())
 receipt.update(status='PASS_EXTENDED_NOT_DEVICE_APPROVAL',files={f.name:sha(f) for f in sorted(out.iterdir()) if f.name!='receipt.json'},performance_summary={'scenario_launch_ms':j['scenario_launch_ms'],'page_action_p95_ms':p95(p['page_action_ms']),'sampled_peak_pss_kb':max(p['sampled_pss_kb']),'cold_launch_p95_ms':launch_perf['cold_launch_p95_ms'],'frame_p95_ms':launch_perf['frame_p95_ms'],'budget_enforced':False});dump(out/'receipt.json',receipt)
 try:validate(out,case,ident,build)
 except Exception:receipt['status']='FAIL_EXTENDED_VERIFICATION';dump(out/'receipt.json',receipt);raise

def verify_all(root,expected,build=None,selected=None):
 cases=list(CASES) if selected is None else list(selected)
 require(cases and len(set(cases))==len(cases) and set(cases)<=set(CASES),'invalid selected case roster')
 require(set(p.name for p in root.iterdir())==set(cases),'missing or extra extended matrix shard')
 return {case:validate(root/case,case,expected,build)['performance_summary'] for case in cases}
def external_identity(source,tree,run_id,attempt):
 require(re.fullmatch('[0-9a-f]{40}',source or '') and re.fullmatch('[0-9a-f]{40}',tree or '') and re.fullmatch('[1-9][0-9]*',run_id or '') and re.fullmatch('[1-9][0-9]*',attempt or ''),'invalid external evidence identity')
 require(n.git('rev-parse','HEAD')==source and n.git('rev-parse','HEAD^{tree}')==tree,'external verifier source drift')
 return {'schema':'hooklab.uxr04.case.v1','source_sha':source,'source_tree':tree,'run_id':run_id,'run_attempt':attempt,'repository':'Mikeyphw/MikeysHookLab','hosted_emulator_only':True,'physical_device_qualified':False,'release_qualified':False,'visual_approved':False}
def main():
 p=argparse.ArgumentParser();p.add_argument('mode',choices=['run','verify','aggregate','publish-apks','verify-apks']);p.add_argument('--case',choices=CASES);p.add_argument('--out',type=Path,required=True);p.add_argument('--source');p.add_argument('--tree');p.add_argument('--run-id');p.add_argument('--attempt');p.add_argument('--summary',type=Path);p.add_argument('--apks',type=Path);p.add_argument('--selected-json');a=p.parse_args()
 try:
  selected=None
  if a.selected_json is not None:
   require(a.mode=='aggregate','selection only allowed for aggregate');rows=json.loads(a.selected_json)
   require(isinstance(rows,list) and rows and all(isinstance(row,dict) and set(row)=={'case','api'} and row['case'] in CASES and row['api']==CASES[row['case']][0] for row in rows),'selected case configuration drift');selected=[row['case'] for row in rows]
  external=any(x is not None for x in (a.source,a.tree,a.run_id,a.attempt,a.summary))
  if a.mode=='publish-apks':require(a.case is None and a.apks is None and not external,'build publication arguments refused');publish_apks(a.out,n.expected_identity())
  elif a.mode=='verify-apks':
   require(a.case is None and a.apks is None and a.summary is None and external,'explicit identity required');print(json.dumps(shared_apks(a.out,external_identity(a.source,a.tree,a.run_id,a.attempt)),sort_keys=True))
  elif a.mode=='run':require(a.case is not None and a.apks is not None and not external,'case and shared APKs required; external identity forbidden in capture');run(a.out,a.case,a.apks)
  else:
   expected=external_identity(a.source,a.tree,a.run_id,a.attempt) if external else n.expected_identity();expected['schema']='hooklab.uxr04.case.v1'
   build=shared_apks(a.apks,expected)
   if a.mode=='verify':require(a.case is not None and a.summary is None,'case required');validate(a.out,a.case,expected,build)
   else:
    result=verify_all(a.out,expected,build,selected)
    if a.summary is not None:require(a.summary.is_file() and not a.summary.is_symlink() and json.loads(a.summary.read_text())==result,'hosted aggregate disagrees with independent case verification')
    print(json.dumps(result,sort_keys=True))
  return 0
 except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError,zipfile.BadZipFile) as e:print('EXTENDED_UI_REFUSED',e,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
