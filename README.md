# GPS Setter

[![release](https://img.shields.io/github/v/release/jqssun/android-gps-setter)](https://github.com/jqssun/android-gps-setter/releases)
[![downloads](https://img.shields.io/github/downloads/jqssun/android-gps-setter/total)](https://github.com/jqssun/android-gps-setter/releases)
  
[![license](https://img.shields.io/github/license/jqssun/android-gps-setter)](https://github.com/jqssun/android-gps-setter/blob/master/LICENSE)
[![issues](https://img.shields.io/github/issues/jqssun/android-gps-setter)](https://github.com/jqssun/android-gps-setter/issues)
[![stars](https://img.shields.io/github/stars/jqssun/android-gps-setter)](https://github.com/jqssun/android-gps-setter/stargazers)

<!-- 
[![LSPosed](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.jqssun.android-gps-setter/total?label=LSPosed%20Repo&logo=Android&style=flat&labelColor=F48FB1&logoColor=ffffff)](https://github.com/Xposed-Modules-Repo/com.jqssun.android-gps-setter/releases)
[![Github downloads](https://img.shields.io/github/downloads/jqssun/android-gps-setter/total?label=Release)]()
![](https://github.com/Xposed-Modules-Repo/io.github.jqssun.gps-setter/blob/main/banner.png) 
-->

## Motivation

An increasing number of apps are abusing the location permission for tracking purposes, preventing the user from using the app without granting the permission. Traditionally on Android, modifying the response from android server is done using the mock location provider - however, the availability of this feature is device dependent. Additionally, some apps have started explicitly checking for signals regarding whether the location provided is reliable. This module aims to mitigate this by providing an ability to either,
1. hook the app itself to modify the location it receives, or
2. hook the system server if the app explicitly checks for whether itself is being hooked

Specifically, in the case of hooking just the app, it intercepts [`android.location.Location`](https://developer.android.com/reference/android/location/Location) and [`android.location.LocationManager`](https://developer.android.com/reference/android/location/LocationManager) methods including
- [`getLatitude()`](https://developer.android.com/reference/android/location/Location#getLatitude())
- [`getLongitude()`](https://developer.android.com/reference/android/location/Location#getLongitude())
- [`getAccuracy()`](https://developer.android.com/reference/android/location/Location#getAccuracy())
- [`getLastKnownLocation(java.lang.String)`](https://developer.android.com/reference/android/location/LocationManager#getLastKnownLocation(java.lang.String))

## Changes

This module inherits from the original GpsSetter project with the following changes:
- added support for new location APIs in system server from Android 14+
- added support for dynamically adjusting the location via a joystick overlay
- added ability to only depend on FOSS libraries
- updated UI to work with latest Material Design
- updated custom designed resource bundles
- newer dependencies

## Compatibility

- Android 8.1+ (tested up to Android 15)
- Rooted devices with Xposed framework installed (e.g. LSPosed)
- Unrooted devices with LSPatch (with manually embedded specified location)

## Credits

- [Android1500](https://github.com/Android1500/GpsSetter)
- [MapLibre](https://github.com/maplibre/maplibre-native)
- [microG](https://github.com/microg/GmsCore)