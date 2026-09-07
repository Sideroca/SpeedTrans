package com.speedtrans.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.speedtrans.app.store.SettingsStore

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val store = SettingsStore(this)
        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etKey = findViewById<EditText>(R.id.etKey)
        val etModel = findViewById<EditText>(R.id.etModel)

        etUrl.setText(store.baseUrl)
        etKey.setText(store.apiKey)
        etModel.setText(store.model)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            store.baseUrl = etUrl.text.toString()
            store.apiKey = etKey.text.toString()
            store.model = etModel.text.toString()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
