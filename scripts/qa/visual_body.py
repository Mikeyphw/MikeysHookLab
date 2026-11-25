"""Source-bound fixed-label body evidence; no prototype state or golden inference."""
import math,re
from visual_routes import BODY,THEME
PALETTE={"dark":{"accent":"#b7eccb","bg":"#0d1214","error":"#f29d9a","ink":"#eef3ef","line":"#2a3436","muted":"#a5b2af","on":"#183229","surface":"#151b1e","surface2":"#20292c","warn":"#f4c987"},"light":{"accent":"#295e4c","bg":"#f4f6f3","error":"#ae403f","ink":"#182321","line":"#c5d0c9","muted":"#546660","on":"#ffffff","surface":"#e9eeeb","surface2":"#dce5df","warn":"#80551a"}}
def need(ok,msg):
    if not ok:raise ValueError(msg)
# The controls that actually perform an explicit action must have a real
# clickable instance. Noninteractive headers and unavailable-state rows do not.
ACTIONS={"home":{"scope","browse","diagnostics"},"connected-home":{"scope","browse","diagnostics"},"light-home":{"scope","browse","diagnostics"},"hooks":{"groups","by-app"},"by-app":{"groups","by-app"},"group-detail":{"evidence"},"hook-detail":{"health","evidence"},"settings":{"theme","dynamic","scope","verbose","privacy","about","help"},"scope":{"request","remove"},"mcc":{"inspect","confirm","root-inspect"},"about":{"repository","privacy","help"},"help":{"hooks","diagnostics","privacy","manual"},"privacy":{"diagnostics"}}
def validate(g,route):
    need(g['palette']==PALETTE[THEME[route]],'native fixed palette mismatch '+route)
    actual=g['body_controls'];expected=BODY.get(route,{})
    need(isinstance(actual,dict) and set(actual)==set(expected),'body control roster drift '+route)
    for k,row in actual.items():
        need(set(row)=={'label','instances'} and row['label']==expected[k]['label'],'fixed label drift '+k)
        instances=row['instances'];need(isinstance(instances,list) and 0<len(instances)<=100,'missing/oversized control instances '+k)
        for item in instances:
            need(set(item)=={'clickable','enabled','visible','geometry'} and all(type(item[x]) is bool for x in ('clickable','enabled','visible')),'body control shape drift')
            d=item['geometry']
            if not item['visible']:need(d is None,'offscreen geometry invented');continue
            need(isinstance(d,dict) and set(d)=={'label','bounds','font_sp','color'} and d['label']==row['label'],'body geometry label drift')
            b=d['bounds'];need(isinstance(b,list) and len(b)==4 and all(type(v) in (int,float) and math.isfinite(v) for v in b) and 0<=b[0]<390 and 0<=b[1]<844 and 0<b[2]<=390-b[0]+.05 and 0<b[3]<=844-b[1]+.05,'invalid body geometry')
            need(type(d['font_sp']) in (int,float) and 0<d['font_sp']<=100 and isinstance(d['color'],list) and len(d['color'])==3 and all(type(x) is int and 0<=x<=255 for x in d['color']),'invalid body typography')
        if k in ACTIONS.get(route,set()):need(any(x["clickable"] for x in instances),"missing native action "+route+" "+k)
    return actual

def compare(browser,native,route):
    actual=validate(native,route);expected=BODY.get(route,{})
    rows=[];matched=set()
    for key,spec in expected.items():
        act=spec['reference_action'];arg=spec['reference_argument'];ref=[]
        if act is not None:
            ref=[i for i in browser['items'] if i['action']==act and i['argument']==arg and i['tag']=='button' and not(i['action']=='root' and i['class_name'] in ('active',''))]
            need(len(ref)==1,'reference body action drift '+route+' '+key);matched.add(ref[0]['index'])
        # Prototype and native labels, state and geometry are not asserted equivalent.
        # Report the actual geometry of each visible instance, and absence when offscreen.
        x=ref[0] if ref else None
        rows.append({'id':key,'reference':{k:x[k] for k in ('label','bounds','visible','action','argument')} if x else None,'native':actual[key],'candidate_only':True})
    unresolved=[{k:x[k] for k in ('label','bounds','visible','action','argument')} for x in browser['items'] if x['tag']=='button' and x['action'] and x['index'] not in matched and x['action'] not in ('back',) and not(x['action']=='root' and x['class_name'] in ('active',''))]
    return {'controls':rows,'unmapped_reference_actions':unresolved,'fixture_equivalence':False}
