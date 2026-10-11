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
        A: android:name(0x01010003)="android.permission.ACCESS_NETWORK_STATE"
      E: uses-permission (line=5)
        A: android:name(0x01010003)="xyz.mdhv.formanalyser.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
"""

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


class MergedManifestContractTest(unittest.TestCase):
    def test_expected_release_surface_passes(self):
        self.assertEqual(audit(PERMISSIONS + LAUNCHER + PROFILE_RECEIVER), [])

    def test_unreviewed_permission_is_rejected(self):
        dump = PERMISSIONS + '''
      E: uses-permission (line=6)
        A: android:name(0x01010003)="android.permission.RECORD_AUDIO"
''' + LAUNCHER + PROFILE_RECEIVER
        self.assertTrue(any("RECORD_AUDIO" in error for error in audit(dump)))

    def test_unprotected_exported_component_is_rejected(self):
        dump = PERMISSIONS + LAUNCHER + PROFILE_RECEIVER + '''
          E: receiver (line=30)
            A: android:name(0x01010003)="com.example.UnreviewedReceiver"
            A: android:exported(0x01010010)=true
'''
        self.assertTrue(any("UnreviewedReceiver" in error for error in audit(dump)))

    def test_profile_receiver_without_signature_permission_is_rejected(self):
        dump = PERMISSIONS + LAUNCHER + PROFILE_RECEIVER.replace(
            'A: android:permission(0x01010006)="android.permission.DUMP"\n', ""
        )
        self.assertTrue(any("ProfileInstallReceiver" in error for error in audit(dump)))

    def test_launcher_must_be_explicitly_exported_and_launcher_filtered(self):
        dump = PERMISSIONS + LAUNCHER.replace(
            'A: android:exported(0x01010010)=true\n', ""
        ) + PROFILE_RECEIVER
        self.assertTrue(any("android:exported" in error for error in audit(dump)))


if __name__ == "__main__":
    unittest.main()
