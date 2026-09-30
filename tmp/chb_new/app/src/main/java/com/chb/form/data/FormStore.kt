package com.chb.form.data
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
private val Context.ds by preferencesDataStore("chb_form")
private val KEY = stringPreferencesKey("draft")
object FormStore {
suspend fun save(ctx: Context, d: FormData) { val o = JSONObject().apply { put("checks", JSONArray(d.checks))
put("fields", JSONArray(d.fields))
put("card", d.cardPath ?: JSONObject.NULL)
put("sign", d.signaturePath ?: JSONObject.NULL)
put("withSign", d.withSignature) }
ctx.ds.edit { it[KEY] = o.toString() } }
suspend fun load(ctx: Context): FormData { val raw = ctx.ds.data.first()[KEY] ?: return FormData()
return runCatching { val o = JSONObject(raw)
val c = o.getJSONArray("checks")
val f = o.getJSONArray("fields")
FormData( checks = List(19) { if (it < c.length())
c.getBoolean(it) else false
}, fields = List(14) { if (it < f.length())
f.getString(it) else ""
}, cardPath = o.optString("card").takeIf { it.isNotBlank() && it != "null"
}, signaturePath = o.optString("sign").takeIf { it.isNotBlank() && it != "null"
}, withSignature = o.optBoolean("withSign", true) ) }.getOrDefault(FormData()) }
suspend fun clear(ctx: Context) = ctx.ds.edit { it.remove(KEY) } }
