package org.avmedia.timeserverconfigurator

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object Utils {
    fun isValidJson(jsonString: String): Boolean {
        return try {
            JSONObject(jsonString) // If this succeeds, it's a valid JSON object
            true
        } catch (e: JSONException) {
            try {
                JSONArray(jsonString) // Or it may be a JSON array
                true
            } catch (e: JSONException) {
                false
            }
        }
    }
}