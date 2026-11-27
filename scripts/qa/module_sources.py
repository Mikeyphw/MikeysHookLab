"""Deterministic, reviewed source projection of existing implementations into independent DEX namespaces.
No Gradle/D8 invocation here. Original built-ins remain a compatibility path until explicit selection.
"""
from pathlib import Path
import argparse,json,re,sys
ROOT=Path(__file__).resolve().parents[2]
BASE='com.mikeyphw.hooklab'
ROSTER={
 'obtainx':{'sources':('DroidifyHook','NeoStoreAction','NeoStoreHook','ObtainXBridge','ObtainXIds','SourcePolicy'),
            'install':{'neostore.obtainx.manual':'NeoStoreHook','droidify.obtainx.manual':'DroidifyHook'}},
 'wordmaster':{'sources':('WordMasterNightHook',),'install':{'wordmaster.night.filter':'WordMasterNightHook'}},
 'chatgpt':{'sources':('AsyncCopyTrace','ChatGptCodeProbe','ChatGptTermuxHandoff','CodeBlockActionMatcher','CodeBlockProbeMatcher','CopyButtonCausalityTracker','TermuxOfferGate'),
            'install':{'chatgpt.codeblock.copy.probe':'ChatGptCodeProbe','chatgpt.codeblock.termux.handoff':'ChatGptTermuxHandoff'}}}
REPLACEMENTS={BASE+'.core.HookContext':BASE+'.sdk.PackHookContext',BASE+'.core.TweakDiagnostics':BASE+'.sdk.PackDiagnostics',
              'io.github.libxposed.api.XposedInterface':BASE+'.sdk.PackHookApi',BASE+'.termux.TermuxCommandPolicy':BASE+'.sdk.PackPreviewContract'}

def project(module,root=ROOT):
    if module not in ROSTER:raise ValueError('unknown migration module')
    src=root/'app/src/main/java'/Path(*BASE.split('.'))/module
    actual={p.stem for p in src.glob('*.java')}
    if actual!=set(ROSTER[module]['sources']):raise ValueError('review migration source inventory drift: '+module)
    namespace=BASE+'.modules.'+module; output={}
    for name in ROSTER[module]['sources']:
        p=src/(name+'.java')
        if not p.is_file() or p.is_symlink():raise ValueError('unsafe migration source')
        s=p.read_text()
        if s.count('package '+BASE+'.'+module+';')!=1:raise ValueError('source namespace drift')
        s=s.replace('package '+BASE+'.'+module+';','package '+namespace+';')
        for old,new in REPLACEMENTS.items():
            if 'import '+old+';' in s:
                s=s.replace('import '+old+';','import '+new+';')
                s=re.sub(r'\b'+re.escape(old.rsplit('.',1)[-1])+r'\b',new.rsplit('.',1)[-1],s)
        s=s.replace('import '+BASE+'.core.VerboseLogs;\n','')
        # Code loaded from a pack may call only the SDK's parent-defined port.
        for imp in re.findall(r'^import ([^;]+);',s,re.M):
            if not imp.startswith(('java.','javax.crypto.','android.',BASE+'.sdk.')):raise ValueError('unreviewed pack dependency: '+imp)
        if 'io.github.libxposed' in s or BASE+'.core.' in s:raise ValueError('raw framework/core dependency')
        # Parent-owned eligibility applies also to UI callbacks posted before revocation.
        guards={
          'NeoStoreHook':(('void poll(int token) {','if(!VerboseLogs.active()){stop();return;}'),('void tap(int token,Object shownRoute,SourcePolicy.Snapshot shown) {','if(!VerboseLogs.active())return;')),
          'ObtainXBridge':(('public static Outcome launch(Activity activity, SourcePolicy.Snapshot snapshot, String route) {','if(!VerboseLogs.active())return Outcome.LAUNCH_REFUSED;'),),
          'ChatGptTermuxHandoff':(('private static void openPreview(TermuxOfferGate b){','if(!VerboseLogs.active())return;'),('Object scope=a[0],c=a[1];','if(!VerboseLogs.active())return h.call(h.invoke3,delegate,scope,c,a[2]);')),
          'WordMasterNightHook':(('try { ensureShade(activity,filter); }',''),),
        }
        for marker,guard in guards.get(name,()):
            if s.count(marker)!=1:raise ValueError('reviewed callback boundary drift: '+name)
            if name=='WordMasterNightHook':s=s.replace(marker,'try { if(VerboseLogs.active())ensureShade(activity,filter); }')
            else:s=s.replace(marker,marker+'\n            '+guard)
        output[name+'.java']=s
    installs='\n'.join('        if(context.settings.enabled("'+hook+'")){selected++; '+klass+'.install(host);}' for hook,klass in sorted(ROSTER[module]['install'].items()))
    count=' + '.join('(context.settings.enabled("'+hook+'")?1:0)' for hook in sorted(ROSTER[module]['install']))
    output['Entrypoint.java']=f'''package {namespace};
import {BASE}.sdk.HookSdk;
import {BASE}.sdk.PackHookContext;
import {BASE}.sdk.PackHookApi;
/** Independent reviewed migration of the same built-in implementation sources. */
public final class Entrypoint implements HookSdk.Entrypoint {{
    @Override public void install(HookSdk.Context context)throws Exception {{
        if(({count})!=1)throw new IllegalArgumentException("exactly one eligible hook required");
        PackHookContext host=context.packHost();
        VerboseLogs.bind(host.framework(PackHookApi.class));
        context.diagnostics.record(HookSdk.Event.INSTALL_STARTED);
        int selected=0;
{installs}
        if(selected!=1)throw new IllegalArgumentException("hook roster mismatch");
        context.diagnostics.record(HookSdk.Event.INSTALL_COMPLETED);
    }}
}}
'''
    events=sorted(set(re.findall(r'VerboseLogs\.Event\.([A-Z_]+)','\n'.join(output.values()))))
    # Pack-local static bridge points only to the parent-provided logger. No raw preference access.
    output['VerboseLogs.java']=f'''package {namespace};
import {BASE}.sdk.PackHookApi;
final class VerboseLogs {{
    enum Event {{ {','.join(events) if events else 'UNUSED'} }}
    private static volatile PackHookApi api;
    static void bind(PackHookApi value){{api=value;}}
    static void emit(Event event){{PackHookApi p=api;if(p!=null)p.verbose(event.name());}}
    static boolean active(){{PackHookApi p=api;return p!=null&&p.active();}}
}}
'''
    return output

def settings_schema(module):return 2 if module=='wordmaster' else 1

def manifest(module,version=None,root=ROOT):
    if version is None:version='1.1.0' if module=='wordmaster' else '1.0.0'
    registry=json.loads((root/'runtime/src/main/resources/META-INF/hooklab/builtin-registry.json').read_text())
    rows=[{k:r[k] for k in ('id','package','min_version','max_version')} for r in registry['hooks'] if r['module']==module]
    if {r['id'] for r in rows}!=set(ROSTER[module]['install']):raise ValueError('migration manifest roster drift')
    return {'schema':'hooklab.module.manifest.v1','module_id':module,'version':version,'sdk_min':1,'sdk_max':1,'api':102,
            'entrypoint':BASE+'.modules.'+module+'.Entrypoint','hooks':sorted(rows,key=lambda r:r['id'])}

def main():
    p=argparse.ArgumentParser();p.add_argument('module',choices=ROSTER);p.add_argument('--out',type=Path,required=True);p.add_argument('--version');a=p.parse_args()
    try:
        if a.out.exists():raise ValueError('fresh output directory required')
        a.out.mkdir(parents=True);dest=a.out/'src';dest.mkdir()
        for name,data in project(a.module).items():(dest/name).write_text(data)
        (a.out/'manifest.json').write_text(json.dumps(manifest(a.module,a.version),sort_keys=True,separators=(',',':')))
        return 0
    except (ValueError,OSError) as e:print('MODULE_SOURCE_REFUSED',str(e),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
