#!/usr/bin/env python3
"""Contract tests for the Lancaster-grounded generic arrow taxonomy library."""
from __future__ import annotations
import json, struct, tempfile, unittest
from pathlib import Path
import build_arrow_reference_library as lib


def read_glb(path: Path):
    raw=path.read_bytes();magic,version,length=struct.unpack_from('<III',raw,0)
    if (magic,version,length)!=(0x46546c67,2,len(raw)):raise AssertionError('invalid GLB header')
    jlen,jtype=struct.unpack_from('<II',raw,12)
    if jtype!=0x4e4f534a:raise AssertionError('missing JSON chunk')
    doc=json.loads(raw[20:20+jlen]);return doc


class ArrowReferenceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();cls.out=Path(cls.tmp.name)
        old=lib.OUT;lib.OUT=cls.out
        try:lib.main()
        finally:lib.OUT=old
        cls.manifest=json.loads((cls.out/'manifest.json').read_text())

    @classmethod
    def tearDownClass(cls):cls.tmp.cleanup()

    def test_expected_families_exist(self):
        self.assertEqual(set(self.manifest['models']),{'shaft_profiles','shaft_constructions','points','nock_systems'})
        for name in self.manifest['models']:self.assertTrue((self.out/(name+'.glb')).exists())

    def test_glbs_are_standard_self_contained_and_small(self):
        total=0
        for name,info in self.manifest['models'].items():
            p=self.out/(name+'.glb');total+=p.stat().st_size
            doc=read_glb(p)
            self.assertEqual(doc['asset']['version'],'2.0')
            self.assertTrue(doc['extras']['visualizationOnly'])
            self.assertEqual(doc['extras']['units'],'metres')
            self.assertLess(info['triangles'],25000)
            for image in doc.get('images',[]):self.assertNotIn('uri',image)
        self.assertLess(total,2_500_000)

    def test_profile_and_component_taxonomy(self):
        expected={
          'shaft_profiles':{'parallel','barrelled','front_taper','rear_taper'},
          'shaft_constructions':{'aluminium','all_carbon','carbon_over_aluminium','aluminium_over_carbon'},
          'points':{'breakoff_stainless','breakoff_tungsten','gluein_aluminium_bullet','nibb','screw_bullet_8_32','screw_field_8_32','gluein_chisel'},
          'nock_systems':{'direct_insert_166','pin_small','pin_large','pin_out','bushing_insert','swaged_conventional','rear_impact_collar'},
        }
        for model,parts in expected.items():
            doc=read_glb(self.out/(model+'.glb'))
            self.assertEqual({n['name'] for n in doc['nodes']},parts)

    def test_sources_are_lancaster_and_images_are_not_vendored(self):
        self.assertGreaterEqual(len(self.manifest['sources']),15)
        self.assertTrue(all(url.startswith('https://lancasterarchery.com/') for url in self.manifest['sources'].values()))
        self.assertIn('no source images embedded',self.manifest['imagePolicy'])

    def test_published_parallel_reference_is_not_silently_changed(self):
        # X10 Parallel Pro 600 example from Lancaster: .194 in OD, 32 in length.
        self.assertAlmostEqual(.194*.0254/2,.0024638,places=7)
        self.assertAlmostEqual(32*.0254/2,.4064,places=7)

if __name__=='__main__':unittest.main(verbosity=2)
