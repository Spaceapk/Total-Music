package com.music.bitchord.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.R

/** Small per-instance setup shown by Android when a widget is first placed. */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId) == null
        ) {
            finish()
            return
        }
        val initial = MediaWidgetPreferences.theme(this, widgetId)
        setContent {
            var selected by remember { mutableStateOf(initial) }
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.widget_config_theme), style = MaterialTheme.typography.bodyMedium)
                        WidgetThemeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected == mode,
                                    onClick = { selected = mode },
                                )
                                Text(
                                    stringResource(when (mode) {
                                        WidgetThemeMode.SYSTEM -> R.string.widget_theme_system
                                        WidgetThemeMode.LIGHT -> R.string.widget_theme_light
                                        WidgetThemeMode.DARK -> R.string.widget_theme_dark
                                    }),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            onClick = {
                                MediaWidgetPreferences.setTheme(this@WidgetConfigActivity, widgetId, selected)
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                )
                                finish()
                            },
                        ) {
                            Text(stringResource(R.string.widget_config_add))
                        }
                    }
                }
            }
        }
    }
}
