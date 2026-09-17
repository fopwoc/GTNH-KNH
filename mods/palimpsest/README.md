# Palimpsest

Palimpsest is an experimental client mod for historical map storage. This first version contains a synthetic storage benchmark. It does not read chunks or render an actual map.

Run `/palimpsest` in game to open the benchmark. **Generate +250** creates a 32×32 tile world and then adds 250 sparse time steps. Further clicks append another 250 time steps. Scrub the slider to reconstruct an earlier view, pan the 8×6 tile viewport, and use **Reopen index** to time a fresh index scan from disk. The readout reports sealed bytes, read time, layers visited, and layers decoded.

Each tile is 16×16 pixels with one byte per color. Every observation is the same layer type: a time stamp, a 256-bit coverage mask, and one color byte for each covered pixel. A first observation covers all pixels. Later observations usually cover only changed pixels. Historical reads walk layers from newest to oldest and skip a layer when newer layers already cover all its pixels.

Layers are appended in immutable, content-addressed `.pseg` segments under `config/palimpsest/benchmark/` in the Minecraft instance. A segment contains explicit little-endian records with CRC32C checksums. On reopening, Palimpsest verifies each segment's SHA-256 name and record checksums, then rebuilds its in-memory tile/time index. Writes seal a temporary file before an atomic rename. The format is experimental and may change without migration.

The benchmark uses reproducible random edits. It measures the storage prototype on your machine; it is not a gameplay performance result.
