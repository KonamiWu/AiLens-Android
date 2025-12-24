package com.konami.ailens.navigation

import android.app.Activity

/**
 * Provider interface for NavigationService.
 * This allows the main source set to initialize navigation
 * without directly depending on NavigationService, which only exists in AiLens flavor.
 */
interface NavigationServiceProvider {
    /**
     * Initialize the navigator with the given activity.
     */
    fun initializeNavigator(activity: Activity)

    companion object {
        /**
         * Get the navigation service provider for the current build flavor.
         * Returns null if navigation is not available (e.g., in Despacito1 flavor).
         */
        fun getInstance(): NavigationServiceProvider? {
            return try {
                val providerClass = Class.forName("com.konami.ailens.navigation.NavigationServiceProviderImpl")
                providerClass.getDeclaredConstructor().newInstance() as NavigationServiceProvider
            } catch (e: ClassNotFoundException) {
                null // Navigation not available in this flavor
            }
        }
    }
}
