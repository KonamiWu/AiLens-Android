package com.konami.ailens.navigation

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.SharedPrefs
import com.konami.ailens.AddressHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AutoCompletePrediction(
    val placeId: String?,
    val lat: Double?,
    val lng: Double?,
    val description: String,
    val mainText: String,
    val secondaryText: String,
    val isHistory: Boolean = false
)

class AddressPickerViewModel(app: Application) : AndroidViewModel(app) {
    private val client = OkHttpClient()
    
    private val _autoCompletePredictions = MutableStateFlow<List<AutoCompletePrediction>>(emptyList())
    val autoCompletePredictions: StateFlow<List<AutoCompletePrediction>> = _autoCompletePredictions
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private var searchJob: Job? = null
    
    fun searchAutoComplete(query: String) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {

                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }

                // Get filtered history items
                val historyItems = SharedPrefs.instance.filterAddressHistory(query)
                val historyPredictions = historyItems.map { historyItem ->
                    AutoCompletePrediction(
                        placeId = null,
                        lat = historyItem.lat,
                        lng = historyItem.lng,
                        description = "${historyItem.mainText}, ${historyItem.secondaryText}",
                        mainText = historyItem.mainText,
                        secondaryText = historyItem.secondaryText,
                        isHistory = true
                    )
                }

                val context = getApplication<Application>()
                val key = try {
                    val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    ai.metaData.getString("com.google.android.geo.API_KEY")
                } catch (e: Exception) {
                    Log.e("AddressPickerViewModel", "Failed to get API key", e)
                    null
                } ?: return@launch

                // Get system language
                val systemLanguage = context.resources.configuration.locales[0].toLanguageTag()

                // Add language parameter to ensure consistent language in results
                val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=${query.trim()}&language=$systemLanguage&key=$key"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()

                val json = JSONObject(body)
                val predictions = json.optJSONArray("predictions")
                val apiResults = mutableListOf<AutoCompletePrediction>()

                predictions?.let { array ->
                    for (i in 0 until array.length()) {
                        val prediction = array.getJSONObject(i)
                        val placeId = prediction.getString("place_id")
                        val description = prediction.getString("description")

                        val structuredFormatting = prediction.optJSONObject("structured_formatting")
                        val mainText = structuredFormatting?.optString("main_text") ?: description
                        val secondaryText = structuredFormatting?.optString("secondary_text") ?: ""

                        apiResults.add(AutoCompletePrediction(
                            placeId = placeId,
                            lat = null,
                            lng = null,
                            description = description,
                            mainText = mainText,
                            secondaryText = secondaryText,
                            isHistory = false
                        ))
                    }
                }

                // Combine history and API results (history first)
                val combinedResults = historyPredictions + apiResults

                withContext(Dispatchers.Main) {
                    _autoCompletePredictions.value = combinedResults
                    _isLoading.value = false
                }

            } catch (e: Exception) {
                Log.e("AddressPickerViewModel", "AutoComplete search failed", e)
                withContext(Dispatchers.Main) {
                    _autoCompletePredictions.value = emptyList()
                    _isLoading.value = false
                }
            }
        }
    }
    
    fun getPlaceDetails(placeId: String, onResult: (lat: Double, lng: Double, address: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val key = try {
                    val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    ai.metaData.getString("com.google.android.geo.API_KEY")
                } catch (e: Exception) {
                    Log.e("AddressPickerViewModel", "Failed to get API key", e)
                    null
                } ?: return@launch

                // Get system language
                val systemLanguage = context.resources.configuration.locales[0].toLanguageTag()

                val url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry,formatted_address&language=$systemLanguage&key=$key"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch
                
                val json = JSONObject(body)
                val result = json.optJSONObject("result") ?: return@launch
                val geometry = result.getJSONObject("geometry")
                val location = geometry.getJSONObject("location")
                val lat = location.getDouble("lat")
                val lng = location.getDouble("lng")
                val address = result.getString("formatted_address")
                
                withContext(Dispatchers.Main) {
                    onResult(lat, lng, address)
                }
                
            } catch (e: Exception) {
                Log.e("AddressPickerViewModel", "Place details failed", e)
            }
        }
    }
    
    fun clearAutoComplete() {
        searchJob?.cancel()
        _autoCompletePredictions.value = emptyList()
        _isLoading.value = false
    }
}