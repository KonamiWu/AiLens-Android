package com.konami.ailens.orchestrator.capability

enum class AgentToAppTool(val raw: String) {
    VOLUME("thinkar_device_tool_volume"),
    BRIGHTNESS("thinkar_device_tool_brightness"),
    SCREEN_MODE("thinkar_device_tool_screen_mode"),
    DND("thinkar_device_tool_dnd"),
    BATTERY("thinkar_device_tool_battery"),

    TAKE_ALL_PHOTO("thinkar_device_tool_page_take_All_photo"),
    TRANSLATION_PAGE("thinkar_device_tool_page_translation"),
    SPORTS_WIDGET("thinkar_device_tool_widget_sports"),
    NEWS_WIDGET("thinkar_device_tool_widget_news"),
    WEATHER_WIDGET("thinkar_device_tool_widget_weather"),
    STOCK_TICKER("thinkar_device_tool_widget_stock_ticker"),
    HEALTH_WIDGET("thinkar_device_tool_widget_health"),

    VERSION("thinkar_device_tool_version"),
    LANGUAGE("thinkar_device_tool_language"),
    TAKE_VIDEO("thinkar_device_tool_page_take_vid"),
    STREAM_PAGE("thinkar_device_tool_page_start_streaming"),
    TELEPROMPTER("thinkar_device_tool_page_teleprompter"),
    POI_WIDGET("thinkar_device_tool_widget_point_of_interest"),
    NAVIGATION_PAGE("thinkar_device_tool_page_navigation"),
    COMPASS_PAGE("thinkar_device_tool_page_compass"),

    UNKNOWN("unknown");

    companion object {
        fun fromRaw(raw: String): AgentToAppTool {
            return entries.firstOrNull { it.raw == raw } ?: UNKNOWN
        }
    }
}