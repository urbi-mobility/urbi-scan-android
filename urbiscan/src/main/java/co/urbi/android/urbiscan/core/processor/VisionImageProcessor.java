// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package co.urbi.android.urbiscan.core.processor;

import android.graphics.Rect;

import androidx.camera.core.ImageProxy;

import kotlin.Triple;

/** An inferface to process the images with different ML Kit detectors and custom image models. */
public interface VisionImageProcessor {

  /** Processes the ImageProxy without overlay. */
  void process(ImageProxy imageProxy);

  /** Stops the underlying machine learning model and release resources. */
  void stop();

  /** Return the top and bottom limit of area to be scanned*/
  Triple<int[], int[], Rect> getCoordinatesLimit();
}
