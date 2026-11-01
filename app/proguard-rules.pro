# Regels voor de geoptimaliseerde (R8) release-build van IPTIMTV.
# De bibliotheken (Compose, Media3, OkHttp, Coil) leveren hun eigen regels mee.

# Leesbare foutmeldingen in crashrapporten.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp verwijst optioneel naar deze bibliotheken.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
