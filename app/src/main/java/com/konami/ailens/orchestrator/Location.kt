package com.konami.ailens.orchestrator

/**
 * Represents a geographical location with latitude and longitude coordinates.
 * This is a platform-agnostic alternative to Google Maps' LatLng class.
 */
data class Location(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
    }

    override fun toString(): String = "($latitude, $longitude)"
}
