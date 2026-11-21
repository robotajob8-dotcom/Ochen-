package com.example.remote

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запускаем сервис сразу — токен уже вшит в APK
        startForegroundService(Intent(this, ControlService::class.java))

        val btn1 = Button(this).apply {
            text = "1. Права администратора (нужны для /screenoff)"
            setOnClickListener {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        ComponentName(context, AdminReceiver::class.java))
                })
            }
        }
        val btn2 = Button(this).apply {
            text = "2. Разрешить геолокацию, камеру, микрофон, уведомления"
            setOnClickListener {
                requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                ), 1)
            }
        }
        val btn3 = Button(this).apply {
            text = "3. Включить блокировку приложений (Специальные возможности)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val status = TextView(this).apply {
            text = "\n✅ Сервис запущен. Напиши /help своему боту.\n\n" + ControlService.HELP
        }

        setContentView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 60, 40, 40)
                listOf(btn1, btn2, btn3, status).forEach { addView(it) }
            })
        })
    }
}
