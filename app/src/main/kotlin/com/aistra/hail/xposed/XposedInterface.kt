package com.aistra.hail.xposed

/**
 * Xposed module entry point — stubbed out.
 * Auto-unfreeze-on-launch via Xposed is not active;
 * freeze/unfreeze is handled through Shizuku instead.
 */
class XposedInterface {
    abstract class BaseHook(protected val classLoader: ClassLoader) {
        abstract fun startHook()
    }
}
