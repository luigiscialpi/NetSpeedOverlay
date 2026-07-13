// Plugin versions below reflect what was current around this project's
// creation date. Bump them via Android Studio's Upgrade Assistant /
// "Check for updates" before building — this couldn't be verified against
// Maven from the sandbox this project was scaffolded in (network egress
// there only allows a handful of package registries, Google's Maven isn't
// one of them), so treat the exact numbers as a starting point, not gospel.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
