# Near vehicle depth audit

DH X77 captured each model at its physical pose using a local depth projection.
The primary-view actor merge then wrote clamped DH terrain depth. All surfaces
in front of that projection's near plane collapsed to its endpoint. The final
compositor inverted that endpoint and compared the reconstructed position with
native terrain, which could reject a visible surface. Shader and shaderless
primary views shared this intermediate representation. The threshold depends
on the projection and view angle rather than the vehicle's distance limit.

X78 stores reversible, floating-point reverse eye depth on the existing private
actor texture. ModelCapture writes this depth and ModelScene reads it on the
native pixel ray. Terrain comparisons and the final native or pack depth writes
use their respective projections. DistantSmokeRenderer's fragment shader reads
the same actor encoding, keeping the existing opaque actor occlusion rule.
The private scope and thermal paths retain their target formats. The replacement
depth texture still uses four bytes per pixel and adds no framebuffer or pass.

The existing actor GPU fixture now covers near actor ordering and occlusion on
both sides of the surface. The smoke fixture uses the new actor encoding.
Windows validation ran a focused LWJGL OpenGL fixture against the production
actor shaders (eight checks), and the offline Forge 1.7.10 Gradle build passed
216,180 core checks and remapped X78. The macOS-specific C fixtures were not
executed. The client-only change leaves gameplay and network ownership in
existing code. Live validation should include approach from ground level and
directly overhead, partial terrain cover, shader PiP, and distant actor/smoke
overlap. The original saved-world scene still needs that retest.
