#!/usr/bin/env python3
"""Deterministic, dependency-free original Crocodyl equipment meshes.
Python 3.10+. Outputs standard glTF 2.0 binary (.glb), metres, +Y up.
Visualization assets, NOT manufacturing specifications or equipment prescriptions.
"""
from __future__ import annotations
import argparse, hashlib, json, math, struct, zlib
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'app-android/src/main/assets/atelier/models'
TAU = math.tau

def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def mul(a,s): return tuple(x*s for x in a)
def dot(a,b): return sum(x*y for x,y in zip(a,b))
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def norm(a):
    l=math.sqrt(dot(a,a)); return mul(a,1/l) if l>1e-12 else (0,1,0)
def mix(a,b,t): return add(mul(a,1-t),mul(b,t))
def srgb(c): return ((c+.055)/1.055)**2.4 if c>.04045 else c/12.92

def mat(name, rgb, metal=0, rough=.4, texture=None):
    p={'baseColorFactor':[srgb(c) for c in rgb]+[1], 'metallicFactor':metal,'roughnessFactor':rough}
    return {'name':name,'pbrMetallicRoughness':p, 'doubleSided':False, '_texture':texture}
MATS=[
 mat('Satin bronze / anodised riser',(.64,.43,.24),.82,.29),
 mat('Carbon composite',(.24,.265,.275),.24,.36,'carbon'),
 mat('Machined stainless',(.68,.73,.76),.96,.23),
 mat('Rubber and serving',(.055,.063,.060),0,.78),
 mat('Oiled walnut grip',(.41,.235,.135),0,.49,'walnut'),
 mat('Emerald polymer',(.30,.73,.43),.1,.28),
 mat('Bowstring fibre',(.73,.76,.67),0,.73),
 mat('Warm white paper',(.93,.917,.865),0,.85),
 mat('Target black',(.055,.07,.08),0,.84),
 mat('Target blue',(.10,.56,.79),0,.79),
 mat('Target red',(.83,.15,.13),0,.79),
 mat('Target gold',(.97,.75,.12),0,.72),
 mat('Compressed straw',(.43,.35,.20),0,.98,'straw'),
 mat('Edge paint',(.175,.20,.17),0,.81),
 mat('Foil bronze accent',(.87,.63,.32),.85,.22),
]

class Mesh:
    def __init__(self): self.v=[];self.f=[];self.uv=[]
    def merge(self,v,f,uv=None):
        o=len(self.v);self.v.extend(v);self.f.extend(tuple(i+o for i in t) for t in f if dot(cross(sub(v[t[1]],v[t[0]]),sub(v[t[2]],v[t[0]])),cross(sub(v[t[1]],v[t[0]]),sub(v[t[2]],v[t[0]]))) > 1e-24)
        self.uv.extend(uv if uv else [(0,0)]*len(v))
    def normals(self):
        n=[[0.,0.,0.] for _ in self.v]
        for i,j,k in self.f:
            c=cross(sub(self.v[j],self.v[i]),sub(self.v[k],self.v[i]))
            for p in (i,j,k):
                for q in range(3): n[p][q]+=c[q]
        # Reconcile UV-seam duplicates, but preserve hard cap/side discontinuities.
        buckets=defaultdict(list)
        for i,p in enumerate(self.v):buckets[tuple(round(c,7) for c in p)].append(i)
        unit=[norm(t) for t in n];out=[]
        for i,p in enumerate(self.v):
            indices=buckets[tuple(round(c,7) for c in p)]
            smooth=[j for j in indices if dot(unit[i],unit[j])>.70]
            out.append(norm(tuple(sum(n[j][k] for j in smooth) for k in range(3))))
        return out

class Model:
    def __init__(self,name): self.name=name;self.meshes=defaultdict(Mesh);self.parts={}
    def part(self,id,title,body,hotspot,explode=(0,0,0)):
        self.parts[id]={'id':id,'title':title,'description':body,'hotspot':hotspot,'explode':explode}
    def mesh(self,part,material,v,f,uv=None): self.meshes[(part,material)].merge(v,f,uv)
    def tube(self,part,material,points,radii,sides=12,caps=True):
        # Parallel transported frame avoids twisting when a path approaches vertical.
        if isinstance(radii,(float,int)): radii=[radii]*len(points)
        v=[];uv=[];f=[];last=None
        for i,p in enumerate(points):
            tangent=norm(sub(points[min(i+1,len(points)-1)],points[max(0,i-1)]))
            if last is None:
                u=norm(cross(tangent,(0,0,1) if abs(tangent[2])<.85 else (0,1,0)))
            else: u=norm(sub(last,mul(tangent,dot(last,tangent))))
            w=cross(tangent,u);last=u
            for j in range(sides+1):
                a=TAU*j/sides;v.append(add(p,add(mul(u,math.cos(a)*radii[i]),mul(w,math.sin(a)*radii[i]))));uv.append((j/sides,i/max(1,len(points)-1)))
        for i in range(len(points)-1):
            for j in range(sides):
                a=i*(sides+1)+j;b=a+sides+1;f.extend([(a,b,a+1),(a+1,b,b+1)])
        side_count = len(f)
        if caps:
            # Independent cap vertices preserve hard material edge normals.
            for i,flip in [(0,True),(len(points)-1,False)]:
                center=len(v);v.append(points[i]);uv.append((.5,.5))
                first=len(v)
                for j in range(sides+1):v.append(v[i*(sides+1)+j]);uv.append((.5+.5*math.cos(TAU*j/sides),.5+.5*math.sin(TAU*j/sides)))
                for j in range(sides): f.append((center,first+j+1,first+j) if flip else (center,first+j,first+j+1))
        # Side parameterization is inward; cap fans already face outward.
        # Reversing both hides cap defects in two-sided viewers but breaks standard culling.
        self.mesh(part,material,v,[(a,c,b) if i < side_count else (a,b,c) for i,(a,b,c) in enumerate(f)],uv)
    def rod(self,p,m,a,b,r,sides=16): self.tube(p,m,[a,b],r,sides)
    def lathe(self,p,m,origin,axis,profile,sides=32):
        axis=norm(axis);u=norm(cross(axis,(0,0,1) if abs(axis[2])<.85 else (0,1,0)));w=cross(axis,u)
        v=[];uv=[];f=[]
        for r,t in profile:
            for j in range(sides+1):
                a=TAU*j/sides;v.append(add(origin,add(mul(axis,t),add(mul(u,r*math.cos(a)),mul(w,r*math.sin(a))))));uv.append((j/sides,t))
        for i in range(len(profile)-1):
            for j in range(sides):
                a=i*(sides+1)+j;b=a+sides+1;f.extend([(a,a+1,b),(a+1,b+1,b)])
        self.mesh(p,m,v,f,uv)
    def ring(self,p,m,c,axis,r,tube,sides=36,minor=8):
        axis=norm(axis);u=norm(cross(axis,(0,0,1) if abs(axis[2])<.85 else (0,1,0)));w=cross(axis,u)
        pts=[add(c,add(mul(u,r*math.cos(TAU*j/sides)),mul(w,r*math.sin(TAU*j/sides)))) for j in range(sides+1)]
        self.tube(p,m,pts,tube,minor,False)
    def ribbon(self,p,m,controls,widths,depths,steps=8,section=12):
        # Smoothed, tapering superellipse section: flat faces and generous rounded edges.
        pts=catmull(controls,steps);v=[];uv=[];f=[]
        for i,c in enumerate(pts):
            t=i/(len(pts)-1);k=min(int(t*(len(widths)-1)),len(widths)-2);q=t*(len(widths)-1)-k
            width=widths[k]*(1-q)+widths[k+1]*q;depth=depths[k]*(1-q)+depths[k+1]*q
            tangent=norm(sub(pts[min(i+1,len(pts)-1)],pts[max(i-1,0)]));across=norm(cross((0,0,1),tangent))
            for j in range(section+1):
                a=TAU*j/section;co=math.cos(a);si=math.sin(a)
                v.append(add(c,add((0,0,math.copysign(abs(co)**.45,co)*width/2),mul(across,math.copysign(abs(si)**.45,si)*depth/2))))
                uv.append((j/section,t*6))
        for i in range(len(pts)-1):
            for j in range(section):
                a=i*(section+1)+j;b=a+section+1;f.extend([(a,b,a+1),(a+1,b,b+1)])
        # Caps use ring fans, section edge is intentionally rounded.
        for i in [0,len(pts)-1]:
            center=len(v);v.append(pts[i]);uv.append((.5,.5))
            for j in range(section):
                a=i*(section+1)+j;f.append((center,a,a+1) if i==0 else (center,a+1,a))
        self.mesh(p,m,v,f,uv)
    def beam(self,p,m,a,b,width,depth):
        # Ribbon swept in XY; for stand legs all components deliberately remain in XY.
        self.ribbon(p,m,[a,b],[width,width],[depth,depth],1,8)


def catmull(controls,steps):
    out=[]
    for k in range(len(controls)-1):
        a=controls[max(0,k-1)];b=controls[k];c=controls[k+1];d=controls[min(len(controls)-1,k+2)]
        for i in range(steps):
            t=i/steps
            out.append(tuple(.5*((2*b[j])+(-a[j]+c[j])*t+(2*a[j]-5*b[j]+4*c[j]-d[j])*t*t+(-a[j]+3*b[j]-3*c[j]+d[j])*t**3) for j in range(3)))
    return out+[controls[-1]]


def bow():
    m=Model('Crocodyl / Olympic recurve study')
    for args in [
      ('riser','Sculpted riser','Original unbranded riser with open truss geometry. A visual study, not a measured replica.',(0,.17,.015),(0,0,0)),
      ('grip','Walnut grip','The grip locates the bow hand. This model does not prescribe grip pressure or hand position.',(-.025,-.025,.022),(-.10,0,.09)),
      ('limbs','Recurved limbs','Tapered composite limbs. Shown braced at rest; there is no simulated draw-weight or flex measurement.',(-.16,.63,0),(0,.10,0)),
      ('string','String and serving','Upper and lower string contacts with a served centre. No release or dry-fire animation.',(-.225,.08,0),(-.09,0,0)),
      ('sight','Recurve sight','Extension, elevation rail and open sight ring. Decorative positions are not sight marks.',(.17,.17,-.072),(.13,.04,-.10)),
      ('stabilisers','Stabilisation system','Long rod, V-bar, side rods and dampers, separated for inspection. Not a recommended weight setup.',(.57,-.10,0),(.13,-.08,0)),
      ('rest','Rest, button and clicker','Distinct small components beside the sight window. Visual identification only.',(-.008,.05,.025),(0,.02,.11)),
      ('hardware','Limb pockets and hardware','Limb pockets, adjustment heads and fasteners are modelled separately.',(.006,.30,0),(0,0,.075))]:m.part(*args)
    # Sculpted side-view truss. Genuine gaps, not a decal pretending to be machined holes.
    for side in [-1,1]:
        ys=[.07,.135,.205,.276,.317]
        m.ribbon('riser',0,[(.011,side*y,0) for y in ys],[.031,.029,.024,.031,.038],[.017,.014,.014,.016,.027],6)
        m.ribbon('riser',0,[(-.032,side*.075,0),(-.054,side*.140,0),(-.044,side*.218,0),(-.015,side*.279,0),(.011,side*.317,0)],[.035,.031,.028,.032,.038],[.024,.018,.016,.018,.024],8)
        m.ribbon('riser',0,[(-.034,side*.078,0),(-.016,side*.080,0),(.012,side*.079,0)],[.035,.035,.030],[.022,.021,.020],4)
        for i,y in enumerate([.125,.195,.263]):
            m.ribbon('riser',0,[(-.040,side*(y-.015),0),(-.013,side*(y+.010),0),(.012,side*(y+.025),0)],[.026,.021,.026],[.012,.011,.012],4)
    m.ribbon('riser',0,[(.006,-.096,0),(.012,-.038,0),(.018,.015,0),(.013,.082,0)],[.034,.029,.023,.030],[.040,.040,.025,.030],8)
    m.ribbon('grip',4,[(-.004,-.091,0),(-.016,-.060,0),(-.019,-.020,0),(-.005,.017,0)],[.035,.040,.043,.028],[.022,.034,.032,.015],12,16)
    # Slim laminated inlays on outer grip edges.
    for z in [-.019,.019]:
        m.tube('grip',14,catmull([(-.015,-.075,z),(-.032,-.039,z),(-.014,.005,z*.72)],10),.00065,8)
    # Limb profiles. String-side sweep with a recurved tip, not a plain longbow arc.
    for s in [-1,1]:
        pts=[(.011,s*.31,0),(-.016,s*.409,0),(-.060,s*.53,0),(-.128,s*.654,0),(-.211,s*.753,0),(-.240,s*.806,0),(-.226,s*.839,0)]
        m.ribbon('limbs',1,pts,[.040,.039,.034,.030,.022,.015,.011],[.007,.0065,.006,.0055,.005,.0045,.004],10,12)
        # Side laminate and tip overlays.
        for z in [-.010,.010]:m.tube('limbs',14,catmull([(-.122,s*.65,z),(-.194,s*.735,z*.85),(-.230,s*.79,z*.55)],8),.00065,6)
        m.ribbon('hardware',0,[(.009,s*.282,0),(.010,s*.305,0),(.005,s*.337,0)],[.048,.049,.043],[.017,.019,.012],4)
        m.lathe('hardware',2,(.025,s*.299,0),(1,0,0),[(0,0),(.011,0),(.012,.002),(.012,.006),(.009,.008),(0,.008)],24)
        m.lathe('hardware',3,(.034,s*.299,0),(1,0,0),[(0,0),(.004,0),(.004,.001),(0,.001)],6)
        m.rod('string',6,(-.226,s*.839,0),(-.240,s*.799,0),.0009,8)
    m.rod('string',6,(-.240,-.799,0),(-.240,.799,0),.00076,10)
    m.rod('string',3,(-.240,-.069,0),(-.240,.135,0),.00125,12)
    for y in [.044,.051]:m.lathe('string',14,(-.240,y,0),(0,1,0),[(0,0),(.00175,0),(.00175,.002),(0,.002)],12)
    # Rest / pressure button / clicker.
    m.lathe('rest',2,(.006,.049,-.028),(0,0,1),[(0,0),(.006,0),(.006,.050),(.003,.054),(0,.054)],16)
    for z in [-.023,-.019,-.015,-.011]:m.ring('rest',3,(.006,.049,z),(0,0,1),.006,.00055,18,6)
    m.tube('rest',2,[(.000,.036,.014),(-.012,.036,.026),(-.024,.039,.030)],.00095,8)
    m.ribbon('rest',2,[(.009,.123,.027),(-.008,.082,.030),(-.025,.043,.034)],[.0045]*3,[.0007]*3,4,8)
    m.lathe('rest',3,(.009,.125,.023),(0,0,1),[(0,0),(.004,0),(.004,.009),(0,.009)],12)
    # Two-rail sight extension and structural bridges.
    for y in [.165,.178]:m.rod('sight',1,(.012,y,-.055),(.232,y,-.055),.003,10)
    for x in [.019,.083,.15,.225]:m.rod('sight',0,(x,.165,-.055),(x,.178,-.055),.004,10)
    m.rod('sight',2,(.020,.171,-.025),(.020,.171,-.059),.008,16)
    for z in [-.059,-.070]:m.rod('sight',1,(.232,.091,z),(.232,.25,z),.0028,10)
    m.rod('sight',2,(.232,.171,-.064),(.232,.171,-.105),.0032,12)
    m.ring('sight',0,(.232,.171,-.108),(1,0,0),.010,.0018,36,8)
    # Sight pin ends at centre, not a closed glass optic or a compound scope.
    m.rod('sight',2,(.232,.161,-.108),(.232,.171,-.108),.0006,6)
    m.lathe('sight',5,(.232,.171,-.108),(1,0,0),[(0,-.001),(.001,-.001),(.001,.001),(0,.001)],10)
    for y in [.09,.25]:m.lathe('sight',0,(.232,y,-.064),(0,1,0),[(0,0),(.007,0),(.007,.008),(0,.008)],20)
    # Long stabiliser, extension, V-bar and damped side rods.
    m.lathe('stabilisers',0,(.023,-.101,0),(1,0,0),[(0,0),(.012,0),(.012,.044),(.009,.048),(0,.048)],24)
    m.rod('stabilisers',1,(.067,-.101,0),(.836,-.101,0),.006,18)
    m.rod('stabilisers',0,(.067,-.102,-.041),(.067,-.102,.041),.007,16)
    for sign in [-1,1]:
        a=(.065,-.103,sign*.037);b=(-.175,-.161,sign*.230)
        m.rod('stabilisers',1,a,b,.0055,14)
        axis=norm(sub(b,a));m.lathe('stabilisers',3,b,axis,[(0,0),(.011,0),(.014,.011),(.013,.028),(.008,.035),(0,.035)],20)
        m.lathe('stabilisers',2,add(b,mul(axis,.035)),axis,[(0,0),(.016,0),(.016,.011),(.014,.014),(0,.014)],28)
    m.lathe('stabilisers',3,(.825,-.101,0),(1,0,0),[(0,0),(.011,0),(.014,.010),(.010,.025),(.014,.04),(.011,.05),(0,.05)],24)
    for x in [.878,.889,.900]:m.lathe('stabilisers',2,(x,-.101,0),(1,0,0),[(0,0),(.017,0),(.018,.003),(.018,.007),(.017,.01),(0,.01)],32)
    # Visible hex fasteners without logos or fictitious model markings.
    for s in [-1,1]:
        for y in [-.242,-.14,.20,.263]:
            m.lathe('hardware',2,(-.005,y,s*.015),(0,0,s),[(0,0),(.003,0),(.003,.002),(0,.002)],6)
    return m


def arrow():
    m=Model('Crocodyl / target arrow anatomy')
    for a in [
      ('shaft','Carbon shaft','A slender carbon target-arrow study. Spine, length and mass are not fitted to the athlete.',(0,0,0),(0,0,0)),
      ('point','Target point','Rounded target-point profile, shown separately for identification. Not a broadhead.',(.376,0,0),(.11,0,0)),
      ('nock','Slotted nock','A visible string slot in a separate polymer nock; not a solid cone.',(-.382,0,0),(-.10,0,0)),
      ('vanes','Three fletching vanes','Three individually modelled thin vanes at 120-degree spacing.',(-.315,.010,0),(-.01,.050,0)),
      ('wrap','Wrap and identification bands','Unbranded wrap and collars. No invented spine specifications.',(-.333,0,0),(0,-.05,0))]:m.part(*a)
    m.lathe('shaft',1,(0,0,0),(1,0,0),[(0,-.365),(.0030,-.365),(.0031,0),(.0029,.344),(0,.344)],28)
    m.lathe('point',2,(0,0,0),(1,0,0),[(0,.333),(.0028,.333),(.0031,.344),(.0033,.357),(.0025,.374),(.0010,.389),(0,.393)],32)
    m.lathe('wrap',7,(0,0,0),(1,0,0),[(0,-.360),(.00318,-.360),(.00318,-.275),(0,-.275)],28)
    for x in [-.359,-.354,-.282,-.278]:m.lathe('wrap',14,(x,0,0),(1,0,0),[(0,0),(.00326,0),(.00326,.0013),(0,.0013)],24)
    # Nock body and two prongs leave an actual empty string slot.
    m.lathe('nock',5,(-.365,0,0),(-1,0,0),[(0,0),(.0029,0),(.0040,.009),(.0038,.014),(0,.014)],24)
    for z in [-.0024,.0024]:
        m.tube('nock',5,[(-.377,0,z),(-.385,0,z),(-.391,0,z*.93)],[.0016,.0016,.0012],12)
    # Two-sided, gently helically curved sheet vanes with real thin side edges.
    for j in range(3):
        v=[];f=[];uv=[];steps=28;spans=5
        for side in [-1,1]:
            for i in range(steps+1):
                t=i/steps;x=-.348+.065*t
                height=.001+.012*(math.sin(math.pi*t)**.65)*(1-.23*t)
                for k in range(spans+1):
                    q=k/spans;r=.0032+height*q;ang=TAU*j/3+.18*t+.11*q
                    v.append((x,r*math.cos(ang)+side*.00014*math.sin(ang),r*math.sin(ang)-side*.00014*math.cos(ang)));uv.append((t,q))
        layer=(steps+1)*(spans+1)
        for s in range(2):
            for i in range(steps):
                for k in range(spans):
                    a=s*layer+i*(spans+1)+k;b=a+spans+1
                    tris=[(a,b,a+1),(a+1,b,b+1)];f.extend(tris if s==0 else [(a,c,b) for a,b,c in tris])
        for i in range(steps):
            for k in [0,spans]:
                a=i*(spans+1)+k;b=a+spans+1;f.extend([(a,a+layer,b),(b,a+layer,b+layer)])
        m.mesh('vanes',5 if j!=2 else 7,v,f,uv)
    return m


def target():
    m=Model('Crocodyl / recurve target')
    for a in [
      ('face','Ten-ring target face','Ten concentric scoring rings with the inner-X guide. This is an equipment illustration, not the scoring engine.',(0,.70,.115),(0,0,.17)),
      ('butt','Compressed target butt','A layered cylindrical practice butt, with restrained fibre texture.',(.56,.70,0),(0,0,-.07)),
      ('stand','Timber stand','Three supports, cross brace and retaining ledge. Visualization only, not a construction plan.',(.35,-.06,0),(0,-.06,0)),
      ('pins','Face retainers','Four visible paper retainers near the outer edge.',(.43,1.13,.12),(0,0,.22))]:m.part(*a)
    # Face is 1.22 m across; a 10-ring illustrative recurve face with equal radial increments.
    origin=(0,.70,0);axis=(0,0,1)
    m.lathe('butt',12,origin,axis,[(0,-.12),(.625,-.12),(.644,-.10),(.644,.079),(.626,.103),(0,.103)],128)
    for z in [-.105,-.071,-.02,.03,.078]:m.ring('butt',13,(0,.70,z),axis,.644,.0028,128,6)
    for ring in range(10):
        r0=ring*.061;r1=(ring+1)*.061;material=[11,11,10,10,9,9,8,8,7,7][ring]
        v=[];uv=[];f=[]
        for r in [r0,r1]:
            for i in range(129):
                a=TAU*i/128;v.append((r*math.cos(a),.70+r*math.sin(a),.107));uv.append((.5+r*math.cos(a)/1.22,.5+r*math.sin(a)/1.22))
        for i in range(128):f.extend([(i,129+i,130+i),(i,130+i,i+1)])
        m.mesh('face',material,v,f,uv)
        m.ring('face',8,(0,.70,.1078),axis,r1,.00048,128,4)
    m.ring('face',8,(0,.70,.108),axis,.0305,.0004,96,4)
    for a,b in [((-.005,.70,.109),(.005,.70,.109)),((0,.695,.109),(0,.705,.109))]:m.rod('face',8,a,b,.00065,6)
    # Front A-frame and rear prop. Deliberately no arrows left in the face.
    for sign in [-1,1]:
        m.beam('stand',4,(sign*.12,1.22,-.080),(sign*.48,-.62,-.08),.058,.046)
        m.rod('stand',3,(sign*.48,-.62,-.08),(sign*.48,-.63,-.08),.039,16)
    m.beam('stand',4,(-.46,-.40,-.095),(.46,-.40,-.095),.043,.046)
    m.beam('stand',4,(-.51,.086,.00),(.51,.086,.00),.09,.042)
    m.tube('stand',4,[(0,1.08,-.14),(0,-.62,-.65)],.026,8)
    m.rod('stand',2,(-.32,-.20,-.10),(0,-.20,-.50),.0025,8)
    m.rod('stand',2,(.32,-.20,-.10),(0,-.20,-.50),.0025,8)
    for a in [math.pi*.25,math.pi*.75,math.pi*1.25,math.pi*1.75]:
        x=.595*math.cos(a);y=.70+.595*math.sin(a)
        m.lathe('pins',0,(x,y,.109),axis,[(0,0),(.007,0),(.007,.004),(.004,.007),(0,.007)],16)
    return m


def png_texture(kind,size=128):
    # Embedded, original procedural colour textures: no downloads or copyrighted images.
    pixels=bytearray()
    for y in range(size):
        pixels.append(0)
        for x in range(size):
            h=((x*73856093)^(y*19349663))&255
            if kind=='carbon':
                tile=((x//8)+(y//8))%2
                strand=(x if tile else y)%8
                b=.73+.16*math.sin(math.pi*strand/8)+h/255*.012
                rgb=(b,b*1.04,b*1.08)
            elif kind=='walnut':
                grain=math.sin(x*.21+4*math.sin(y*.019)+2*math.sin(y*.067))
                micro=math.sin(x*.99+y*.023)*.03
                b=.78+.13*grain+micro+(h/255-.5)*.055
                rgb=(b,b*.87,b*.72)
            else:
                b=.75+.18*math.sin(x*1.41+math.sin(y*.23))+(h/255-.5)*.12
                rgb=(b,b*.95,b*.82)
            pixels.extend(max(0,min(255,round(c*255))) for c in rgb)
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(pixels),9))+chunk(b'IEND',b'')


def export_glb(model,path):
    data=bytearray();views=[];access=[]
    def block(raw,target=None):
        while len(data)%4:data.append(0)
        view={'buffer':0,'byteOffset':len(data),'byteLength':len(raw)}
        if target:view['target']=target
        views.append(view);data.extend(raw);return len(views)-1
    def attr(values,n,component=5126,target=34962):
        flat=[c for a in values for c in (a if isinstance(a,(list,tuple)) else [a])]
        code={5126:'f',5125:'I'}[component]
        raw=struct.pack('<'+code*len(flat),*flat);v=block(raw,target)
        a={'bufferView':v,'componentType':component,'count':len(values),'type':{1:'SCALAR',2:'VEC2',3:'VEC3'}[n]}
        if n==3 and component==5126:a.update(min=[min(a[i] for a in values) for i in range(3)],max=[max(a[i] for a in values) for i in range(3)])
        access.append(a);return len(access)-1
    gl={'asset':{'version':'2.0','generator':'Crocodyl original equipment atelier 1.0','copyright':'Original Crocodyl visualization assets'},'scene':0,'scenes':[{'nodes':[]}],'nodes':[],'meshes':[],'materials':[],'bufferViews':views,'accessors':access,'buffers':[]}
    images=[];textures=[];texids={}
    usedm=sorted({m for p,m in model.meshes});mmap={}
    for old in usedm:
        material=json.loads(json.dumps(MATS[old]));texture=material.pop('_texture',None)
        if texture:
            if texture not in texids:
                images.append({'bufferView':block(png_texture(texture)),'mimeType':'image/png','name':texture})
                textures.append({'source':len(images)-1,'sampler':0});texids[texture]=len(textures)-1
            material['pbrMetallicRoughness']['baseColorTexture']={'index':texids[texture]}
        mmap[old]=len(gl['materials']);gl['materials'].append(material)
    if images:gl.update(images=images,textures=textures,samplers=[{'magFilter':9729,'minFilter':9987,'wrapS':10497,'wrapT':10497}])
    triangles=0;vertices=0
    for id,part in model.parts.items():
        prim=[]
        for (pid,material),mesh in model.meshes.items():
            if pid!=id:continue
            indices=[i for t in mesh.f for i in t]
            prim.append({'attributes':{'POSITION':attr(mesh.v,3),'NORMAL':attr(mesh.normals(),3),'TEXCOORD_0':attr(mesh.uv,2)},'indices':attr(indices,1,5125,34963),'material':mmap[material]})
            triangles+=len(mesh.f);vertices+=len(mesh.v)
        if not prim:continue
        gl['meshes'].append({'name':id,'primitives':prim})
        node={'name':id,'mesh':len(gl['meshes'])-1,'extras':part}
        gl['scenes'][0]['nodes'].append(len(gl['meshes'])-1);gl['nodes'].append(node)
    while len(data)%4:data.append(0)
    gl['buffers']=[{'byteLength':len(data)}]
    gl['extras']={'title':model.name,'units':'metres','upAxis':'+Y','visualizationOnly':True,'triangleCount':triangles,'vertexCount':vertices}
    raw=json.dumps(gl,separators=(',',':'),ensure_ascii=True).encode()
    raw+=b' '*((-len(raw))%4)
    result=struct.pack('<III',0x46546c67,2,12+8+len(raw)+8+len(data))+struct.pack('<II',len(raw),0x4e4f534a)+raw+struct.pack('<II',len(data),0x004e4942)+data
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(result)
    return {'file':path.name,'bytes':len(result),'sha256':hashlib.sha256(result).hexdigest(),'triangles':triangles,'vertices':vertices,'parts':list(model.parts.values())}


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',type=Path,default=OUT);args=ap.parse_args()
    manifest={'version':1,'units':'metres','visualizationOnly':True,'models':{}}
    for key,fn in [('recurve',bow),('arrow',arrow),('target',target)]:
        manifest['models'][key]=export_glb(fn(),args.out/(key+'.glb'))
        print(key,json.dumps({k:v for k,v in manifest['models'][key].items() if k!='parts'}))
    args.out.mkdir(parents=True,exist_ok=True);(args.out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__=='__main__':main()
