package com.chb.form

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chb.form.ui.theme.CHBFormTheme
import com.chb.form.ui.FormWizard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CHBFormTheme {
                FormWizard()
            }
        }
    }
}
