#!/bin/sh
# Off-car test of the mod's native recipes in memory (hmgapi.engine.MemRecipe): the Android
# Auto recipes on the real stock libraries against the file-patched builds, and the car
# navigation's side-streets recipe against its generator. Needs the private stock trees
# (~/hmg-private); skipped without them. Runs with the HMG-API mod SDK (HMGAPI_SDK).
set -e
R=$(cd "$(dirname "$0")/../../recipes" && pwd)
SDK=$(cd "${HMGAPI_SDK:?set HMGAPI_SDK to the unpacked HMG-API mod SDK}" && pwd)
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
H=$HOME/hmg-private
# The file-patched AA builds keep the parser's old string cut; the recipes have it fixed
# (segment +0x17a, tools/aa-text). Compare against a copy of each build with the same fix.
fixcut() {
    python3 - "$1" "$2" <<'PY'
import sys
old = bytes.fromhex('6029024628bf602108460139535c03f0c003802b00d070470029f5d108467047')
new = bytes.fromhex('602901d8084670476021435c03f0c003802b01d10139f8d10846704700bf00bf')
b = open(sys.argv[1], 'rb').read()
assert b.count(old) == 1, sys.argv[1] + ': the old string cut not found exactly once'
open(sys.argv[2], 'wb').write(b.replace(old, new))
PY
}
set --
if [ -f $H/stock/ev6/lib/libautoreceiver_infobank.so ]; then
    fixcut $H/builds/2_tbt/files/libautoreceiver_infobank.so $CP/aa-48ee793a.so
    set -- "$@" $H/stock/ev6/lib/libautoreceiver_infobank.so $CP/aa-48ee793a.so $R/aa-tbt-48ee793a.recipe
fi
if [ -f $H/stock/neev-can-260206/lib/libautoreceiver_infobank.so ]; then
    fixcut $H/builds-neev_ca/2_tbt/files_neev_ca/libautoreceiver_infobank.so $CP/aa-a0bea1c4.so
    set -- "$@" $H/stock/neev-can-260206/lib/libautoreceiver_infobank.so $CP/aa-a0bea1c4.so $R/aa-tbt-a0bea1c4.recipe
fi
# the car navigation's side streets: the generator, run again on the engine, gives the
# committed recipe, and the recipe in memory gives the generator's patched engine
N=$(cd "$(dirname "$0")/../navi" && pwd)
for x in neev23-usa-260507:caa8d4c2; do
    lib=$H/car/${x%%:*}/navi/libExSLNavi.so; rc=$R/navi-side-streets-${x#*:}.recipe
    [ -f "$lib" ] || continue
    python3 -I $N/side-streets-recipe.py "$lib" --out "$CP/navi.recipe" --patched "$CP/navi-${x#*:}.so" 2>/dev/null
    if cmp -s "$CP/navi.recipe" "$rc"; then echo "ok    navi-side-streets-${x#*:}: generated again, the same recipe"
    else echo "FAIL  navi-side-streets-${x#*:}: the generator gives another recipe"; exit 1; fi
    set -- "$@" "$lib" "$CP/navi-${x#*:}.so" $rc
done
[ $# -gt 0 ] || { echo "no stock libraries in $H (skipped)"; exit 0; }
exec java -cp "$SDK/lib/hmgapi-engine.jar:$SDK/test/hmgapi-engine-test.jar" hmgapi.engine.MemRecipeTest "$@"
