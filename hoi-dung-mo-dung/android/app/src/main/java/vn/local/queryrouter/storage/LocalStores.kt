package vn.local.queryrouter.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import vn.local.queryrouter.core.*

data class PersonalItem(val id: String, val title: String, val content: String, val type: String)

class PersonalVault(context: Context) {
    private val prefs = context.getSharedPreferences("personal_vault", Context.MODE_PRIVATE)
    fun list(): List<PersonalItem> {
        val arr = JSONArray(prefs.getString("items", "[]"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i) }.map {
            PersonalItem(it.getString("id"), it.getString("title"), it.getString("content"), it.getString("type"))
        }
    }
    fun add(title: String, content: String, type: String) {
        val all = list().toMutableList(); all += PersonalItem(System.currentTimeMillis().toString(), title, content, type); save(all)
    }
    fun delete(id: String) = save(list().filterNot { it.id == id })
    private fun save(items: List<PersonalItem>) {
        val arr = JSONArray(); items.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("content", it.content).put("type", it.type)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

data class ContactItem(val id: String, val nameOrUnit: String, val position: String, val phone: String)

class ContactStore(context: Context) {
    private val prefs = context.getSharedPreferences("private_contacts", Context.MODE_PRIVATE)
    fun list(): List<ContactItem> {
        val arr = JSONArray(prefs.getString("contacts", "[]"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i) }.map {
            ContactItem(it.getString("id"), it.getString("nameOrUnit"), it.optString("position"), it.getString("phone"))
        }.sortedBy { it.nameOrUnit.lowercase() }
    }
    fun add(nameOrUnit: String, position: String, phone: String) {
        val all = list().toMutableList(); all += ContactItem(System.currentTimeMillis().toString(), nameOrUnit.trim(), position.trim(), phone.trim()); save(all)
    }
    fun delete(id: String) = save(list().filterNot { it.id == id })
    private fun save(items: List<ContactItem>) {
        val arr = JSONArray(); items.forEach { arr.put(JSONObject().put("id", it.id).put("nameOrUnit", it.nameOrUnit).put("position", it.position).put("phone", it.phone)) }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }
}

class CustomAppStore(context: Context) {
    private val prefs = context.getSharedPreferences("custom_apps", Context.MODE_PRIVATE)
    fun apps(): List<AppProfile> {
        val arr = JSONArray(prefs.getString("apps", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            AppProfile(o.getString("id"), o.getString("name"), o.getString("field"), o.getString("description"), o.optString("packageName").ifBlank { null }, listOf("custom_${o.getString("id")}"), userDefined = true)
        }
    }
    fun functions(): List<FunctionNode> {
        val arr = JSONArray(prefs.getString("apps", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val id = o.getString("id"); val routeType = o.optString("routeType", "PACKAGE"); val routeValue = o.optString("routeValue", "")
            FunctionNode(
                id = "custom_$id", field = o.getString("field"), service = o.getString("name"), group = "Người dùng thêm",
                title = o.optString("functionTitle", o.getString("name")), description = o.getString("description"),
                keywords = o.optString("keywords", "").split(',').map { it.trim() }.filter { it.isNotBlank() } + listOf(o.getString("name")),
                answerMode = AnswerMode.ACTION_ONLY,
                routes = if (routeValue.isBlank()) emptyList() else listOf(RouteSpec("route_$id", RouteType.valueOf(routeType), routeValue, "Mở ${o.getString("name")}", 50, VerificationStatus.VERIFIED, if (routeType == "PACKAGE") routeValue else null)),
                dataStatus = DataStatus.CURRENT
            )
        }
    }
    fun add(name: String, field: String, description: String, functionTitle: String, keywords: String, packageName: String, routeType: RouteType, routeValue: String) {
        val arr = JSONArray(prefs.getString("apps", "[]")); val id = System.currentTimeMillis().toString()
        arr.put(JSONObject().put("id", id).put("name", name).put("field", field).put("description", description).put("functionTitle", functionTitle).put("keywords", keywords).put("packageName", packageName).put("routeType", routeType.name).put("routeValue", routeValue))
        prefs.edit().putString("apps", arr.toString()).apply()
    }
}
