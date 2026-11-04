# Third-party notices

Crocodyl is built on the components listed here. Each one is distributed under its own licence, named below as the component's published Maven POM declares it. This file covers those components and the bundled pose model only, not Crocodyl's own code.

The same text is shown in the app under Settings → About → Third-party licences, and is kept in the repository as THIRD_PARTY_NOTICES.md. The two copies are identical.

## Apache License, Version 2.0

Declared in the POM of every component in this section. The licence text is at the end of this file and at https://www.apache.org/licenses/LICENSE-2.0

- androidx.activity: activity, activity-compose, activity-ktx
- androidx.annotation: annotation, annotation-experimental, annotation-jvm
- androidx.appcompat: appcompat, appcompat-resources
- androidx.arch.core: core-common, core-runtime
- androidx.autofill: autofill
- androidx.camera: camera-camera2, camera-core, camera-lifecycle, camera-video, camera-view
- androidx.collection: collection, collection-jvm, collection-ktx
- androidx.compose: compose-bom
- androidx.compose.animation: animation, animation-android, animation-core, animation-core-android
- androidx.compose.foundation: foundation, foundation-android, foundation-layout, foundation-layout-android
- androidx.compose.material: material-icons-core, material-icons-core-android, material-icons-extended, material-icons-extended-android, material-ripple, material-ripple-android
- androidx.compose.material3: material3, material3-android
- androidx.compose.runtime: runtime, runtime-android, runtime-saveable, runtime-saveable-android
- androidx.compose.ui: ui, ui-android, ui-geometry, ui-geometry-android, ui-graphics, ui-graphics-android, ui-text, ui-text-android, ui-tooling-preview, ui-tooling-preview-android, ui-unit, ui-unit-android, ui-util, ui-util-android
- androidx.concurrent: concurrent-futures, concurrent-futures-ktx
- androidx.core: core, core-ktx
- androidx.cursoradapter: cursoradapter
- androidx.customview: customview, customview-poolingcontainer
- androidx.datastore: datastore, datastore-android, datastore-core, datastore-core-android, datastore-core-okio, datastore-core-okio-jvm, datastore-preferences, datastore-preferences-android, datastore-preferences-core, datastore-preferences-core-jvm
- androidx.drawerlayout: drawerlayout
- androidx.emoji2: emoji2
- androidx.exifinterface: exifinterface
- androidx.fragment: fragment
- androidx.graphics: graphics-path
- androidx.interpolator: interpolator
- androidx.lifecycle: lifecycle-common, lifecycle-common-java8, lifecycle-common-jvm, lifecycle-livedata, lifecycle-livedata-core, lifecycle-livedata-core-ktx, lifecycle-process, lifecycle-runtime, lifecycle-runtime-android, lifecycle-runtime-compose, lifecycle-runtime-compose-android, lifecycle-runtime-ktx, lifecycle-runtime-ktx-android, lifecycle-viewmodel, lifecycle-viewmodel-android, lifecycle-viewmodel-compose, lifecycle-viewmodel-compose-android, lifecycle-viewmodel-ktx, lifecycle-viewmodel-savedstate
- androidx.loader: loader
- androidx.navigation: navigation-common, navigation-common-ktx, navigation-compose, navigation-runtime, navigation-runtime-ktx
- androidx.profileinstaller: profileinstaller
- androidx.room: room-common, room-common-jvm, room-ktx, room-runtime, room-runtime-android
- androidx.savedstate: savedstate, savedstate-ktx
- androidx.sqlite: sqlite, sqlite-android, sqlite-framework, sqlite-framework-android
- androidx.startup: startup-runtime
- androidx.tracing: tracing, tracing-ktx
- androidx.vectordrawable: vectordrawable, vectordrawable-animated
- androidx.versionedparcelable: versionedparcelable
- androidx.viewpager: viewpager
- com.google.android.datatransport: transport-api, transport-backend-cct, transport-runtime
- com.google.auto.value: auto-value-annotations
- com.google.code.findbugs: jsr305
- com.google.code.gson: gson
- com.google.crypto.tink: tink-android
- com.google.errorprone: error_prone_annotations
- com.google.firebase: firebase-encoders, firebase-encoders-json, firebase-encoders-proto
- com.google.flogger: flogger, flogger-system-backend
- com.google.guava: failureaccess, guava, listenablefuture
- com.google.j2objc: j2objc-annotations
- com.google.mediapipe: tasks-core, tasks-genai, tasks-vision
- com.squareup.okio: okio, okio-jvm
- javax.inject: javax.inject
- org.jetbrains: annotations
- org.jetbrains.kotlin: kotlin-android-extensions-runtime, kotlin-parcelize-runtime, kotlin-stdlib, kotlin-stdlib-common, kotlin-stdlib-jdk7, kotlin-stdlib-jdk8
- org.jetbrains.kotlinx: kotlinx-coroutines-android, kotlinx-coroutines-bom, kotlinx-coroutines-core, kotlinx-coroutines-core-jvm, kotlinx-serialization-bom, kotlinx-serialization-core, kotlinx-serialization-core-jvm, kotlinx-serialization-json, kotlinx-serialization-json-jvm
- org.jspecify: jspecify

## BSD 3-Clause License

- com.google.protobuf: protobuf-javalite. POM: "BSD-3-Clause", https://opensource.org/licenses/BSD-3-Clause

## MIT License

- org.codehaus.mojo: animal-sniffer-annotations. POM: "MIT license", http://www.opensource.org/licenses/mit-license.php

## Dual-licensed

- org.checkerframework: checker-compat-qual. Its POM lists two licences: "GNU General Public License, version 2 (GPL2), with the classpath exception" (http://www.gnu.org/software/classpath/license.html) and "The MIT License" (http://opensource.org/licenses/MIT).

## libyuv (contained in CameraX camera-core)

The POM of androidx.camera:camera-core declares a second licence, "BSD License", for the libyuv code it contains (https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/README.chromium, which names it BSD-3-Clause). The libyuv licence text:

Copyright 2011 The LibYuv Project Authors. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in
    the documentation and/or other materials provided with the
    distribution.

  * Neither the name of Google nor the names of its contributors may
    be used to endorse or promote products derived from this software
    without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Pose model

- MediaPipe BlazePose pose landmarker (lite), bundled in the app as assets/pose_landmarker_lite.task. Google's model card for BlazePose states "Licensed under Apache License, Version 2.0": https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf

## Apache License, Version 2.0 (text)

                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
