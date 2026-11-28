"""Real source-bound instrumentation on the existing hosted emulator."""
from __future__ import annotations
import argparse,subprocess
from pathlib import Path
from qualification import canonical,strict_json,require
import native_smoke as n
import uxm_contract as u
TEST='com.mikeyphw.hooklab.qa.UxmJourneyTest#';BASE='files/uxm-native/'
def instrument(method):
 ident=TEST+method;run=subprocess.run(['adb','-s',n.SERIAL,'shell','am','instrument','-w','-r','-e','class',ident,n.PKG+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=240);require(run.returncode==0,'UX-M instrumentation transport failed '+method);n.parse_instrumentation(run.stdout,ident);print('UXM_NATIVE_INSTRUMENTATION_PASS',method,flush=True)
def write(path,obj):path.write_bytes(canonical(obj)+b'\n')
def pull(name,path):
 path.write_bytes(n.raw('exec-out','run-as',n.PKG,'cat',BASE+name));require(0<path.stat().st_size<=16*1024*1024,'invalid native evidence size')
def stage_spec(spec):
 raw=canonical(spec);subprocess.run(['adb','-s',n.SERIAL,'shell','-T','run-as',n.PKG,'sh','-c',"'mkdir -p files/uxm-native && rm -f files/uxm-native/case-result.json files/uxm-native/case.png && cat > files/uxm-native/case.json'"],input=raw,check=True,timeout=30);require(n.raw('exec-out','run-as',n.PKG,'cat',BASE+'case.json')==raw,'native case staging changed')
def capture(out):
 c=u.validate();identity=n.expected_identity();n.guard();out.mkdir(parents=True,exist_ok=False);ah=n.digest(n.one('app/build/outputs/apk/debug/*.apk'));th=n.digest(n.one('app/build/outputs/apk/androidTest/debug/*.apk'));n.installed_digest(n.PKG,ah);n.installed_digest(n.PKG+'.test',th)
 # Existing production Activity witness; never a migrated route or golden.
 n.adb('shell','run-as',n.PKG,'rm','-f',BASE+'foundation.json');instrument('foundationWitness');pull('foundation.json',out/'foundation.json');data=strict_json((out/'foundation.json').read_bytes());require(data['source']==identity['source_sha'],'legacy build source mismatch');write(out/'foundation-receipt.json',{**identity,'apk_sha256':ah,'test_apk_sha256':th,'geometry_sha256':n.digest(out/'foundation.json'),'status':'PASS_REAL_LEGACY_ACTIVITY'})
 for key,spec in c['activation']['native_cases'].items():
  require(spec['fixture']=='disconnected-debug','fixture adapter not implemented; no candidate substitution');stage_spec({**spec,'id':key});instrument('admittedJourney');pull('case-result.json',out/(key+'.json'));pull('case.png',out/(key+'.png'));data=strict_json((out/(key+'.json')).read_bytes());data['environment']={**n.renderer_environment(),'api':n.txt('shell','getprop','ro.build.version.sdk'),'abi':n.txt('shell','getprop','ro.product.cpu.abi'),'fingerprint':n.txt('shell','getprop','ro.build.fingerprint'),'font_scale':float(n.txt('shell','settings','get','system','font_scale')),'theme':data['theme'],'density':data['density']};write(out/(key+'.json'),data);n.installed_digest(n.PKG,ah);n.installed_digest(n.PKG+'.test',th);write(out/(key+'-receipt.json'),{**identity,'case':key,'fixture':spec['fixture'],'apk_sha256':ah,'test_apk_sha256':th,'spec_sha256':u.digest(canonical(spec)),'geometry_sha256':n.digest(out/(key+'.json')),'png_sha256':n.digest(out/(key+'.png')),'journey_steps':len(spec['steps']),'status':'PASS_INSTRUMENTATION'})
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);capture(p.parse_args().out)
