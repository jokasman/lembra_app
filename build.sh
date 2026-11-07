#!/bin/bash
# Lembra — build sem Gradle: aapt2 -> javac -> d8/dx -> zipalign -> apksigner
#
# Usa o Android SDK se ANDROID_HOME estiver definido (ex.: GitHub Actions);
# caso contrário usa ferramentas locais em $LEMBRA_TOOLS (ambiente onde a app foi criada).
#
# Assinatura: KEYSTORE (ficheiro, por defeito release.jks), KS_PASS e KS_ALIAS (por defeito "lembra").
set -e
cd "$(dirname "$0")"
KEYSTORE=${KEYSTORE:-release.jks}
KS_ALIAS=${KS_ALIAS:-lembra}
OUT=build/out
rm -rf $OUT && mkdir -p $OUT/cls $OUT/gen $OUT/dex

if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
  BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
  AJAR=$(ls "$ANDROID_HOME"/platforms/android-*/android.jar | sort -V | tail -1)
  AAPT2="$BT/aapt2"; D8="$BT/d8"; APKSIGNER="$BT/apksigner"
  echo "SDK: $BT · $AJAR"
else
  T=${LEMBRA_TOOLS:-/home/claude/tools}
  AAPT2=$T/aaptjs3-2.0.2/package/bin/x64/linux/aapt2
  AJAR=$T/android-35.jar
  DX="java -cp $T/d2j/dex-tools-v2.4/lib/dx-30.0.2.jar com.android.dx.command.Main"
  APKSIGNER="java -jar $T/apktool/package/lib/apksigner.jar"
fi

$AAPT2 compile --dir res -o $OUT/res.zip
$AAPT2 link -I "$AJAR" --manifest AndroidManifest.xml -o $OUT/base.apk --java $OUT/gen $OUT/res.zip --auto-add-overlay
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -cp "$AJAR" -d $OUT/cls $(find src $OUT/gen -name '*.java')
if [ -n "$D8" ]; then
  $D8 --release --min-api 26 --lib "$AJAR" --output $OUT/dex $(find $OUT/cls -name '*.class')
else
  $DX --dex --min-sdk-version=26 --output=$OUT/dex/classes.dex $OUT/cls
fi
python3 tools/zipalign.py $OUT/base.apk $OUT/aligned.apk classes.dex=$OUT/dex/classes.dex

if [ ! -f "$KEYSTORE" ]; then
  echo "Aviso: $KEYSTORE não existe — a criar uma chave nova (as atualizações têm de usar sempre a mesma chave)."
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "${KS_PASS:?Defina KS_PASS}" -keypass "$KS_PASS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Lembra, C=PT"
fi
$APKSIGNER sign --ks "$KEYSTORE" --ks-pass "pass:${KS_PASS:?Defina KS_PASS}" --ks-key-alias "$KS_ALIAS" --out build/Lembra.apk $OUT/aligned.apk
$APKSIGNER verify build/Lembra.apk && ls -la build/Lembra.apk
