package com.konami.ailens.navigation

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val placeId: String,
    val description: String,
    val mainText: String,
    val secondaryText: String
)

class AddressPickerViewModel(app: Application) : AndroidViewModel(app) {
    private val client = OkHttpClient()
    
    private val _autoCompletePredictions = MutableStateFlow<List<AutoCompletePrediction>>(emptyList())
    val autoCompletePredictions: StateFlow<List<AutoCompletePrediction>> = _autoCompletePredictions
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private var searchJob: Job? = null
    
    fun searchAutoComplete(query: String) {
        // Cancel previous search
        searchJob?.cancel()
        
        if (query.trim().length < 2) {
            _autoCompletePredictions.value = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Add delay to avoid too many requests
                delay(300)
                
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }
                
                val context = getApplication<Application>()
                val key = try {
                    val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    ai.metaData.getString("com.google.android.geo.API_KEY")
                } catch (e: Exception) { 
                    Log.e("AddressPickerViewModel", "Failed to get API key", e)
                    null 
                } ?: return@launch
                
                val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=${query.trim()}&key=$key"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch
                
                val json = JSONObject(body)
                val predictions = json.optJSONArray("predictions")
                val results = mutableListOf<AutoCompletePrediction>()
                
                predictions?.let { array ->
                    for (i in 0 until array.length()) {
                        val prediction = array.getJSONObject(i)
                        val placeId = prediction.getString("place_id")
                        val description = prediction.getString("description")
                        
                        val structuredFormatting = prediction.optJSONObject("structured_formatting")
                        val mainText = structuredFormatting?.optString("main_text") ?: description
                        val secondaryText = structuredFormatting?.optString("secondary_text") ?: ""
                        
                        results.add(AutoCompletePrediction(
                            placeId = placeId,
                            description = description,
                            mainText = mainText,
                            secondaryText = secondaryText
                        ))
                    }
                }
                
                withContext(Dispatchers.Main) {
                    _autoCompletePredictions.value = results
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
                
                val url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry,formatted_address&key=$key"
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