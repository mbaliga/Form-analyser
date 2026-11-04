"""Regression tests for the merged Android manifest release contract."""

import unittest

from verify_manifest_contract import audit


PERMISSIONS = """
      E: uses-permission (line=1)
        A: android:name(0x01010003)="android.permission.CAMERA"
      E: uses-permission (line=2)
        A: android:name(0x01010003)="android.permission.VIBRATE"
      E: uses-permission (line=3)
        A: android:name(0x01010003)="android.permission.INTERNET"
      E: uses-permission (line=4)
        A: android:name(0x01010003)="com.asoc.crocodyl.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
"""

APPLICATION = """
        E: application (line=6)
"""

APPLICATION_DEBUGGABLE = APPLICATION.rstrip() + "\n          A: android:debuggable(0x0101000f)=true\n"
APPLICATION_TEST_ONLY = APPLICATION.rstrip() + "\n          A: android:testOnly(0x01010272)=true\n"

LAUNCHER = """
          E: activity (line=10)
            A: android:name(0x01010003)="xyz.mdhv.formanalyser.app.MainActivity"
            A: android:exported(0x01010010)=true
              E: intent-filter (line=12)
                  E: action (line=13)
                    A: android:name(0x01010003)="android.intent.action.MAIN"
                  E: category (line=14)
                    A: android:name(0x01010003)="android.intent.category.LAUNCHER"
"""

PROFILE_RECEIVER = """
          E: receiver (line=20)
            A: android:name(0x01010003)="androidx.profileinstaller.ProfileInstallReceiver"
            A: android:permission(0x01010006)="android.permission.DUMP"
            A: android:exported(0x01010010)=true
"""


# What transport-runtime and transport-backend-cct (MediaPipe's usage-statistics upload path) put into
# a merged manifest. A non-exported service passes the exported-component audit, so only the
# datatransport marker check catches it.
DATATRANSPORT_DISCOVERY = """
          E: service (line=40)
            A: android:name(0x01010003)="com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
            A: android:exported(0x01010010)=false
              E: meta-data (line=42)
                A: android:name(0x01010003)="backend:com.google.android.datatransport.cct.CctBackendFactory"
                A: android:value(0x01010024)="cct"
"""


class MergedManifestContractTest(unittest.TestCase):
    def test_expected_release_surface_passes(self):
        self.assertEqual(audit(PERMISSIONS + APPLICATION + LAUNCHER + PROFILE_RECEIVER), [])

    def test_access_network_state_is_no_longer_an_allowed_permission(self):
        dump = PERMISSIONS + '''
      E: uses-permission (line=6)
        A: android:name(0x01010003)="android.permission.ACCESS_NETWORK_STATE"
''' + APPLICATION + LAUNCHER + PROFILE_RECEIVER
        errors = audit(dump)
        self.assertTrue(any("ACCESS_NETWORK_STATE" in error for error in errors), errors)

    def test_datatransport_upload_path_in_the_merged_manifest_is_rejected(self):
        errors = audit(PERMISSIONS + APPLICATION + LAUNCHER + PROFILE_RECEIVER + DATATRANSPORT_DISCOVERY)
        self.assertTrue(any("TransportBackendDiscovery" in error or "datatransport" in error for error in errors), errors)
        joined = "\n".join(errors)
        for marker in ("com.google.android.datatransport", "backend:", "CctBackendFactory"):
            self.assertIn(marker, joined)

    def test_each_datatransport_marker_is_rejected_on_its_own(self):
        for marker in ("com.google.android.datatransport", "backend:", "CctBackendFactory"):
            with self.subTest(marker=marker):
                dump = PERMISSIONS + APPLICATION + LAUNCHER + PROFILE_RECEIVER + f'''
          E: service (line=40)
            A: android:name(0x01010003)="x.y.{marker}Z"
            A: android:exported(0x01010010)=false
'''
                errors = audit(dump)
                self.assertTrue(any(marker in error for error in errors), errors)

    def test_debuggable_release_application_is_rejected(self):
        errors = audit(PERMISSIONS + APPLICATION_DEBUGGABLE + LAUNCHER + PROFILE_RECEIVER)
        self.assertIn("Release application must not be debuggable", errors)

    def test_test_only_release_application_is_rejected(self):
        errors = audit(PERMISSIONS + APPLICATION_TEST_ONLY + LAUNCHER + PROFILE_RECEIVER)
        self.assertIn("Release application must not be marked testOnly", errors)

    def test_missing_application_element_is_rejected(self):
        errors = audit(PERMISSIONS + LAUNCHER + PROFILE_RECEIVER)
        self.assertIn("Exactly one application element is required", errors)

    def test_unreviewed_permission_is_rejected(self):
        dump = PERMISSIONS + '''
      E: uses-permission (line=6)
        A: android:name(0x01010003)="android.permission.RECORD_AUDIO"
''' + APPLICATION + LAUNCHER + PROFILE_RECEIVER
        self.assertTrue(any("RECORD_AUDIO" in error for error in audit(dump)))

    def test_unprotected_exported_component_is_rejected(self):
        dump = PERMISSIONS + APPLICATION + LAUNCHER + PROFILE_RECEIVER + '''
          E: receiver (line=30)
            A: android:name(0x01010003)="com.example.UnreviewedReceiver"
            A: android:exported(0x01010010)=true
'''
        self.assertTrue(any("UnreviewedReceiver" in error for error in audit(dump)))

    def test_profile_receiver_without_signature_permission_is_rejected(self):
        dump = PERMISSIONS + APPLICATION + LAUNCHER + PROFILE_RECEIVER.replace(
            'A: android:permission(0x01010006)="android.permission.DUMP"\n', ""
        )
        self.assertTrue(any("ProfileInstallReceiver" in error for error in audit(dump)))

    def test_launcher_must_be_explicitly_exported_and_launcher_filtered(self):
        dump = PERMISSIONS + APPLICATION + LAUNCHER.replace(
            'A: android:exported(0x01010010)=true\n', ""
        ) + PROFILE_RECEIVER
        self.assertTrue(any("android:exported" in error for error in audit(dump)))


if __name__ == "__main__":
    unittest.main()
