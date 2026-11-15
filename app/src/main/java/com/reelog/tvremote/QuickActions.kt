package com.reelog.tvremote

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One-shot TV actions used outside the app: widget buttons and the Quick Settings tile. */
object QuickActions {
    suspend fun run(context: Context, action: String, toast: (String) -> Unit) {
        val store = TvStore(context)
        if (store.selectedTv == null) return toast("Abre TvRemote y elige un televisor")
        val remote = SamsungRemote(context, onStatus = {})
        val session = TvSession(context, store, remote, status = toast)
        try {
            if (action == "power") {
                session.power()
            } else if (!session.sendKey(action)) {
                toast("No se pudo conectar con el televisor")
            }
            delay(400) // let the last frame leave before closing the socket
        } finally {
            remote.close()
        }
    }
}

/** Receives widget/tile actions and runs them off the main thread. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val toast: (String) -> Unit = { msg -> main.post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() } }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(45_000) { QuickActions.run(app, action, toast) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "tv_action"

        fun intent(context: Context, action: String): Intent =
            Intent(context, ActionReceiver::class.java)
                .setAction("${context.packageName}.TV_$action")
                .putExtra(EXTRA_ACTION, action)
    }
}

/** Home-screen widget: power, volume down, mute, volume up and open the app. */
class RemoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_remote).apply {
            setOnClickPendingIntent(R.id.w_power, broadcast(context, "power", 1))
            setOnClickPendingIntent(R.id.w_vol_down, broadcast(context, "KEY_VOLDOWN", 2))
            setOnClickPendingIntent(R.id.w_mute, broadcast(context, "KEY_MUTE", 3))
            setOnClickPendingIntent(R.id.w_vol_up, broadcast(context, "KEY_VOLUP", 4))
            setOnClickPendingIntent(
                R.id.w_open,
                PendingIntent.getActivity(
                    context, 5, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, ActionReceiver.intent(context, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** Quick Settings tile that toggles the TV power. */
class PowerTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = "TV"
            icon = Icon.createWithResource(this@PowerTileService, R.drawable.ic_w_power)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        sendBroadcast(ActionReceiver.intent(this, "power"))
    }
}
