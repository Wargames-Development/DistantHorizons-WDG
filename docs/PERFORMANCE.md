# Performance guidance

These are conservative starting points, not guarantees. Total system RAM is not Java allocation; leave memory for the operating system, launcher and GPU driver. First-generation LOD work is materially heavier than reloading already-generated LODs.

## Minimum/test tier

- LOW quality, MINIMAL_IMPACT threads, 64–128 chunk LOD radius, SURFACE generation.
- Start with a lower Java allocation appropriate to the complete modpack, commonly around 4–6 GiB where total RAM permits.
- Disable shader packs.
- Expect slow initial generation and occasional integrated-server delay.

## Recommended tier

- LOW or MEDIUM quality, 128–256 chunks.
- LOW_IMPACT or BALANCED only after observing the machine under load.
- A common starting allocation is 6–8 GiB, adjusted for the whole pack and total system RAM.
- Expect initial generation to use substantial CPU and GPU resources.

## High-end tier

- Larger radius and MEDIUM/HIGH quality after staged testing.
- Higher thread presets are optional and can still saturate CPU/GPU during first generation.
- Larger Java allocation may be appropriate, but excessive heap can increase collection pauses and must not starve the OS.

For laptops and Apple Silicon, begin at the fresh-profile defaults and watch thermals, responsiveness and frame time. Windows gaming desktops should capture GPU driver, CPU/GPU load and frame-time observations. Shader use adds substantial GPU load and must be tested separately.

When load is excessive: lower LOD radius, lower quality, reduce thread preset, pause distant generation, or switch to `PRE_EXISTING_ONLY` for troubleshooting. Severe persistent system unresponsiveness, repeated server stalls or thermal overload under conservative defaults is a release concern; high initial load alone is not automatically a failure.
