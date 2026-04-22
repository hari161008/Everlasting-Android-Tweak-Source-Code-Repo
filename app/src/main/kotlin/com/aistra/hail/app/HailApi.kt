package com.aistra.hail.app

import android.content.Intent

object HailApi {
    const val ACTION_LAUNCH              = "com.coolappstore.everlastingandroidtweak.action.LAUNCH"
    const val ACTION_FREEZE              = "com.coolappstore.everlastingandroidtweak.action.FREEZE"
    const val ACTION_UNFREEZE            = "com.coolappstore.everlastingandroidtweak.action.UNFREEZE"
    const val ACTION_FREEZE_TAG          = "com.coolappstore.everlastingandroidtweak.action.FREEZE_TAG"
    const val ACTION_UNFREEZE_TAG        = "com.coolappstore.everlastingandroidtweak.action.UNFREEZE_TAG"
    const val ACTION_FREEZE_ALL          = "com.coolappstore.everlastingandroidtweak.action.FREEZE_ALL"
    const val ACTION_UNFREEZE_ALL        = "com.coolappstore.everlastingandroidtweak.action.UNFREEZE_ALL"
    const val ACTION_FREEZE_NON_WHITELISTED = "com.coolappstore.everlastingandroidtweak.action.FREEZE_NON_WHITELISTED"
    const val ACTION_FREEZE_AUTO         = "com.coolappstore.everlastingandroidtweak.action.FREEZE_AUTO"
    const val ACTION_LOCK                = "com.coolappstore.everlastingandroidtweak.action.LOCK"
    const val ACTION_LOCK_FREEZE         = "com.coolappstore.everlastingandroidtweak.action.LOCK_FREEZE"

    fun getIntentForPackage(action: String, packageName: String) =
        Intent(action).putExtra(HailData.KEY_PACKAGE, packageName)

    fun Intent.addTag(tag: String) = putExtra(HailData.KEY_TAG, tag)

    fun getIntentForTag(action: String, tag: String) = Intent(action).addTag(tag)
}
