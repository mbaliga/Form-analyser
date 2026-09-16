#!/usr/bin/env python3
"""Build Crocodyl's generic target-arrow taxonomy as portable GLB assets.

These are educational system studies, not brand replicas, dimensional specifications,
or equipment-fitting advice.  The source ledger below records what each generic model
is intended to demonstrate.
"""
from __future__ import annotations
import hashlib, json, math, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import build_assets as ba

OUT = ROOT / "app-android/src/main/assets/atelier/arrow-library"
TAU = math.tau

# Existing atelier materials are reused where possible. Add neutral generic materials only.
ALUMINIUM = len(ba.MATS); ba.MATS.append(ba.mat("Hard anodised aluminium", (.48,.52,.54), .88,.28))
TUNGSTEN  = len(ba.MATS); ba.MATS.append(ba.mat("Tungsten alloy study", (.25,.26,.27), .94,.18))
ZINC      = len(ba.MATS); ba.MATS.append(ba.mat("Zinc alloy point", (.46,.50,.52), .82,.34))
POLYMER   = len(ba.MATS); ba.MATS.append(ba.mat("Smoked polymer nock", (.18,.40,.30), .05,.34))
CARBON, STEEL = 1, 2
PAPER, BLACK, BLUE, RED, GOLD = 7, 8, 9, 10, 11
NEUTRAL = 13

SOURCES = {
 "easton_shaft_design": {"title":"Arrow Shaft Design and Performance","publisher":"Easton Archery","url":"https://eastonarchery.com/2018/12/arrow-shaft-design-and-performance/","supports":["parallel, tapered and barrelled are major target-shaft design families"]},
 "easton_2026": {"title":"Easton Target 2026 product guide","publisher":"Easton Archery","url":"https://eastonarchery.com/wp-content/uploads/2026/03/Easton-2026.pdf","supports":["current barrelled and single-taper aluminium-carbon shafts, aluminium target shafts, bullet points and nock component families"]},
 "easton_ac": {"title":"A/C Arrow Construction - How A/C Arrows are Made","publisher":"Easton Archery","url":"https://eastonarchery.com/2022/05/a-c-arrow-construction-how-a-c-arrows-are-made/","supports":["aluminium core combined with bonded external carbon fibre"]},
 "easton_superdrive": {"title":"Superdrive Micro","publisher":"Easton Archery","url":"https://eastonarchery.com/arrows_/superdrive-micro/","supports":["multi-layer all-carbon parallel shaft; pin and direct-fit nock options"]},
 "easton_nocks": {"title":"Nocks - The Vital Connection","publisher":"Easton Archery","url":"https://eastonarchery.com/2014/01/nocks-the-vital-connection/","supports":["direct-fit/insert, overnock/out-nock and pin-nock systems"]},
 "easton_components": {"title":"Components Guide","publisher":"Easton Archery","url":"https://eastonarchery.com/components-guide/","supports":["field-point and target-point component families"]},
 "easton_hl": {"title":"4MM HL Stainless Steel Break-off Point","publisher":"Easton Archery","url":"https://eastonarchery.com/shop/components/points/4mm-hl-stainless-steel-break-off-point/","supports":["stainless glue-in break-off target point"]},
 "easton_x10": {"title":"X10 components","publisher":"Easton Archery","url":"https://eastonarchery.com/product-tag/x10/","supports":["tungsten and stainless target points; pin and overnock components"]},
 "world_archery": {"title":"World Archery Rulebook Book 2 (2026)","publisher":"World Archery","url":"https://extranet.worldarchery.sport/documents/index.php/Rules/Rule_Book_versions/2026-01-27/EN-Book_2_-_2026-01-27_Version.pdf","supports":["122 cm face: ten scoring zones, 6.1 cm radial width; inner-X 6.1 cm diameter"]},
 "owner_square_sheet": {"title":"Owner correction, 2026-09-17","publisher":"Crocodyl product direction","url":"docs/crocodyl/visuals/ARROW_FIRST_ART_DIRECTION.md","supports":["target sheet is square while scoring area remains concentric circles"]},
}

def part(m, pid, title, desc, subtype, refs, hotspot=(0,0,0), note=None):
    m.part(pid,title,desc,hotspot)
    m.parts[pid].update(category=m.name, subtype=subtype, sourceRefs=refs,
                        dimensionsStatus="illustrative taxonomy geometry")
    if note: m.parts[pid]["notes"] = note

def lathe(m,pid,mat,profile,y=0,z=0,sides=40):
    m.lathe(pid,mat,(0,y,z),(1,0,0),profile,sides)

def box(m,pid,mat,cx,cy,cz,sx,sy,sz):
    x0,x1=cx-sx/2,cx+sx/2; y0,y1=cy-sy/2,cy+sy/2; z0,z1=cz-sz/2,cz+sz/2
    v=[(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
    f=[(0,2,1),(0,3,2),(4,5,6),(4,6,7),(0,1,5),(0,5,4),(1,2,6),(1,6,5),(2,3,7),(2,7,6),(3,0,4),(3,4,7)]
    m.mesh(pid,mat,v,f,[(0,0)]*8)

def annulus(m,pid,mat,r0,r1,z=.0012,sides=128):
    v=[]; uv=[]; f=[]
    for r in (r0,r1):
        for i in range(sides+1):
            a=TAU*i/sides; v.append((r*math.cos(a),r*math.sin(a),z)); uv.append((.5+r*math.cos(a),.5+r*math.sin(a)))
    for i in range(sides): f += [(i,sides+1+i,sides+2+i),(i,sides+2+i,i+1)]
    m.mesh(pid,mat,v,f,uv)

def shafts():
    m=ba.Model("Arrow shaft profiles")
    specs=[
      ("parallel","Parallel shaft","Constant outside diameter over the working length.",[-.36,.36],[.003,.003],-.055,["easton_shaft_design","easton_2026"]),
      ("barrelled","Barrelled shaft","Largest outside diameter near the middle, reducing toward both ends.",[-.36,-.22,0,.22,.36],[.00255,.00278,.00315,.00278,.00255],0,["easton_shaft_design","easton_2026"]),
      ("single_taper","Single-taper shaft","Outside diameter progressively reduces toward one end.",[-.36,-.12,.12,.36],[.00315,.00308,.00286,.00245],.055,["easton_shaft_design","easton_2026"]),
    ]
    for pid,title,desc,xs,rs,y,refs in specs:
        part(m,pid,title,desc,pid,refs,(0,y,0),"Diameter differences are mildly exaggerated for phone-scale legibility.")
        lathe(m,pid,NEUTRAL,[(0,xs[0])]+[(r,x) for x,r in zip(xs,rs)]+[(0,xs[-1])],y,0,48)
    return m

def constructions():
    m=ba.Model("Arrow shaft constructions")
    for pid,title,desc,y,refs in [
      ("aluminium","Aluminium alloy tube","Drawn aluminium-alloy target-shaft construction.",-.06,["easton_2026"]),
      ("carbon","All-carbon composite","Multi-layer all-carbon shaft construction; exact layup is product-specific.",0,["easton_superdrive"]),
      ("aluminium_carbon","Aluminium-core / carbon-shell","Aluminium core tube with externally bonded carbon fibre.",.06,["easton_ac","easton_2026"]),
    ]: part(m,pid,title,desc,pid,refs,(0,y,0),"Layer thicknesses and diameters are schematic, not proprietary specifications.")
    lathe(m,"aluminium",ALUMINIUM,[(0,-.34),(.00335,-.34),(.00335,.34),(0,.34)],-.06)
    lathe(m,"carbon",CARBON,[(0,-.34),(.00315,-.34),(.00315,.34),(0,.34)],0)
    # Deliberately expose the alloy core at both ends to make the hybrid construction visible.
    lathe(m,"aluminium_carbon",ALUMINIUM,[(0,-.35),(.00265,-.35),(.00265,.35),(0,.35)],.06)
    lathe(m,"aluminium_carbon",CARBON,[(0,-.31),(.00325,-.31),(.00325,.31),(0,.31)],.06)
    return m

def points():
    m=ba.Model("Target point families and materials")
    rows=[
      ("bullet","One-piece bullet point","Rounded one-piece target point profile. Material intentionally neutral: bullet describes shape/family, not one universal alloy.",NEUTRAL,-.10,"one-piece bullet",["easton_2026","easton_components"],[(0,-.050),(.0031,-.050),(.0040,-.039),(.0042,-.020),(.0033,.004),(.0014,.027),(0,.036)]),
      ("zinc_glue_in","Zinc glue-in target point","Generic zinc glue-in target-point study.",ZINC,-.05,"zinc glue-in",["easton_2026"],[(0,-.050),(.0029,-.050),(.0036,-.035),(.0037,-.012),(.0022,.018),(0,.034)]),
      ("stainless_breakoff","Stainless break-off point","Glue-in stainless target point with scored removable weight sections.",STEEL,0,"stainless break-off",["easton_hl","easton_2026"],[(0,-.055),(.0028,-.055),(.0034,-.040),(.0036,-.010),(.0024,.017),(0,.035)]),
      ("tungsten_breakoff","Tungsten-alloy point","Compact heavy target-point family represented with a tungsten-alloy material study.",TUNGSTEN,.05,"tungsten target point",["easton_x10","easton_2026"],[(0,-.052),(.0027,-.052),(.0033,-.038),(.0033,-.006),(.0021,.017),(0,.031)]),
      ("screw_in_field","Screw-in field/target point","Threaded point used with an insert; attachment differs from glue-in target points.",STEEL,.10,"screw-in field/target",["easton_components"],[(0,-.050),(.0035,-.050),(.0040,-.034),(.0040,-.010),(.0025,.019),(0,.038)]),
    ]
    for pid,title,desc,mat,y,sub,refs,profile in rows:
        part(m,pid,title,desc,sub,refs,(0,y,0),"Generic silhouette; not a branded dimensional copy.")
        lathe(m,pid,mat,profile,y,0,48)
        if "breakoff" in pid:
            for x in (-.041,-.033): m.ring(pid,7,(x,y,0),(1,0,0),.00286,.00016,24,5)
        if pid=="screw_in_field":
            for x in (-.050,-.044,-.038): m.ring(pid,7,(x,y,0),(1,0,0),.00165,.00014,20,4)
    return m

def ears(m,pid,mat,x,y,z=0):
    # Two visible polymer ears with an open string groove; intentionally generic.
    for s in (-1,1):
        zz=z+s*.0024
        lathe(m,pid,mat,[(0,x-.019),(.0013,x-.019),(.0017,x-.010),(.0017,x-.002),(0,x+.001)],y,zz,24)

def nocks():
    m=ba.Model("Target nock attachment systems")
    # Common shaft stub for each system.
    rows=[("direct_fit","Direct-fit / insert nock","Nock stem presses directly into the shaft bore.",-.105,"direct-fit insert",["easton_2026","easton_nocks"]),
          ("pin_nock","Pin nock system","Metal pin adapter fits the shaft; polymer nock presses onto the external pin.",-.035,"pin nock",["easton_nocks","easton_2026"]),
          ("overnock","Overnock / out-nock","Nock body fits around the outside of the rear shaft.",.035,"overnock/out-nock",["easton_nocks","easton_x10"]),
          ("bushing_insert","Bushing + insert nock","Metal rear bushing presents a socket for a separate insert nock.",.105,"bushing-mounted insert",["easton_2026"]) ]
    for pid,title,desc,y,sub,refs in rows:
        part(m,pid,title,desc,sub,refs,(-.03,y,0),"Generic interface study; groove fit and compatibility are setup-specific.")
        lathe(m,pid,CARBON,[(0,-.012),(.0032,-.012),(.0032,.105),(0,.105)],y)
    # direct fit stem + nock body
    lathe(m,"direct_fit",POLYMER,[(0,-.038),(.0018,-.038),(.0018,.006),(.0028,.006),(0,.015)],-.105); ears(m,"direct_fit",POLYMER,-.038,-.105)
    # pin adapter, external pin, nock ears
    lathe(m,"pin_nock",ALUMINIUM,[(0,-.040),(.0014,-.040),(.0014,-.015),(.0026,-.010),(.0026,.008),(0,.008)],-.035)
    lathe(m,"pin_nock",ALUMINIUM,[(0,-.060),(.0009,-.060),(.0009,-.038),(0,-.038)],-.035); ears(m,"pin_nock",POLYMER,-.063,-.035)
    # overnock sleeve visibly wider than shaft
    lathe(m,"overnock",POLYMER,[(0,-.044),(.0037,-.044),(.0037,-.006),(.0034,.003),(0,.003)],.035); ears(m,"overnock",POLYMER,-.044,.035)
    # metal bushing plus insert nock
    lathe(m,"bushing_insert",ALUMINIUM,[(0,-.027),(.0022,-.027),(.0022,-.013),(.0030,-.010),(.0030,.012),(0,.012)],.105)
    lathe(m,"bushing_insert",POLYMER,[(0,-.044),(.0019,-.044),(.0019,-.010),(0,-.010)],.105); ears(m,"bushing_insert",POLYMER,-.044,.105)
    return m

def target():
    m=ba.Model("Square 122 cm target-face study")
    part(m,"face","Square 122 cm target face","Square paper sheet carrying ten concentric scoring rings.","122 cm ten-ring face",["world_archery","owner_square_sheet"],(0,0,0),"Face study only; paper stock, print tolerances and manufacturer margins are not claimed.")
    box(m,"face",PAPER,0,0,-.001,1.22,1.22,.002)
    mats=[GOLD,GOLD,RED,RED,BLUE,BLUE,BLACK,BLACK,PAPER,PAPER]
    for i,mat in enumerate(mats): annulus(m,"face",mat,i*.061,(i+1)*.061)
    m.ring("face",BLACK,(0,0,.002),(0,0,1),.0305,.0006,96,4)
    part(m,"retainers","Paper face retainers","Four generic retainers shown near the corners.","retainers",["owner_square_sheet"],(.56,.56,.008),"Placement is illustrative, not a competition specification.")
    for x in (-.56,.56):
        for y in (-.56,.56): box(m,"retainers",ALUMINIUM,x,y,.006,.018,.018,.006)
    return m

def export(key,model):
    path=OUT/f"{key}.glb"; info=ba.export_glb(model,path)
    info["parts"]=[{**p} for p in model.parts.values()]
    return info

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    built={}
    for key,fn in [("shaft_profiles",shafts),("shaft_constructions",constructions),("points",points),("nock_systems",nocks),("target_face_square",target)]:
        built[key]=export(key,fn()); print(key,built[key]["bytes"],built[key]["triangles"])
    manifest={
      "version":1,"scope":"generic target-arrow taxonomy","notBrandReplicas":True,"notFittingAdvice":True,
      "dimensionPolicy":"Illustrative educational geometry only. No spine, arrow length, point mass, nock groove or component compatibility is prescribed.",
      "taxonomy":{"shaftProfiles":["parallel","barrelled","single-taper"],"shaftConstructions":["aluminium","all-carbon","aluminium-core/carbon-shell"],"pointFamilies":["bullet","zinc glue-in","stainless break-off","tungsten-alloy","screw-in field/target"],"nockSystems":["direct-fit/insert","pin nock","overnock/out-nock","bushing-mounted insert"],"target":"square substrate with concentric scoring rings"},
      "sources":SOURCES,"models":built,
    }
    (OUT/"arrow_library_manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")

if __name__=="__main__": main()
