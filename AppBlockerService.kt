package com.example.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return  // наше приложение — пропускаем

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        val unlockUntil = prefs.getLong("unlock_until", 0L)
        if (System.currentTimeMillis() < unlockUntil) return

        val blocklist = prefs.getStringSet("blocklist", emptySet()) ?: emptySet()
        if (pkg in blocklist) {
            startActivity(Intent(this, PinActivity::class.java).apply {
                putExtra("pkg", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    override fun onInterrupt() {}
}
