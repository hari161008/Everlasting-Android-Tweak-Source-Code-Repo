package com.aistra.hail.xposed

/**
 * Xposed launch hook — stubbed out.
 * Auto-unfreeze-on-launch via Xposed is not active;
 * freeze/unfreeze is handled through Shizuku instead.
 */
class LaunchAppHook(classLoader: ClassLoader) : XposedInterface.BaseHook(classLoader) {
    override fun startHook() {
        // no-op: Xposed not used
    }
}
