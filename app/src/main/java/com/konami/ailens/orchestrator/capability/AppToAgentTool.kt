package com.konami.ailens.orchestrator.capability

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AppToAgentTool {
    @SerialName("volume")
    Volume,

    @SerialName("brightness")
    Brightness,

    @SerialName("screenMode")
    ScreenMode,

    @SerialName("dnd")
    Dnd,

    @SerialName("battery")
    Battery,

    @SerialName("takeAllPhoto")
    TakeAllPhoto,

    @SerialName("translationPage")
    TranslationPage,

    @SerialName("sportsWidget")
    SportsWidget,

    @SerialName("newsWidget")
    NewsWidget,

    @SerialName("weatherWidget")
    WeatherWidget,

    @SerialName("stockTicker")
    StockTicker,

    @SerialName("healthWidget")
    HealthWidget,

    @SerialName("version")
    Version,

    @SerialName("language")
    Language,

    @SerialName("takeVideo")
    TakeVideo,

    @SerialName("streamPage")
    StreamPage,

    @SerialName("teleprompter")
    Teleprompter,

    @SerialName("poiWidget")
    PoiWidget,

    @SerialName("navigationPage")
    NavigationPage,

    @SerialName("compassPage")
    CompassPage
}
