---
navigation:
  parent: index.md
  title: Atomic Reconstructor
  icon: aa_reconstructor_packaged_core
  position: 10
---

# Atomic Reconstructor

**Packaged Core:** <ItemLink id="ae2ltpp:aa_reconstructor_packaged_core" />
**Mod:** Actually Additions

Automates the Atomic Reconstructor's laser conversions (one input item → one output item).
Place the provider against the Atomic Reconstructor, encode the conversion as a pattern, and the
result is returned instantly — no item entity is spawned.

## Requirements

* The Reconstructor still needs **power (RF/CF)**. Each conversion draws energy just like a manual one;
  if it runs dry, the craft simply waits until it has charge again.
* Only the **default conversion lens** works. Specialised lenses (mining, death, color, …) are ignored,
  because their side effects can't be automated safely.
