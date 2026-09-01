# Calibrated 2D capture protocol

## Valid measurement level

This protocol produces **L1 calibrated 2D** evidence only. It supports in-plane angles, timing,
and repeatability with explicit confidence. It does not support scapular rotation, joint moments,
true 3D alignment, injury diagnosis, or kinetics.

## Capture

1. Fix the phone on a stable support; no hand-held capture.
2. Record camera height, distance, lens, orientation, resolution, and frame rate.
3. Place a known scale in the athlete plane and keep it visible during calibration.
4. Align the optical axis perpendicular to the intended movement plane; record estimated yaw.
5. Capture the athlete, bow, drawing elbow, hands, and follow-through without occlusion.
6. Record handedness, discipline, draw length, and an immutable equipment snapshot.
7. Reject or downgrade a shot when pose confidence, lighting, occlusion, or out-of-plane motion
   exceeds the metric's registered limit.

## Shot phases

`setup -> raise -> draw -> anchor -> transfer/expand -> aim/hold -> release -> follow-through -> recovery`

Each boundary stores timestamp, confidence, detector version, and optional manual correction.

## Validation gates

- Synthetic geometry with known angles and trajectories.
- Same-clip determinism and repeatability across rotations/devices.
- Sensitivity to distance, height, yaw, lighting, clothing, and occlusion.
- Coach/biomechanist boundary agreement.
- Criterion comparison before any metric is promoted beyond research.

Report bias, MAE, limits of agreement, reliability, failure rate, and confidence calibration.
