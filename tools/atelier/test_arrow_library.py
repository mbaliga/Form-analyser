#!/usr/bin/env python3
import json, math, struct, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/'app-android/src/main/assets/atelier/arrow-library'

def glb(name):
    b=(ROOT/name).read_bytes();magic,ver,total=struct.unpack_from('<III',b,0);assert (magic,ver,total)==(0x46546c67,2,len(b));jl,jt=struct.unpack_from('<II',b,12);assert jt==0x4e4f534a;doc=json.loads(b[20:20+jl]);off=20+jl;bl,bt=struct.unpack_from('<II',b,off);assert bt==0x004e4942 and off+8+bl==len(b);return doc,b[off+8:]
def vals(doc,buf,idx):
    a=doc['accessors'][idx];v=doc['bufferViews'][a['bufferView']];n={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']];fmt={5126:'f',5125:'I'}[a['componentType']];raw=struct.unpack_from('<'+fmt*(a['count']*n),buf,v['byteOffset']+a.get('byteOffset',0));return [raw[i:i+n] for i in range(0,len(raw),n)]
class ArrowLibrary(unittest.TestCase):
    def test_manifest_and_files(self):
        m=json.loads((ROOT/'arrow_library_manifest.json').read_text());self.assertTrue(m['notBrandReplicas']);self.assertTrue(m['notFittingAdvice']);self.assertEqual(set(m['models']),{'shaft_profiles','shaft_constructions','points','nock_systems','target_face_square'})
        for x in m['models'].values():self.assertLess(x['bytes'],1_500_000);self.assertTrue((ROOT/x['file']).exists())
    def test_glb_structure_and_finite_geometry(self):
        for p in ROOT.glob('*.glb'):
            doc,buf=glb(p.name);self.assertEqual(doc['asset']['version'],'2.0')
            for mesh in doc['meshes']:
                for pr in mesh['primitives']:
                    pos=vals(doc,buf,pr['attributes']['POSITION']);nor=vals(doc,buf,pr['attributes']['NORMAL']);idx=[v[0] for v in vals(doc,buf,pr['indices'])]
                    self.assertEqual(len(pos),len(nor));self.assertTrue(all(math.isfinite(c) for row in pos+nor for c in row));self.assertTrue(all(0<=i<len(pos) for i in idx));self.assertEqual(len(idx)%3,0)
    def test_semantic_parts(self):
        expected={'shaft_profiles.glb':{'parallel','barrelled','single_taper'},'shaft_constructions.glb':{'aluminium','carbon','aluminium_carbon'},'points.glb':{'bullet','zinc_glue_in','stainless_breakoff','tungsten_breakoff','screw_in_field'},'nock_systems.glb':{'direct_fit','pin_nock','overnock','bushing_insert'},'target_face_square.glb':{'face','retainers'}}
        for name,parts in expected.items():
            doc,_=glb(name);self.assertEqual({n['name'] for n in doc['nodes']},parts)
            for n in doc['nodes']:self.assertTrue(n['extras']['sourceRefs'])
    def test_target_sheet_is_square(self):
        doc,buf=glb('target_face_square.glb');node=next(n for n in doc['nodes'] if n['name']=='face');mesh=doc['meshes'][node['mesh']];paper=next(i for i,m in enumerate(doc['materials']) if m['name']=='Warm white paper');pr=next(x for x in mesh['primitives'] if x['material']==paper);p=vals(doc,buf,pr['attributes']['POSITION']);xs=[v[0] for v in p];ys=[v[1] for v in p];self.assertAlmostEqual(max(xs)-min(xs),1.22,4);self.assertAlmostEqual(max(ys)-min(ys),1.22,4)
if __name__=='__main__':unittest.main(verbosity=2)
