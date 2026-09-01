# Biomechanics metric registry

| Metric | Minimum level | Unit | Primary limitation | Action status |
|---|---:|---|---|---|
| Phase duration | L0 | ms | Boundary confidence/frame rate | Reviewable |
| Bow-arm elbow angle | L1 | degrees | In-plane only; landmark visibility | Reviewable |
| Trunk lean | L1 | degrees | Camera yaw/out-of-plane motion | Reviewable |
| Anchor-position dispersion | L1 | scaled distance | Scale and landmark confidence | Reviewable |
| Expansion velocity | L1 | scaled distance/s | Frame rate and plane assumption | Research |
| Release-hand path | L1 | scaled trajectory | Occlusion after release | Research |
| Shoulder-girdle proxy | L1 | proxy | Not scapular kinematics | Research |
| 3D joint centres | L2 | mm | Multi-view calibration/sync | Not available |
| Draw-force-line geometry | L3 | degrees/mm | Requires bow sensing/force model | Not available |
| Joint moments | L4 | Nm | Lab kinetics and force assumptions | Not available |

Every emitted metric must store registry version, capture level, calibration state, confidence,
failure reason, units, and derivation links. A metric hidden by its minimum-level gate cannot be
reconstructed from a coarser aggregate.
