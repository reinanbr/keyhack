plugins {
    id("keyhack.android.library")
}

// The keyboard's only network code, for GIF search (ADR 0004). Only the
// `online` build of the app includes this module; the `offline` build has
// neither the HTTP client nor the INTERNET permission.
dependencies {
    implementation(project(":media"))
}
