#!/usr/bin/env python3
"""Build generic target-arrow taxonomy GLBs grounded in Lancaster Archery Supply references.

No Lancaster product image is embedded or redistributed. Geometry is generic unless a dimension is
explicitly marked as published in the generated manifest. Uses the existing dependency-free atelier
GLB exporter so the output remains portable and deterministic.
"""
from __future__ import annotations
import json
from pathlib import Path
import build_assets as base

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'app-android/src/main/assets/atelier/arrow-reference-library'

# Additional neutral materials used only by this taxonomy library.
ALUMINUM = len(base.MATS); base.MATS.append(base.mat('Satin aluminium', (.64,.67,.69), .92, .26))
TUNGSTEN = len(base.MATS); base.MATS.append(base.mat('Tungsten alloy', (.43,.45,.46), .98, .19))
BLACK_NICKEL = len(base.MATS); base.MATS.append(base.mat('Black nickel stainless', (.16,.17,.18), .90, .24))
POLYMER = len(base.MATS); base.MATS.append(base.mat('Translucent nock polymer', (.58,.82,.72), .05, .24))
DARK_CARBON = 1
STEEL = 2

SOURCES = {
 'parallel_x10_32': 'https://lancasterarchery.com/products/easton-x10%E2%84%A2-3-2mm-parallel-pro-shafts',
 'parallel_x10_4': 'https://lancasterarchery.com/products/easton-x10-parallel-pro-4mm-shafts',
 'barrel_x10': 'https://lancasterarchery.com/products/easton-x10-arrow-shafts',
 'front_taper_protour': 'https://lancasterarchery.com/products/easton-x10-protour-arrow-shafts',
 'rear_taper_vxt': 'https://lancasterarchery.com/products/victory-vxt-v1-arrow-shafts',
 'pure_carbon': 'https://lancasterarchery.com/products/carbon-express-nano-pro-x-treme-shafts',
 'aluminium_x7': 'https://lancasterarchery.com/products/easton-x7-eclipse-black-arrow-shaft',
 'fmj': 'https://lancasterarchery.com/products/easton-4mm-fmj-arrow-shafts',
 'breakoff_ss': 'https://lancasterarchery.com/products/easton-4mm-hl-stainless-steel-break-off-point',
 'breakoff_tungsten': 'https://lancasterarchery.com/products/easton-x10-ballistic-tungsten-break-off-points-100-120-gr',
 'aluminium_bullet': 'https://lancasterarchery.com/products/easton-aluminum-glue-in-bullet-point',
 'nibb': 'https://lancasterarchery.com/products/easton-nibb-points',
 'screw_bullet': 'https://lancasterarchery.com/products/nap-bullet-screw-in-practice-points',
 'field_point': 'https://lancasterarchery.com/blogs/guides-and-information/basic-guide-to-target-archery-arrow-points',
 'chisel': 'https://lancasterarchery.com/products/pdp-glue-in-target-point',
 'nock_guide': 'https://lancasterarchery.com/blogs/guides-and-information/nocks-everything-you-need-to-know',
 'beiter_pin': 'https://lancasterarchery.com/products/beiter-pin-nock',
 'beiter_pin_out': 'https://lancasterarchery.com/products/beiter-pin-out-nock',
 'beiter_insert': 'https://lancasterarchery.com/products/beiter-insert-nock-204',
 'impact_collar': 'https://lancasterarchery.com/products/easton-x10-nock-impact-collar',
 'tribute': 'https://lancasterarchery.com/products/easton-xx75-tribute-arrow-shaft',
}


def add_part(m, pid, title, description, y):
    m.part(pid, title, description, (0, y, 0), (0, .035, 0))


def shaft_profiles():
    m=base.Model('Crocodyl / shaft profile taxonomy')
    specs=[
      ('parallel','Parallel cylindrical','Constant external diameter. Representative dimensions use the published X10 Parallel Pro 600 size: 0.194 in OD, 32 in length.',-.18,
       [(.0024638,-.4064),(.0024638,.4064)]),
      ('barrelled','Barrelled','Maximum diameter near the middle with reduced ends. Family is source-backed; exact X10 end diameters/taper stations are not published by Lancaster, so this is generic topology.',-.06,
       [(.00228,-.40),(.00240,-.28),(.00256,-.08),(.00258,0),(.00256,.08),(.00240,.28),(.00228,.40)]),
      ('front_taper','Front taper','Parallel rear/mid body tapering toward the point end. ProTour confirms the family; taper station and end diameter here are generic.',.06,
       [(.00255,-.40),(.00255,.18),(.00248,.27),(.00236,.34),(.00220,.40)]),
      ('rear_taper','Front-parallel / rear taper','Parallel front body with a reduced rear section, based on the VXT front-parallel/rear-taper description. Exact dimensions are generic.',.18,
       [(.00218,-.40),(.00232,-.32),(.00248,-.22),(.00255,-.10),(.00255,.40)]),
    ]
    for pid,title,desc,y,profile in specs:
        add_part(m,pid,title,desc,y);m.lathe(pid,DARK_CARBON,(0,y,0),(1,0,0),profile,32)
    return m


def shaft_constructions():
    m=base.Model('Crocodyl / shaft construction taxonomy')
    # Schematic cutaways: exposed sections show layer order, not wall-thickness specifications.
    rows=[(-.15,'aluminium','Aluminium alloy','Single aluminium-alloy tube, represented by an X7/XX75-style target-shaft construction.'),
          (-.05,'all_carbon','All-carbon','Carbon-fibre shaft family such as Nano Pro/VXT; no aluminium core.'),
          (.05,'carbon_over_aluminium','Carbon over aluminium','Carbon exterior bonded to a precision aluminium core, as in X10/A/C/E families. Layer thickness schematic only.'),
          (.15,'aluminium_over_carbon','Aluminium over carbon','Carbon core with aluminium jacket (FMJ-style inverse construction). Included as construction taxonomy, not a target-arrow recommendation.')]
    for y,pid,title,desc in rows:
        add_part(m,pid,title,desc,y)
        if pid=='aluminium':
            m.lathe(pid,ALUMINUM,(0,y,0),(1,0,0),[(0,-.18),(.0032,-.18),(.0032,.18),(0,.18)],28)
        elif pid=='all_carbon':
            m.lathe(pid,DARK_CARBON,(0,y,0),(1,0,0),[(0,-.18),(.0030,-.18),(.0030,.18),(0,.18)],28)
        elif pid=='carbon_over_aluminium':
            m.lathe(pid,DARK_CARBON,(0,y,0),(1,0,0),[(0,-.18),(.0030,-.18),(.0030,.10),(0,.10)],28)
            m.lathe(pid,ALUMINUM,(0,y,0),(1,0,0),[(0,.08),(.00235,.08),(.00235,.18),(0,.18)],28)
        else:
            m.lathe(pid,ALUMINUM,(0,y,0),(1,0,0),[(0,-.18),(.00315,-.18),(.00315,.10),(0,.10)],28)
            m.lathe(pid,DARK_CARBON,(0,y,0),(1,0,0),[(0,.08),(.00245,.08),(.00245,.18),(0,.18)],28)
    return m


def _stepped_tail(start=-.050, end=-.006, r=.00125, steps=5):
    profile=[(0,start),(r,start)]
    span=(end-start)/steps
    for i in range(steps):
        a=start+i*span;b=a+span*.78;c=start+(i+1)*span
        profile.extend([(r,a),(r,b),(r*.82,b),(r*.82,c),(r,c)])
    return profile


def points():
    m=base.Model('Crocodyl / target point taxonomy')
    ys=[-.18,-.12,-.06,0,.06,.12,.18]
    entries=[
      ('breakoff_stainless','Stainless break-off target point','Glue-in stainless target point with break-off weight-adjustment steps. Lancaster documents Easton 4 mm HL as 130 to 80 gr in 10 gr increments and black-nickel stainless.',ys[0]),
      ('breakoff_tungsten','Tungsten break-off target point','Dense tungsten-alloy break-off family. Lancaster lists X10 Ballistic Tungsten at 100-120 gr; geometry here is generic, not an Easton replica.',ys[1]),
      ('gluein_aluminium_bullet','Aluminium glue-in bullet','Rounded bullet-profile glue-in point for aluminium shafts; Lancaster lists 50-100 gr variants depending on shaft size.',ys[2]),
      ('nibb','NIBB / long-shank target point','Hardened steel point with an extra-long aluminium shank, matching Lancaster construction description.',ys[3]),
      ('screw_bullet_8_32','8-32 screw-in bullet','Machined steel bullet point with standard 8-32 threaded attachment. Head uses a representative 9/32 in class.',ys[4]),
      ('screw_field_8_32','8-32 screw-in field point','Narrow field-profile screw-in point, distinct from bullet shape; standard insert-thread concept.',ys[5]),
      ('gluein_chisel','Glue-in chisel target point','Steel glue-in chisel-style target point family. Generic educational silhouette.',ys[6]),
    ]
    for pid,title,desc,y in entries:add_part(m,pid,title,desc,y)
    # Stainless break-off, five removable 10-gr steps represented schematically.
    p=_stepped_tail(steps=5)+[(.00125,-.006),(.00255,-.004),(.0030,.002),(.0028,.010),(.0020,.020),(.0008,.028),(0,.031)]
    m.lathe('breakoff_stainless',BLACK_NICKEL,(0,ys[0],0),(1,0,0),p,32)
    # Tungsten family, fewer break-off stations matching the narrower 100-120 gr range conceptually.
    p=_stepped_tail(start=-.043,end=-.012,r=.00123,steps=2)+[(.00123,-.012),(.00245,-.009),(.00285,-.002),(.00255,.009),(.0015,.020),(0,.027)]
    m.lathe('breakoff_tungsten',TUNGSTEN,(0,ys[1],0),(1,0,0),p,32)
    # Aluminium glue-in bullet.
    m.lathe('gluein_aluminium_bullet',ALUMINUM,(0,ys[2],0),(1,0,0),[(0,-.032),(.0017,-.032),(.0017,-.004),(.0036,-.002),(.00365,.005),(.0032,.013),(.0020,.023),(0,.029)],32)
    # NIBB: long aluminium shank plus hardened-steel nose.
    m.lathe('nibb',ALUMINUM,(0,ys[3],0),(1,0,0),[(0,-.055),(.0015,-.055),(.0015,.001),(0,.001)],24)
    m.lathe('nibb',STEEL,(0,ys[3],0),(1,0,0),[(0,-.002),(.00155,-.002),(.0029,.001),(.0028,.008),(.0017,.019),(0,.026)],28)
    # 9/32 representative bullet head = 7.14 mm nominal diameter; simplified 8-32 threaded stem.
    stem_r=.00208;head_r=.00357
    m.lathe('screw_bullet_8_32',STEEL,(0,ys[4],0),(1,0,0),[(0,-.018),(stem_r,-.018),(stem_r,-.002),(head_r,0),(head_r,.006),(head_r*.82,.014),(head_r*.45,.022),(0,.026)],32)
    for x in [-.016,-.0135,-.011,-.0085,-.006,-.0035]:m.ring('screw_bullet_8_32',BLACK_NICKEL,(x,ys[4],0),(1,0,0),stem_r,.00012,20,5)
    m.lathe('screw_field_8_32',STEEL,(0,ys[5],0),(1,0,0),[(0,-.018),(stem_r,-.018),(stem_r,-.002),(.0032,0),(.0028,.008),(.0019,.018),(.0007,.029),(0,.032)],32)
    for x in [-.016,-.0135,-.011,-.0085,-.006,-.0035]:m.ring('screw_field_8_32',BLACK_NICKEL,(x,ys[5],0),(1,0,0),stem_r,.00012,20,5)
    # Rotationally symmetric approximation of the chisel family; metadata is explicit about generic silhouette.
    m.lathe('gluein_chisel',STEEL,(0,ys[6],0),(1,0,0),[(0,-.035),(.0018,-.035),(.0018,-.003),(.0032,0),(.0030,.007),(.0013,.020),(0,.024)],24)
    return m


def _nock_body(m,pid,y,gap,stem_r=.0020,sleeve_r=None):
    body_r=sleeve_r or .0030
    m.lathe(pid,POLYMER,(0,y,0),(1,0,0),[(0,-.018),(stem_r,-.018),(stem_r,-.006),(body_r,-.003),(body_r,.006),(0,.006)],24)
    pr=.00110;offset=gap/2+pr
    for z in (-offset,offset):m.tube(pid,POLYMER,[(.003,y,z),(.013,y,z),(.020,y,z*.86)],[pr,pr,.00085],12)


def nocks():
    m=base.Model('Crocodyl / nock and rear-interface taxonomy')
    rows=[
      (-.20,'direct_insert_166','Direct-fit insert nock','Nock stem inserts into a .166-class shaft/bushing. Groove is represented from Lancaster Easton N example (0.081 in groove); not a brand replica.'),
      (-.12,'pin_small','Pin nock / small groove','Polymer nock fitted onto a separate metal pin. Representative groove uses Beiter/Easton small-groove class around .088 in.'),
      (-.04,'pin_large','Pin nock / large groove','Same pin interface with a larger groove class around .098 in.'),
      (.04,'pin_out','Pin-out / over-shaft nock','Nock fits the pin and extends over the end of the shaft, matching the Beiter Pin-Out attachment concept.'),
      (.12,'bushing_insert','Bushing-backed insert nock','Metal rear bushing installed in the shaft with a replaceable insert nock, matching UNI/G-UNI/Super-UNI-style architecture.'),
      (.20,'swaged_conventional','Conventional swaged/tapered rear','Traditional aluminium-shaft nock swage/taper with a conventional replaceable nock.'),
    ]
    for y,pid,title,desc in rows:add_part(m,pid,title,desc,y)
    _nock_body(m,'direct_insert_166',-.20,.00206,stem_r=.00205)
    # Pin nocks: steel pin base, then polymer body.
    for y,pid,gap in [(-.12,'pin_small',.002235),(-.04,'pin_large',.002489)]:
        m.lathe(pid,STEEL,(0,y,0),(1,0,0),[(0,-.028),(.0017,-.028),(.0017,-.006),(.0023,-.003),(.0015,.000),(0,.000)],20)
        _nock_body(m,pid,y,gap,stem_r=.00155)
    # Pin-out has a visibly larger rear sleeve that overlaps shaft OD while still sitting on a pin.
    m.lathe('pin_out',STEEL,(0,.04,0),(1,0,0),[(0,-.030),(.0017,-.030),(.0017,-.005),(.0022,-.002),(0,-.002)],20)
    _nock_body(m,'pin_out',.04,.002235,stem_r=.00155,sleeve_r=.00345)
    # Bushing-backed insert: metal bushing protrudes slightly, polymer stem enters it.
    m.lathe('bushing_insert',ALUMINUM,(0,.12,0),(1,0,0),[(0,-.030),(.0030,-.030),(.0030,-.010),(.00335,-.006),(.00335,-.002),(0,-.002)],24)
    _nock_body(m,'bushing_insert',.12,.00206,stem_r=.0020)
    # Swaged rear tail and conventional nock.
    m.lathe('swaged_conventional',ALUMINUM,(0,.20,0),(1,0,0),[(0,-.030),(.0034,-.030),(.0034,-.016),(.0028,-.009),(.0021,-.003),(0,-.003)],28)
    _nock_body(m,'swaged_conventional',.20,.00206,stem_r=.00205)
    # Impact collar shown as separate taxonomy part because it is a protective accessory, not the nock attachment itself.
    add_part(m,'rear_impact_collar','Rear impact collar','Thin metal collar around the rear shaft end; Lancaster lists the X10 3.2 mm collar at 3 gr. Compatibility remains product-family-specific.',.28)
    m.lathe('rear_impact_collar',ALUMINUM,(0,.28,0),(1,0,0),[(0,-.010),(.00325,-.010),(.00325,.010),(0,.010)],28)
    return m


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    models=[('shaft_profiles',shaft_profiles()),('shaft_constructions',shaft_constructions()),('points',points()),('nock_systems',nocks())]
    manifest={'schemaVersion':1,'generatedBy':'tools/atelier/build_arrow_reference_library.py','scope':'generic educational target-arrow taxonomy; not brand replicas','imagePolicy':'Lancaster product photography used as remote visual reference only; no source images embedded','sources':SOURCES,'models':{}}
    for key,model in models:
        info=base.export_glb(model,OUT/(key+'.glb'))
        manifest['models'][key]=info
        print(key,json.dumps({k:v for k,v in info.items() if k!='parts'}))
    manifest['provenanceRules']={
      'published':'dimension/material/interface stated on a Lancaster product or guide page',
      'photo-observed':'visible silhouette/relationship only, never treated as a measurement',
      'generic-topology':'family is source-backed but exact hidden or dimensional geometry is not published'
    }
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__=='__main__':main()
