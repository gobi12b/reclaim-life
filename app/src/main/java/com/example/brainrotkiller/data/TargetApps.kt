package com.example.brainrotkiller.data

/** Packages whose vertical-feed scrolling we count as "watching a reel". */
object TargetApps {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    val PACKAGES: Set<String> = setOf(INSTAGRAM, YOUTUBE)

    fun isTarget(packageName: String?): Boolean = packageName in PACKAGES
}
