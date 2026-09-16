"""CPU-only contract tests for the original glTF assets; Python stdlib only."""
import hashlib
import json
import math
import struct
import tempfile
import unittest
from pathlib import Path
import build_assets as bake


def read_glb(path):
    data=path.read_bytes()
    magic,version,length=struct.unpack_from('<III',data)
    assert (magic,version,length)==(0x46546c67,2,len(data))
    jlen,jtype=struct.unpack_from('<II',data,12)
    assert jtype==0x4e4f534a and jlen%4==0
    doc=json.loads(data[20:20+jlen]);off=20+jlen
    blen,btype=struct.unpack_from('<II',data,off)
    assert btype==0x004e4942 and off+8+blen==len(data)
    return doc,data[off+8:]


def values(doc,buf,index):
    a=doc['accessors'][index];v=doc['bufferViews'][a['bufferView']]
    width={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
    code={5126:'f',5125:'I'}[a['componentType']]
    nums=struct.unpack_from('<'+code*(a['count']*width),buf,v['byteOffset']+a.get('byteOffset',0))
    return [nums[i:i+width] for i in range(0,len(nums),width)]


class AssetContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();cls.root=Path(cls.tmp.name)
        cls.models={k:fn() for k,fn in [('recurve',bake.bow),('arrow',bake.arrow),('target',bake.target)]}
        cls.info={k:bake.export_glb(m,cls.root/(k+'.glb')) for k,m in cls.models.items()}

    @classmethod
    def tearDownClass(cls):cls.tmp.cleanup()

    def test_standard_glb_headers_alignment_and_embedded_dependencies(self):
        for key in self.models:
            with self.subTest(key=key):
                doc,buf=read_glb(self.root/(key+'.glb'))
                self.assertEqual(doc['asset']['version'],'2.0')
                self.assertEqual(doc['buffers'][0]['byteLength'],len(buf))
                self.assertEqual(doc['extras']['units'],'metres')
                self.assertTrue(doc['extras']['visualizationOnly'])
                for v in doc['bufferViews']:
                    self.assertEqual(v['byteOffset']%4,0)
                    self.assertLessEqual(v['byteOffset']+v['byteLength'],len(buf))
                for image in doc.get('images',[]):self.assertNotIn('uri',image)

    def test_finite_vertices_unit_normals_and_valid_indices(self):
        for key in self.models:
            doc,buf=read_glb(self.root/(key+'.glb'))
            for mesh in doc['meshes']:
                for p in mesh['primitives']:
                    verts=values(doc,buf,p['attributes']['POSITION'])
                    normals=values(doc,buf,p['attributes']['NORMAL'])
                    uvs=values(doc,buf,p['attributes']['TEXCOORD_0'])
                    indices=[v[0] for v in values(doc,buf,p['indices'])]
                    self.assertEqual(len(verts),len(normals));self.assertEqual(len(verts),len(uvs))
                    self.assertTrue(all(math.isfinite(c) for a in [verts,normals,uvs] for v in a for c in v))
                    self.assertTrue(all(abs(math.sqrt(sum(c*c for c in n))-1)<1e-4 for n in normals))
                    self.assertEqual(len(indices)%3,0)
                    self.assertTrue(all(0<=i<len(verts) for i in indices))
                    self.assertTrue(all(abs(c)<5 for v in verts for c in v))

    def test_mobile_budgets(self):
        self.assertLess(sum(i['bytes'] for i in self.info.values()),2_000_000)
        for i in self.info.values():
            self.assertLess(i['triangles'],30_000)
            self.assertLess(i['vertices'],25_000)

    def test_semantic_parts_are_stable_and_nonempty(self):
        expected={'recurve':{'riser','grip','limbs','string','sight','stabilisers','rest','hardware'},'arrow':{'shaft','point','nock','vanes','wrap'},'target':{'face','butt','stand','pins'}}
        for key in self.models:
            doc,_=read_glb(self.root/(key+'.glb'))
            self.assertEqual({n['name'] for n in doc['nodes']},expected[key])
            for n in doc['nodes']:
                self.assertEqual(n['name'],n['extras']['id'])
                self.assertEqual(len(n['extras']['hotspot']),3)
                self.assertEqual(len(n['extras']['explode']),3)
                self.assertTrue(doc['meshes'][n['mesh']]['primitives'])

    def test_deterministic_bake_and_checksums(self):
        for key,model in self.models.items():
            another=self.root/(key+'-again.glb');i=bake.export_glb(model,another)
            self.assertEqual(i['sha256'],self.info[key]['sha256'])
            self.assertEqual(hashlib.sha256(another.read_bytes()).hexdigest(),i['sha256'])

    def test_standard_material_values(self):
        for key in self.models:
            doc,_=read_glb(self.root/(key+'.glb'))
            for m in doc['materials']:
                p=m['pbrMetallicRoughness']
                self.assertTrue(all(0<=v<=1 for v in p['baseColorFactor']))
                self.assertTrue(0<=p['metallicFactor']<=1)
                self.assertTrue(0<=p['roughnessFactor']<=1)

    def test_authored_triangles_are_not_degenerate(self):
        for model in self.models.values():
            for mesh in model.meshes.values():
                for i,j,k in mesh.f:
                    n=bake.cross(bake.sub(mesh.v[j],mesh.v[i]),bake.sub(mesh.v[k],mesh.v[i]))
                    self.assertGreater(bake.dot(n,n),1e-24)

if __name__=='__main__':unittest.main(verbosity=2)
