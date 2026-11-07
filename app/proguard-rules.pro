# SPDX-License-Identifier: GPL-3.0-or-later
# Library keep rules come from OkHttp and kotlinx-serialization themselves (LLD §9).
# Strip debug/info logging from release builds (LLD §6); warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
