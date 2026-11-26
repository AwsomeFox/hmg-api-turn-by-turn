#!/bin/sh
# Off-car test of the turn-by-turn mod on the patch engine. It changes no stock file (its
# hooks and its library patches are in memory), so no car files are needed: the plan must be
# clean, the apply adds only the road-graph reader's library, and a restore removes it.
# Runs with the HMG-API mod SDK (HMGAPI_SDK, set by the mod workflow).
#   HMGAPI_SDK=<sdk> projection-tbt/test.sh [product] [onebin]
set -e
M=$(cd "$(dirname "$0")" && pwd)
export HMGAPI_PRODUCT=${1:-CVEV.USA.0000.V015.012.260507}
export HMGAPI_ONEBIN=${2:-cvev_us}
PRODUCT=$HMGAPI_PRODUCT ONEBIN=$HMGAPI_ONEBIN
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
R=$T/root
. "${HMGAPI_SDK:?set HMGAPI_SDK to the unpacked HMG-API mod SDK}/test/lib.sh"
# its NEMESIS package refuses the NEMESIS packages that send the phone's turns themselves
d=$(HMGAPI_NEMESIS_CONFLICTS=/dev/null python3 "$HMGAPI_SDK/bin/nemesis-meta.py" depends hmgapi.projection-tbt 'hmgapi@>=1.0' --mod "$M/mod.json")
if [ "$d" = "hmgapi@>=1.0,!projection.tbt,!enhanced.projection" ]; then echo "ok    NEMESIS package refuses projection.tbt and enhanced.projection"
else echo "FAIL  NEMESIS package depends: [$d]"; exit 1; fi

mkdir -p $R/system/app $R/system/lib $R/system/etc/permissions $R/system/onebin/$ONEBIN $R/data/hmgapi/mods $R/data/hmgapi/settings
: > $R/system/onebin/$ONEBIN/featurexml.cfg
printf 'ro.build.product=%s\n' "$PRODUCT" > $R/system/build.prop
printf 'ro.build.product=%s\nro.board.platform=tcc803x\nro.build.version.release=4.4.2\nro.mobis.onebindir=%s\n' "$PRODUCT" "$ONEBIN" > $R/props.txt
install_mod "$M"
BEFORE=$(tree)

eng plan
check "plan has no problems" 0 $rc
[ $rc = 0 ] || echo "$out"
eng apply
check "apply" 0 $rc
[ $rc = 0 ] || echo "$out"
has "road-graph reader added" "/system/lib/libtbtnavigraph.so" "$out"
check "nothing else on /system changes" "" "$(echo "$out" | grep '/system/' | grep -v libtbtnavigraph || true)"
check "no props, no feature files" "" "$(cat $R/settings.txt 2>/dev/null; ls $R/system/etc/permissions)"

eng restore
check "restore exit" 0 $rc
check "/system byte-identical to before" "$BEFORE" "$(tree)"
exit $fail
