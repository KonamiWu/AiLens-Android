package com.konami.ailens.agent

data class EnvironmentConfig(
    val backendURL: String,
    val supabaseURL: String,
    val supabaseAnonKey: String,
    val websocketEndpoint: String,
    val translationEndpoint: String,
    val sessionType: String
)

enum class Environment(val config: EnvironmentConfig) {
    Dev(
        EnvironmentConfig(
            backendURL = "https://thinkar-dev-nest-service.azurewebsites.net",
            supabaseURL = "https://zaeroloxirimsdrurzne.supabase.co",
            supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InphZXJvbG94aXJpbXNkcnVyem5lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDA5ODQ4MDMsImV4cCI6MjA1NjU2MDgwM30.4BIDwEycmdt_bHvVrALbUyl1LAUiCuqc-t7MtwQQrfA",
            websocketEndpoint = "/live-agent",
            translationEndpoint = "/translation",
            sessionType = "video"
        )
    ),
    Stage(
        EnvironmentConfig(
            backendURL = "https://thinkar-stage-nest-service.azurewebsites.net",
            supabaseURL = "https://agobxkgpfjpgeouysqgp.supabase.co",
            supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFnb2J4a2dwZmpwZ2VvdXlzcWdwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzc0NjE4ODYsImV4cCI6MjA1MzAzNzg4Nn0.qdnAGRufrSL7MyQO_1Q0Ku62SQpzPWbhjxBWDT57eL4",
            websocketEndpoint = "/live-agent",
            translationEndpoint = "/translation",
            sessionType = "video"
        )
    )
}

enum class ServiceMode(val value: String) {
    Agent("agent"),
    Translation("translation");
}

enum class TranslationLanguage(val code: String, val displayName: String) {
    English("en", "English"),
    Chinese("zh-Hant", "Chinese"),
    Japanese("ja", "Japanese");
}