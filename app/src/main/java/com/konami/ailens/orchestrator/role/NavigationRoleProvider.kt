package com.konami.ailens.orchestrator.role

/**
 * Provider interface for NavigationRole.
 * This allows the main source set to register navigation capabilities
 * without directly depending on NavigationRole, which only exists in AiLens flavor.
 */
interface NavigationRoleProvider {
    /**
     * Creates and returns a NavigationRole instance if navigation is available,
     * or null if navigation is not supported in this build flavor.
     */
    fun createNavigationRole(): Role?

    companion object {
        /**
         * Get the navigation role provider for the current build flavor.
         * Returns null if navigation is not available (e.g., in Despacito1 flavor).
         */
        fun getInstance(): NavigationRoleProvider? {
            return try {
                val providerClass = Class.forName("com.konami.ailens.orchestrator.role.NavigationRoleProviderImpl")
                providerClass.getDeclaredConstructor().newInstance() as NavigationRoleProvider
            } catch (e: ClassNotFoundException) {
                null // Navigation not available in this flavor
            }
        }
    }
}
