# Notices

## Where this repository comes from

It was made from [Open-HMG/hmgapi-wireless-cp-aa](https://github.com/Open-HMG/hmgapi-wireless-cp-aa) (then named hmgapi-wireless-carplay-aa)
(AGPL-3.0) with its full history, keeping the turn-by-turn half of that mod. Every
earlier commit and its author are in `git log`. That mod itself moved out of
[HMG-API](https://github.com/Open-HMG/HMG-API)'s `mods/wireless-carplay-aa` (AGPL-3.0),
by VysseCodes.

## NEMESIS `projection.tbt` (AGPL-3.0)

[NEMESIS](https://github.com/xwtk/hmg_nemesis) by xwtk is licensed under the GNU Affero
General Public License v3.0. Its package *Turn-by-Turn Guidance for Phone Projection*
(`projection.tbt`) ships a build of the car's Android Auto library that hands the phone's
navigation data to the head unit.

`projection-tbt/recipes/aa-tbt-48ee793a.recipe` and `aa-tbt-a0bea1c4.recipe` were
generated (HMG-API's `mkmemrecipe.py`) from the difference between the stock library and
that package's build, so they carry its changes as bytes applied in memory;
`aa-tbt-dbfe7bd2.recipe` reuses the same parser on another library build (by Weab-chan).
One change is this project's own: the parser's string cut was fixed
(`projection-tbt/tools/aa-text`). No manufacturer library is distributed: a recipe holds
only the changed bytes and checksums of the regions around them.

## ch0iiih/HMG_Phone_Projection_TBT (AGPL-3.0)

[HMG_Phone_Projection_TBT](https://github.com/ch0iiih/HMG_Phone_Projection_TBT) by ch0iiih.
`projection-tbt/native/navigraph` and `src/hmgapi/mods/tbt/NaviGraph.java` are adapted
from its native helper and its map look-ahead and junction matching (named in those
files and in `docs/intersections.md`). Several turn fixes follow fixes it made first
(named in the commits).

## Ultimate Wireless Projection / Evergreen CarPlay AR (GPL-3.0)

Findings from [KSubedi's builds](https://github.com/KSubedi/ultimate-wireless-projection)
on which maneuvers Apple Maps sends, and when, are used in `CpTurns` and described in
`docs/ar-hud.md`. No code was taken from it.

## Manufacturer and third-party software

Nothing from Hyundai, Kia, Genesis, Apple or Google is included. The compile-only stubs in
`projection-tbt/stubs` name classes of the head unit's iAP2 library so the mod can be
built; they contain no implementation and are not shipped.
