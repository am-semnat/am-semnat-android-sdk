# Keep JMRTD and SCUBA classes intact so consumers can comply with the LGPL
# relinking clause. If R8/ProGuard obfuscates or inlines these, end users
# cannot swap in a different JMRTD/SCUBA build.
-keep class org.jmrtd.** { *; }
-keep class net.sf.scuba.** { *; }

# BouncyCastle uses reflection for algorithm lookup.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn org.bouncycastle.**

# JMRTD pulls in javax.security.auth.x500 names via reflection.
-dontwarn javax.naming.**
