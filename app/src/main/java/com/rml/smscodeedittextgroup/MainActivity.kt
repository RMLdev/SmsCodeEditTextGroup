package com.rml.smscodeedittextgroup

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.rml.scetglib.SmsCodeEditTextGroup

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val smsCodeEditTextGroup = findViewById<SmsCodeEditTextGroup>(R.id.scetg)

        smsCodeEditTextGroup.setOnCodeEntered {
            if (it=="2021") {
                smsCodeEditTextGroup.setCorrectInput()
            } else {
                smsCodeEditTextGroup.setIncorrectInput()
            }
        }
    }
}
