# Contributing

Thanks for helping. Bugs in this mod and requests to add a car go in
[issues](https://github.com/AwsomeFox/hmg-api-turn-by-turn/issues/new/choose); code and
docs in pull requests. Problems with HMG-API itself (installing, the app, other mods, Car
Reports, hook maps) go to [HMG-API](https://github.com/Open-HMG/HMG-API/issues/new/choose).

## Before a pull request

- Build and test off the car with the HMG-API mod SDK (README, "Building and testing"):
  the build, `projection-tbt/test.sh`, `tools/*/test.sh` and `check-scripts`, the
  same steps CI runs. Needs a JDK, the Android SDK and the SDK's `requirements.txt`; no
  car files or keys. Maintainers with the private stock trees also get the real-file
  checks in `test.sh` and `tools/recipes/test.sh`.
- Say which cars (model, region, head unit software) a change was tested on, or "off-car only".
- New or changed hook points: a HMG-API pull request first, mapped for every car
  (README, "Hook points live in HMG-API"). Here, `hooks.points` only names points HMG-API
  has; CI checks them against the `"hmgapi"` floor.
- Mod scripts: only commands the head unit has (`$HMGAPI_SDK/bin/check-scripts.sh projection-tbt`).
- Leave `version` in `mod.json` as it is: it's set at release.
- Who reviews what (`.github/CODEOWNERS`): AwsomeFox, while the repository is theirs. Once
  it moves to Open-HMG: VysseCodes reviews CI, releases and signing (`.github/`) and native
  code (`projection-tbt/native/`); everything else needs one of the Open-HMG maintainers.

## Releases (maintainers)

As the Wireless mod's, by HMG-API's [docs/releasing.md](https://github.com/Open-HMG/HMG-API/blob/main/docs/releasing.md):
set `version` in `projection-tbt/mod.json` and write `.github/release-notes/v<version>.md`
in a release PR; once it's merged, push the tag `v<version>`. `.github/workflows/release.yml`
runs CI on the tag, signs the package with `MOD_KEY` (the `release` environment, `v*` tags
only), builds the NEMESIS packages and `SHA256SUMS`, and attaches them to a **draft**
release. Publishing it is a maintainer's call. The run's summary has the
`tools/catalog.py add` command for HMG-API's catalogue (finals), or the
`tools/release-mods.txt` line (pre-releases, once the mod has a catalogue entry).

- **Author key:** VysseCodes' (`7251ce71-…`, `.github/mod-key.pub`), the same as the
  Wireless mod's: the key is per author, it's already on HMG-API's trusted list, and it
  can never change once a release is out. So `MOD_KEY` only goes in a repository
  VysseCodes controls: this one moves to Open-HMG before its first tag.
- **`repo` in `mod.json`** must be this repository's address: the release refuses a tag
  otherwise (HMG-API's catalogue checks the download is a release of the entry's repo).
  Change it, and the link in HMG-API's `maps/authors.json`, when the repository moves.
- **The first release goes out with Wireless 3.0**, and owners update Wireless first: next
  to Wireless 2.x this mod turns Wireless's hooks off (`conflicts`). The order across the
  repositories is in HMG-API's release checklist for the split.

## Rules

- Anything that writes to `/system` keeps a stock checksum check, a backup and an
  uninstall that restores the exact stock bytes.
- The repo holds no manufacturer apps or libraries: only hooks, hash-only recipes,
  delta patch sets, checksums and our own code.
- Never collect or publish per-car secrets (VIN, serials, keys, account tokens,
  `/data` contents), in code, reports, logs or issues.
- Vehicle commands go through the car's own services, never our own CAN frames.
- Commit messages describe the change in plain words.

By contributing you agree your work is licensed under the project's AGPL-3.0.
