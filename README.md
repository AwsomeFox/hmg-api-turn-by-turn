# Phone Turn-by-Turn for HMG-API (CarPlay + Android Auto)

A mod for [HMG-API](https://github.com/Open-HMG/HMG-API), the mod manager for Hyundai,
Kia and Genesis Gen5W head units. It puts your phone's directions on the instrument
cluster and the HUD: Google Maps, Waze and others over Android Auto, Apple Maps and
others over CarPlay, **wired or wireless**.

It is the turn-by-turn half of the
[Wireless CarPlay + Android Auto](https://github.com/Open-HMG/hmgapi-wireless-carplay-aa)
mod, moved here with its history so that:

- cars with wired phones only (or without the head unit's Wi-Fi hardware) can have it;
- a car whose HMG-API hook map lacks one turn-by-turn point no longer loses the wireless
  connection with it, and the other way round.

> **Here for now; moving to [Open-HMG](https://github.com/Open-HMG)** once its maintainers have
> looked at it (links to this repository keep working after the move).
>
> **Status: set up, not released.** Built and tested off-car only (HMG-API 1.1.0-rc.10
> SDK). Nothing in this repository has been on a car as its own mod yet. Until it is
> released, turn-by-turn ships inside the Wireless mod 2.x.

## What it does

| | Android Auto | CarPlay |
|---|---|---|
| Next turn, distance and road name on the cluster and HUD | ✅ on the Android Auto library builds below | ✅ |
| The HUD's turn list, lanes, destination and remaining distance | ✅ | ✅ where the phone sends them |
| Turns in the car's own format near a maneuver (AR HUD arrow, distance bar) | setting, off by default | setting, off by default |
| The intersection's side streets around the arrow | test settings (the lanes, the car's map) | setting (the iPhone's junction) |
| The car's own navigation stays off the cluster while the phone guides | ✅ | ✅ |

Settings are in the HMG-API app (pick the mod in its list). The mod itself is the on/off
switch: there is no separate "turn-by-turn" setting any more.

How it works, in short:

- **Android Auto:** the car's Android Auto library is patched in memory (the file stays
  stock) so Maps' turn data reaches the mod, which sends it to the cluster through
  HMG-API (`AaTurns`). It only runs on library builds it has a verified patch for; on any
  other, Android Auto's turns stay off and the car's navigation keeps the cluster.
- **CarPlay:** the mod asks the iPhone for route guidance over iAP2 and sends it to the
  cluster (`CpTurns`). No file is patched.
- **Formats and findings:** [docs/cluster-turns.md](docs/cluster-turns.md),
  [docs/ar-hud.md](docs/ar-hud.md), [docs/intersections.md](docs/intersections.md).

## Cars

The list is `system` in [`projection-tbt/mod.json`](projection-tbt/mod.json): the same
models, regions and head unit software as the Wireless mod 2.2, without needing the
Wi-Fi hardware. It needs a hook map for the car in HMG-API
([Supported cars](https://github.com/Open-HMG/HMG-API/wiki/Supported-cars)).

Android Auto's turns also need one of these library builds (`libautoreceiver_infobank.so`):

| md5 starts | Cars it was built from |
|---|---|
| `48ee793a` | Ioniq 5 and EV6 US 260507, EV6 Canada 260206 |
| `a0bea1c4` | Ioniq 5 Canada 260206 |
| `dbfe7bd2` | Ioniq 6 Canada 260206 and US 260903 |

## Updating from the Wireless mod 2.x

Until 3.0, turn-by-turn was part of
[Wireless CarPlay + Android Auto](https://github.com/Open-HMG/hmgapi-wireless-cp-aa). If
you have Wireless 2.x and want to keep your directions on the cluster and HUD:

1. **Update Wireless to 3.0 first.** It has no turn-by-turn of its own.
2. **Then install this mod.** Installed next to Wireless 2.x, it turns *Wireless's* hooks
   off (both have the same hooks, and HMG-API loads one), so wireless CarPlay and Android
   Auto stop until Wireless is 3.0.
3. Set your turn-by-turn settings again in the HMG-API app (pick **Phone Turn-by-Turn**):
   they were Wireless's and don't come across.

Going back: remove this mod first, then install Wireless 2.2, which has turn-by-turn built in.

## With the Wireless mod

Neither mod requires the other.

- **Wired only:** install this mod alone.
- **Wireless:** install both. Use Wireless **3.0 or newer**: older versions still carry
  these hooks, so this mod refuses to load next to them (`conflicts`).
- In the CarPlay app the two cooperate without a dependency: the Wireless mod keeps asking
  the iPhone for route guidance after its Bluetooth-to-Wi-Fi handover, and this mod ignores
  that handover's disconnects. The whole agreement is in
  [docs/wireless-contract.md](docs/wireless-contract.md).
- **NEMESIS packages:** it refuses to install next to `projection.tbt` and
  `enhanced.projection`, which send the phone's turns themselves.

## Building and testing

With the HMG-API mod SDK (`hmgapi-sdk.json` pins it; `./build.sh sdk` in HMG-API builds
one), a JDK and the Android SDK:

```sh
$HMGAPI_SDK/bin/build-mod.sh projection-tbt out
HMGAPI_SDK=$HMGAPI_SDK sh projection-tbt/test.sh          # the engine: plan, apply, restore
for t in aaturns cpturns aa-support aa-text navi navigraph ceev-tbt recipes; do
    HMGAPI_SDK=$HMGAPI_SDK sh projection-tbt/tools/$t/test.sh
done
```

No car files or keys are needed; the recipe tests on real libraries skip without the
private stock trees. Hook points live in HMG-API: `hooks.points` here only names points
HMG-API already has, and a new one is a HMG-API pull request first.

Releases: a tag `v<version>` drafts a signed release
([CONTRIBUTING](CONTRIBUTING.md#releases-maintainers)).

### Hook points this mod is waiting for

A mod can only declare points HMG-API already maps on every car it lists: one missing
point turns all of a mod's hooks off on that car. So these stay out of `mod.json` until
HMG-API has them:

| For | Points | Where it stands |
|---|---|---|
| The distance bar under Android Auto and CarPlay, in the car navigation's own frame so its speed-limit sign stays ([docs/ar-hud.md](docs/ar-hud.md)) | `framework.navigation_cluster.update_tbt_trap_info`, `…update_tbt_trap_info_new`, `framework.navi_tbt_trap_info`, `framework.navi_tbt_trap_info.bar_graph_level` | not proposed to HMG-API yet |

### Still to come over

Two open pull requests on the Wireless repository are not merged here yet, because they
conflict with the ones that are: route details from the full navigation message
([#112](https://github.com/Open-HMG/hmgapi-wireless-carplay-aa/pull/112), with
[#111](https://github.com/Open-HMG/hmgapi-wireless-carplay-aa/pull/111)).

## Credits

This mod is many people's work. Thank you:

- **[VysseCodes](https://github.com/VysseCodes)**: [HMG-API](https://github.com/Open-HMG/HMG-API)
  and the Wireless CarPlay + Android Auto mod this came out of, including the first
  Android Auto and CarPlay turn-by-turn in it, and before that
  [HMG Leafia Plus](https://github.com/VysseCodes/HMG-Leafia-Plus).
- **[xwtk](https://github.com/xwtk)**: [NEMESIS](https://github.com/xwtk/hmg_nemesis) and
  its package *Turn-by-Turn Guidance for Phone Projection* (`projection.tbt`). The Android
  Auto library patch in `projection-tbt/recipes/aa-tbt-*.recipe` reproduces, in memory, the
  changes of that package's library build (see [NOTICE](NOTICE.md)).
- **[Isaac Wisdom](https://github.com/isaacwisdom)**: the CarPlay maneuver fix that made
  CarPlay's turns reach the cluster at all, later CarPlay turn fixes, and reviews.
- **[Weab-chan](https://github.com/Weab-chan)**: the Ioniq 6 Android Auto library patch
  (`aa-tbt-dbfe7bd2`) and its checks (`tools/ceev-tbt`).
- **[AwsomeFox](https://github.com/AwsomeFox)**: the car's own turn format and the AR HUD
  under Android Auto and CarPlay, side streets, the road-graph reader, and the split into
  this repository.
- **[ch0iiih](https://github.com/ch0iiih)**:
  [HMG_Phone_Projection_TBT](https://github.com/ch0iiih/HMG_Phone_Projection_TBT) (AGPL-3.0).
  Several fixes here follow its findings, and the road-graph reader is adapted from its
  native helper (named in the files that use it).
- **[KSubedi](https://github.com/KSubedi)** and the testers of
  [Ultimate Wireless Projection](https://github.com/KSubedi/ultimate-wireless-projection)
  (formerly Evergreen's CarPlay AR builds): road-test findings on Apple Maps' highway exits,
  stale turns and late details, used in the CarPlay turn handling (no code taken).
- **[zpoon](https://github.com/zpoon)**: [Evergreen](https://github.com/zpoon/gen5w-Evergreen),
  and reviews and fixes across Open-HMG.
- **[hwjang1](https://github.com/hwjang1)**: checked the car's own turn format with CarPlay
  on a Korean EV6 with the AR HUD.
- **Bryan Lieberman, Narayan, lemoyneiv, SafariForest, mantene, Joshua Auger, Ian Fisher,
  DrTaru, Conor McBride** and everyone else in this repository's history
  (`git shortlog -sn`), and the owners who drove test builds and sent logs.
- References for the protocols: [aa-proxy-rs](https://github.com/aa-proxy/aa-proxy-rs) and
  [open-android-auto](https://github.com/mrmees/open-android-auto).

## Licence

[AGPL-3.0](LICENSE), as HMG-API and the Wireless mod. Not affiliated with or endorsed by
Hyundai, Kia, Genesis, Apple or Google. Modifying a head unit is at your own risk.
