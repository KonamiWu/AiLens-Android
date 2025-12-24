package com.konami.ailens.navigation

import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.konami.ailens.orchestrator.Orchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RouteUi(
    val origin: LatLng,
    val destination: LatLng,
    val points: List<LatLng>,
    val durationText: String,
    val distanceText: String
)

class NavigationMapViewModel(app: Application) : AndroidViewModel(app) {
    private val client = OkHttpClient()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address

    private val _route = MutableStateFlow<RouteUi?>(null)
    val route: StateFlow<RouteUi?> = _route

    fun clearRoute() {
        _route.value = null
        _address.value = ""
    }

    fun handleMapTap(dest: LatLng, mode: Orchestrator.TravelMode, addressOverride: String? = null) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val addr = addressOverride ?: try {
                Geocoder(context).getFromLocation(dest.latitude, dest.longitude, 1)?.firstOrNull()?.getAddressLine(0)
            } catch (_: Exception) { null }
            _address.value = addr ?: "${dest.latitude}, ${dest.longitude}"

            val lm = context.getSystemService(LocationManager::class.java)
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (p in providers) {
                try {
                    val l = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || l.time > best!!.time) best = l
                } catch (_: SecurityException) {}
            }
            val origin = best ?: return@launch

            val key = try {
                val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                ai.metaData.getString("com.google.android.geo.API_KEY")
            } catch (e: Exception) { null } ?: return@launch
            val modeStr = when (mode) {
                Orchestrator.TravelMode.WALKING -> "walking"
                Orchestrator.TravelMode.MOTORCYCLE -> "driving"
                else -> "driving"
            }
            val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${dest.latitude},${dest.longitude}&mode=$modeStr&key=$key"
            try {
                val req = Request.Builder().url(url).build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: return@launch
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes") ?: return@launch
                if (routes.length() == 0) return@launch
                val route0 = routes.getJSONObject(0)
                val overview = route0.getJSONObject("overview_polyline").getString("points")
                val leg0 = route0.getJSONArray("legs").getJSONObject(0)
                val durationText = leg0.getJSONObject("duration").getString("text")
                val distanceText = leg0.getJSONObject("distance").getString("text")

                val points = decodePolyline(overview)
                _route.value = RouteUi(
                    origin = LatLng(origin.latitude, origin.longitude),
                    destination = dest,
                    points = points,
                    durationText = durationText,
                    distanceText = distanceText
                )
            } catch (_: Exception) { }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}

