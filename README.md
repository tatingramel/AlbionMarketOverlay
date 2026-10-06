# Albion Overlay (Asia / East)

Floating overlay that shows Trading Post prices per city, with name search and a watchlist.
Data: Albion Online Data Project (https://east.albion-online-data.com).

## Build
1. Download `formatted/items.txt` from the ao-bin-dumps repo and save it as
   `app/src/main/assets/items.txt` (needed for name search).
2. Open this folder in Android Studio, let it sync (it creates the Gradle wrapper), then Build > Build APK.
3. Install the APK, open the app, grant "Display over other apps", tap Start overlay.

Change server: edit BASE in AlbionApi.kt.
