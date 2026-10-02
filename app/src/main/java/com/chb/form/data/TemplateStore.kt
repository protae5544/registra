package com.chb.form.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.templateDs by preferencesDataStore("chb_templates")

/**
 * ระบบจัดเก็บข้อมูลแยกตามเทมเพลต
 * รองรับการบันทึกหลายรายการ + นำเข้า/ส่งออก JSON
 */
object TemplateStore {

    private fun draftKey(type: TemplateType) = stringPreferencesKey("draft_${type.id}")
    private fun recordsKey(type: TemplateType) = stringPreferencesKey("records_${type.id}")

    // ---------- Draft (ร่างปัจจุบัน) ----------
    suspend fun saveTrainingDraft(ctx: Context, d: FormData) {
        val o = JSONObject().apply {
            put("checks", JSONArray(d.checks))
            put("fields", JSONArray(d.fields))
            put("card", d.cardPath ?: JSONObject.NULL)
            put("sign", d.signaturePath ?: JSONObject.NULL)
            put("withSign", d.withSignature)
        }
        ctx.templateDs.edit { it[draftKey(TemplateType.TRAINING)] = o.toString() }
    }

    suspend fun loadTrainingDraft(ctx: Context): FormData {
        val raw = ctx.templateDs.data.first()[draftKey(TemplateType.TRAINING)] ?: return FormData()
        return runCatching {
            val o = JSONObject(raw)
            val c = o.getJSONArray("checks")
            val f = o.getJSONArray("fields")
            FormData(
                checks = List(19) { if (it < c.length()) c.getBoolean(it) else false },
                fields = List(14) { if (it < f.length()) f.getString(it) else "" },
                cardPath = o.optString("card").takeIf { it.isNotBlank() && it != "null" },
                signaturePath = o.optString("sign").takeIf { it.isNotBlank() && it != "null" },
                withSignature = o.optBoolean("withSign", true)
            )
        }.getOrDefault(FormData())
    }

    suspend fun saveDormDraft(ctx: Context, d: DormFormData) {
        val o = dormToJson(d)
        ctx.templateDs.edit { it[draftKey(TemplateType.DORMITORY)] = o.toString() }
    }

    suspend fun loadDormDraft(ctx: Context): DormFormData {
        val raw = ctx.templateDs.data.first()[draftKey(TemplateType.DORMITORY)] ?: return DormFormData()
        return runCatching { jsonToDorm(JSONObject(raw)) }.getOrDefault(DormFormData())
    }

    suspend fun clearDraft(ctx: Context, type: TemplateType) {
        ctx.templateDs.edit { it.remove(draftKey(type)) }
    }

    // ---------- Records (หลายรายการ) ----------
    suspend fun saveRecord(ctx: Context, type: TemplateType, id: String, label: String, json: JSONObject) {
        val current = loadRecordsRaw(ctx, type)
        val arr = current.optJSONArray("items") ?: JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optString("id") == id) {
                item.put("label", label)
                item.put("data", json)
                item.put("updatedAt", System.currentTimeMillis())
                found = true
                break
            }
        }
        if (!found) {
            arr.put(JSONObject().apply {
                put("id", id)
                put("label", label)
                put("data", json)
                put("createdAt", System.currentTimeMillis())
                put("updatedAt", System.currentTimeMillis())
            })
        }
        current.put("items", arr)
        ctx.templateDs.edit { it[recordsKey(type)] = current.toString() }
    }

    suspend fun loadRecords(ctx: Context, type: TemplateType): List<RecordMeta> {
        val raw = loadRecordsRaw(ctx, type)
        val arr = raw.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            RecordMeta(
                id = item.optString("id"),
                label = item.optString("label"),
                createdAt = item.optLong("createdAt"),
                updatedAt = item.optLong("updatedAt")
            )
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun loadRecordData(ctx: Context, type: TemplateType, id: String): JSONObject? {
        val raw = loadRecordsRaw(ctx, type)
        val arr = raw.optJSONArray("items") ?: return null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("id") == id) return item.optJSONObject("data")
        }
        return null
    }

    suspend fun deleteRecord(ctx: Context, type: TemplateType, id: String) {
        val current = loadRecordsRaw(ctx, type)
        val arr = current.optJSONArray("items") ?: return
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("id") != id) newArr.put(item)
        }
        current.put("items", newArr)
        ctx.templateDs.edit { it[recordsKey(type)] = current.toString() }
    }

    private suspend fun loadRecordsRaw(ctx: Context, type: TemplateType): JSONObject {
        val raw = ctx.templateDs.data.first()[recordsKey(type)] ?: return JSONObject().put("items", JSONArray())
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject().put("items", JSONArray()))
    }

    // ---------- JSON Helpers ----------
    fun dormToJson(d: DormFormData): JSONObject = JSONObject().apply {
        put("applicants", JSONArray().apply {
            d.applicants.forEach { a ->
                put(JSONObject().apply {
                    put("name", a.name)
                    put("position", a.position)
                })
            }
        })
        put("affiliationType", d.affiliationType.name)
        put("headOfTeamCn", d.headOfTeamCn)
        put("foremanCn", d.foremanCn)
        put("headOfTeamContractor", d.headOfTeamContractor)
        put("supervisor", d.supervisor)
        put("contractMaker", d.contractMaker)
        put("otherAffiliation", d.otherAffiliation)
        put("days", d.days)
        put("startDate", d.startDate)
        put("endDate", d.endDate)
        put("guarantorName", d.guarantorName)
        put("guarantorPosition", d.guarantorPosition)
        put("guarantorCompany", d.guarantorCompany)
        put("sequenceNo", d.sequenceNo)
        put("documentDate", d.documentDate)
        put("card", d.cardPath ?: JSONObject.NULL)
        put("signApplicant", d.signatureApplicantPath ?: JSONObject.NULL)
        put("signApprover", d.signatureApproverPath ?: JSONObject.NULL)
        put("withSign", d.withSignature)
    }

    fun jsonToDorm(o: JSONObject): DormFormData {
        val appsArr = o.optJSONArray("applicants") ?: JSONArray()
        val applicants = (0 until 3).map { i ->
            val a = if (i < appsArr.length()) appsArr.optJSONObject(i) else null
            DormFormData.Applicant(
                name = a?.optString("name") ?: "",
                position = a?.optString("position") ?: ""
            )
        }
        return DormFormData(
            applicants = applicants,
            affiliationType = runCatching {
                DormFormData.AffiliationType.valueOf(o.optString("affiliationType", "NONE"))
            }.getOrDefault(DormFormData.AffiliationType.NONE),
            headOfTeamCn = o.optString("headOfTeamCn"),
            foremanCn = o.optString("foremanCn"),
            headOfTeamContractor = o.optString("headOfTeamContractor"),
            supervisor = o.optString("supervisor"),
            contractMaker = o.optString("contractMaker"),
            otherAffiliation = o.optString("otherAffiliation"),
            days = o.optString("days"),
            startDate = o.optString("startDate"),
            endDate = o.optString("endDate"),
            guarantorName = o.optString("guarantorName"),
            guarantorPosition = o.optString("guarantorPosition"),
            guarantorCompany = o.optString("guarantorCompany"),
            sequenceNo = o.optString("sequenceNo"),
            documentDate = o.optString("documentDate"),
            cardPath = o.optString("card").takeIf { it.isNotBlank() && it != "null" },
            signatureApplicantPath = o.optString("signApplicant").takeIf { it.isNotBlank() && it != "null" },
            signatureApproverPath = o.optString("signApprover").takeIf { it.isNotBlank() && it != "null" },
            withSignature = o.optBoolean("withSign", true)
        )
    }

    data class RecordMeta(
        val id: String,
        val label: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    fun newRecordId(): String = UUID.randomUUID().toString().take(8)
}
