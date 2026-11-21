package com.example.remote

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PinActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg   = intent.getStringExtra("pkg") ?: ""
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val pin   = prefs.getString("pin", "1234") ?: "1234"

        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)

        val pinInput = EditText(this).apply {
            hint = "PIN-код"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val btnOk = Button(this).apply {
            text = "Войти"
            setOnClickListener {
                if (pinInput.text.toString() == pin) {
                    // 5 минут доступа, потом блокировка снова
                    prefs.edit()
                        .putLong("unlock_until", System.currentTimeMillis() + 5 * 60_000L)
                        .apply()
                    finish()
                } else {
                    Toast.makeText(this@PinActivity, "Неверный PIN", Toast.LENGTH_SHORT).show()
                    pinInput.text.clear()
                }
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 140, 80, 80)
            addView(TextView(this@PinActivity).apply {
                text = "«$appName» заблокировано"
                textSize = 22f
                setPadding(0, 0, 0, 16)
            })
            addView(TextView(this@PinActivity).apply {
                text = "Введи PIN-код"
                textSize = 16f
                setPadding(0, 0, 0, 32)
            })
            addView(pinInput)
            addView(btnOk)
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Назад = рабочий стол, не закрытие
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
