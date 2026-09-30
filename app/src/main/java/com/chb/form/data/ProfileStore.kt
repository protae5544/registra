package com.chb.form.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.profileDs by preferencesDataStore("chb_profiles")
private val KEY_EMPLOYER = stringPreferencesKey("employer_profile")
private val KEY_PERSONAL = stringPreferencesKey("personal_profile")

data class EmployerProfile(
    val company: String = "",
    val foreman: String = "",
    val leader: String = "",
    val defaultAreaIdx: Int? = null,
    val defaultCourses: List<Int> = emptyList()
) {
    val isConfigured: Boolean get() = company.isNotBlank() || foreman.isNotBlank() || leader.isNotBlank() || defaultAreaIdx != null
}

data class PersonalProfile(
    val name: String = "",
    val tel: String = "",
    val blood: String = "",
    val position: String = "",
    val emgName: String = "",
    val emgRel: String = "",
    val emgTel: String = "",
    val expIdx: Int? = null,
    val expDuration: String = "",
    val prevIdx: Int? = null
) {
    val isConfigured: Boolean get() = name.isNotBlank() || tel.isNotBlank() || emgName.isNotBlank() || position.isNotBlank()
}

object ProfileStore {

    suspend fun saveEmployer(ctx: Context, p: EmployerProfile) {
        val o = JSONObject().apply {
            put("company", p.company)
            put("foreman", p.foreman)
            put("leader", p.leader)
            put("defaultAreaIdx", p.defaultAreaIdx ?: JSONObject.NULL)
            put("defaultCourses", JSONArray(p.defaultCourses))
        }
        ctx.profileDs.edit { it[KEY_EMPLOYER] = o.toString() }
    }

    suspend fun loadEmployer(ctx: Context): EmployerProfile {
        val raw = ctx.profileDs.data.first()[KEY_EMPLOYER] ?: return EmployerProfile()
        return runCatching {
            val o = JSONObject(raw)
            val courses = mutableListOf<Int>()
            val arr = o.optJSONArray("defaultCourses")
            if (arr != null) {
                for (i in 0 until arr.length()) courses.add(arr.getInt(i))
            }
            EmployerProfile(
                company = o.optString("company", ""),
                foreman = o.optString("foreman", ""),
                leader = o.optString("leader", ""),
                defaultAreaIdx = if (o.has("defaultAreaIdx") && !o.isNull("defaultAreaIdx")) o.getInt("defaultAreaIdx") else null,
                defaultCourses = courses
            )
        }.getOrDefault(EmployerProfile())
    }

    suspend fun savePersonal(ctx: Context, p: PersonalProfile) {
        val o = JSONObject().apply {
            put("name", p.name)
            put("tel", p.tel)
            put("blood", p.blood)
            put("position", p.position)
            put("emgName", p.emgName)
            put("emgRel", p.emgRel)
            put("emgTel", p.emgTel)
            put("expIdx", p.expIdx ?: JSONObject.NULL)
            put("expDuration", p.expDuration)
            put("prevIdx", p.prevIdx ?: JSONObject.NULL)
        }
        ctx.profileDs.edit { it[KEY_PERSONAL] = o.toString() }
    }

    suspend fun loadPersonal(ctx: Context): PersonalProfile {
        val raw = ctx.profileDs.data.first()[KEY_PERSONAL] ?: return PersonalProfile()
        return runCatching {
            val o = JSONObject(raw)
            PersonalProfile(
                name = o.optString("name", ""),
                tel = o.optString("tel", ""),
                blood = o.optString("blood", ""),
                position = o.optString("position", ""),
                emgName = o.optString("emgName", ""),
                emgRel = o.optString("emgRel", ""),
                emgTel = o.optString("emgTel", ""),
                expIdx = if (o.has("expIdx") && !o.isNull("expIdx")) o.getInt("expIdx") else null,
                expDuration = o.optString("expDuration", ""),
                prevIdx = if (o.has("prevIdx") && !o.isNull("prevIdx")) o.getInt("prevIdx") else null
            )
        }.getOrDefault(PersonalProfile())
    }
}
