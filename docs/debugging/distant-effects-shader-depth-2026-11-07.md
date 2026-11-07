# Shader-only terminal-flight disappearance: X45 investigation

The X45 material-marker change did not resolve the user's disappearance. The
material/TAA fixture demonstrated a separate pack behaviour; it did not identify
the cause of the reported low-altitude cutoff. The latest runtime evidence points
to the depth coordinate contract inside the effects pass, before TAA.

## Inputs inspected

- Wargames Launcher's latest `fml-client-latest.log`, `latest.log`, and
  `lwjgl3ify-java21-child.log`, from the X45 session starting 11:37 on 7 November.
- Its installed `angelica-2.2.14.jar`, including the Java 17 implementations used
  on Java 21, public projection supplier, matrix uniform registration, and actual
  terrain shader transformation.
- The active `BountifulStarsALPHA0.0.3-WDG-water-input-probe.zip`, its options,
  `dh_terrain.glsl`, and subsequent composite/TAA passes.
- Local recording `raw/2026_11_07_11_38_18.mcpr`: player position/look packets,
  DH body relay samples, and native explosion relay sample.
- Current core DH projection construction and the custom effects pass.

No server simulation, resource packs, missile models, or shader-pack files were
modified during this investigation.

## Runtime findings

X45 was installed; the material attachment was present (`particleMaterial=159`).
There was no reported target failure or diagnostic linkage failure.

At 11:39:05 the client received the native explosion at
`(9153.5568,109.3749,-2122.5725)`. Its horizontal wave was captured: the `other`
buffer had **3763 raster samples, 0 depth samples, 0 cloud samples, 0 visible
samples**. All of that sampled wave was rejected by depth, before cloud/TAA.
Old plume fragments still passed, which means a positive aggregate plume count
does not establish visibility of fresh low-altitude smoke.

The earlier 11:39:03 sample had 182 captured body quads but zero raster samples.
It is not evidence that a later composite overwrote that body. The recording
shows the player panning down ahead of the descending missile around this time;
parts of this phase reconstruct above the viewport. By the final descent and
impact, the recorded missile and explosion are near the centre of the view.
The recording's player is stationary at approximately `(9051.28,358.97,-2334.03)`
(feet), with final yaw/pitch approximately `(-26.72,40.78)` degrees.

No native missile was tracked in this flight and the proxy remained selected
through terminal relay samples. A native/LOD ownership switch does not explain
this flight's terminal cutoff.

## The violated depth contract

Two different projection matrices were being treated as interchangeable:

1. Core DH's `RenderUtil.setDhProjectionMatrix` caps its own near plane at **7.5**
   when the high-altitude override is absent. The camera was near y=360, well
   below the override threshold.
2. Angelica registers the pack's `dhProjection` using the public static
   `DHCompat.getProjection()` supplier. That method constructs a separate
   perspective matrix from `getNearPlane()` and `getFarPlane()`.
   `getNearPlane()` calls the DH render API, whose implementation returns
   `RenderUtil.getNearClipPlaneInBlocks()` **without the 7.5 cap**.

The installed pack writes terrain position with `dhProjection`. Inspection of
Angelica's actual `DHTerrainTransformer` shows it renames legacy `gl_ProjectionMatrix`
and model-view builtins, but does not replace the pack's `dhProjection` with the
core matrix. The terrain vertex's model-view/inverse-model-view expressions
cancel; its clip depth uses the independently supplied pack projection.

Our effect draw used `frame.dhMvmProjMatrix` from core DH to compare against the
pack's terrain depth copy **and** to write/test the pack's live depth attachment.
Both comparisons were therefore in the wrong depth coordinates. At an empty sky
pixel the clear depth is 1, so distant effects can pass. When the same flight
moves in front of terrain, the differently mapped terrain value can falsely put
that foreground missile or wave behind the ground. Shaderless rendering uses the
core matrix for both terrain and effects, so that comparison is consistent.

This is a projection mismatch, not a need for an arbitrary depth epsilon.

## Reconstruction and reproducer

The recording's tick 26165600 contains a body position at y=137.8806; the native
blast is at y=109.3749. Their height difference is approximately **28.5 blocks**,
matching the reported missing section. With the recorded camera, the reconstructed
body view depth is approximately 322.7 blocks. A background plane at the observed
impact height intersects that ray at approximately 364.0 blocks.

The following values use a 16:10 aspect for illustration; the failure also holds
at common 4:3, 16:9 and 21:9 aspects. These are calculated reproduction values,
not depth texels sampled from the running game. The plane is a controlled surface
at the recorded impact height, not an extraction of all world terrain.

| Quantity | Value |
| --- | ---: |
| Core near plane | 7.5 |
| Pack near plane from RD=8, automatic shader overdraw, 16:10 aspect | 15.4502 |
| Missile depth with old core projection | 0.977065 |
| Terrain depth with pack projection | 0.958175 |
| Missile depth with matching pack projection | 0.952738 |

Forward-depth comparison rejects `0.977065 > 0.958175`, despite the missile
being in front. The matching value passes `0.952738 < 0.958175`.

The GPU regression executes the existing effect shaders with this disagreement:

- Old matrix: the recorded-height foreground missile disappears against ground.
- Matching matrix: identical placement/colour/cloud state remains visible.
- Shaderless control: matching core terrain/effect coordinates remain visible.
- Sky control: the old mismatch is hidden by clear depth and appears to work.
- Occlusion control: matching matrices still reject geometry behind ground.

Previous fixtures supplied matching or arbitrary identity matrices to both depth
and effect inputs; they never exercised this real integration mismatch.

`shader-descent-recording-fixture.json` retains the terminal relay observations.
The read-only reconstruction can be repeated with:

```sh
python3 scripts/tests/analyse_distant_effects_replay.py '/path/to/2026_11_07_11_38_18.mcpr'
```

Its NDC positions use nominal FOV84 from the saved settings and an explicit
assumed aspect; they are not captured GL matrices. The exact recorded world
positions and quantized player look packets are preserved separately.

## Correction

The optional Angelica bridge now snapshots `DHCompat.getProjection()` each frame.
It copies the unrelocated JOML matrix through a reflective `FloatBuffer` ABI into
DH's row-major fields. This avoids a shaded-JOML method-descriptor mismatch.
The existing DH model view and particle-to-DH origin offset are retained.

With shaders, that actual pack projection supplies both the sampled terrain
coordinate and `gl_FragDepth`. Shaderless rendering still uses its core matrix.
No hardcoded alternative near plane, depth bias, particle position, cloud formula,
source factory, lifetime, or ownership rule is introduced.

Diagnostics now include `projectionNear/core`, making any future mismatch visible
in the automatic flight history. Actual Angelica bytecode checks verify both the
public supplier and its registration as the pack's DH projection source. A real
runtime-JOML reflective-copy check verifies matrix ordering and the method ABI.

The 84 GPU checks include the reproduced shader-only ground rejection. These and
the source/recording analysis establish the mismatch and correction. Final visual
acceptance of this build in Minecraft is still unobserved; no isolated test is
being presented as a full in-game verification.
