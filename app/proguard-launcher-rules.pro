# Nuvio TV Launcher: ProGuard/R8 rules (kept apart from Nuvio TV's proguard-rules.pro).

# ── On-device ADB setup (libadb-android / BouncyCastle) ───────────────────────
# The phone-driven Home button setup creates its ADB identity certificate through
# BouncyCastle, whose algorithms are registered from classes loaded by name.
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
