plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("kotlin-android-extensions")
    id("dev.icerock.mobile.multiplatform")
    id("kotlin-kapt")
}

android {
    compileSdkVersion(Versions.Android.compileSdk)

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
    }

    dataBinding {
        isEnabled = true
    }
}

dependencies {
    mppLibrary(Deps.Libs.MultiPlatform.kotlinStdLib)
    mppLibrary(Deps.Libs.MultiPlatform.coroutines)
    mppLibrary(Deps.Libs.MultiPlatform.mokoMvvm)
    mppLibrary(Deps.Libs.MultiPlatform.mokoResources)
    mppLibrary(Deps.Libs.MultiPlatform.mokoPermissions)
    mppLibrary(Deps.Libs.MultiPlatform.napier)

    androidLibrary(Deps.Libs.Android.constraintLayout)
    androidLibrary(Deps.Libs.Android.appCompat)
    androidLibrary(Deps.Libs.Android.material)
    androidLibrary(Deps.Libs.Android.photoView)
    androidLibrary(Deps.Libs.Android.lifecycle)

    mppModule(Modules.MultiPlatform.domain)
}
