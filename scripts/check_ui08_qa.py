#!/usr/bin/env python3
"""Host-only read-only UI08 regression and source-truth audit; no Gradle/device/root."""
from pathlib import Path
import re,sys,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
def read(path):return (r/path).read_text(encoding='utf-8')
def must(ok,name):
    if not ok:raise SystemExit('UI08_QA_FAIL '+name)
    print('UI08_QA_PASS '+name,flush=True)
main=read('app/src/main/java/com/mikeyphw/hooklab/ui/MainActivity.java')
flat=read('app/src/main/java/com/mikeyphw/hooklab/ui/FlatUi.java')
mcc=read('app/src/main/java/com/mikeyphw/hooklab/mcc/MccControlActivity.java')
model=read('app/src/main/java/com/mikeyphw/hooklab/ui/Ui08LegacySelection.java')
road=read('docs/roadmaps/hooklab-flat-ux.md')
gate=read('docs/qa/hooklab-flat-ux-acceptance.md')
manifest=ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
must(not manifest.findall('uses-permission'),'no new Android permissions')
must('private void appCard(' not in main and 'hooksCatalog();' in main,
     'retired unused legacy cards; catalog remains the only hooks view')
must('Ui08LegacySelection.resolveUnique(TweakCatalog.all(),oldPackage)' in main
     and 'if (unique != null) return null;' in model,
     'legacy package migration rejects ambiguous multiple hooks')
must(main.count('FlatUi.sectionHeading(titleView);')==2
     and 'title.setAccessibilityHeading(true);' in flat
     and 'titleView.setText' not in main,
     'native card headings and semantics')
must('Ui08QualityPolicy.MIN_TOUCH_DP' in flat
     and 'setEnsureMinTouchTargetSize(true)' in flat
     and 'toolbar.setNavigationContentDescription(R.string.ui_back)' in main,
     'touch targets and explicit back labels')
must('stateStore.writeTweak(' in main and 'stateStore.removeScope(' in main
     and 'stateStore.writeSafetyLock(' in main and 'setPositiveButton("Set to 9"' in mcc,
     'existing controlled framework/MCC mutation paths retained')
must('diagnosticsStore().filtered(' in main and 'getString(R.string.dx01_import_only)' in main
     and 'executionVerified(){return false;}' in read('app/src/main/java/com/mikeyphw/hooklab/ui/HookHealthModel.java'),
     'diagnostic imports remain unauthenticated and bounded')
for qualifier in ('', '-night'):
    p='app/src/main/res/values'+qualifier+'/colors.xml'
    root=ET.parse(r/p).getroot()
    colors={node.get('name'):int((node.text or '').strip().lstrip('#'),16)
            for node in root.findall('color')}
    def c(f,b):
        def lin(n):
            x=n/255.0
            return x/12.92 if x<=0.04045 else ((x+0.055)/1.055)**2.4
        def lum(value):return .2126*lin((value>>16)&255)+.7152*lin((value>>8)&255)+.0722*lin(value&255)
        a,b=lum(colors[f]),lum(colors[b])
        return (max(a,b)+.05)/(min(a,b)+.05)
    must(c('hooklab_on_surface','hooklab_canvas')>=4.5 and
         c('hooklab_secondary','hooklab_canvas')>=4.5,
         'real bundled '+(qualifier or 'day')+' palette contrast >=4.5')
required=re.findall(r'(?<![A-Z0-9])(?:DS01|UI04|UI05|UI06|DX01|DX02|DX03|UI07|UI08)-P\d\d',road)
required=set(required)
covered=set(re.findall(r'(?:DS01|UI04|UI05|UI06|DX01|DX02|DX03|UI07|UI08)-P\d\d',gate))
must(len(required)==38 and covered==required,'38 roadmap promises retained in QA matrix')
must('DEVICE_NOT_RUN' in gate and 'RELEASE_NOT_AUTHORIZED' in gate
     and 'LSPOSED_RUNTIME_NOT_VERIFIED' in gate,
     'release and device gates explicitly unqualified')
print('UI08_QA_COMPLETE=PASS local_gradle=NOT_RUN device=NOT_RUN release=NOT_AUTHORIZED')
