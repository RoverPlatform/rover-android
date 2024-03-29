/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.platform

import android.os.Build

/**
 * Uses Jared Rummler's library to get the marketing/consumer name of the device. Otherwise,
 * returns [Build.MODEL].
 */
fun getDeviceName(): String {
    return getDeviceName(Build.DEVICE, Build.MODEL, Build.MODEL.capitalize())
}

/**
 * Get the consumer friendly name of a device.
 *
 * Borrowed from https://github.com/jaredrummler/AndroidDeviceNames by Jared Rummler.
 *
 * (getDeviceName() method copied, specifically, and transformed into Kotlin)
 *
 * @param codename
 * the value of the system property "ro.product.device" ([Build.DEVICE]).
 * @param model
 * the value of the system property "ro.product.model" ([Build.MODEL]).
 * @param fallback
 * the fallback name if the device is unknown. Usually the value of the system property
 * "ro.product.model" ([Build.MODEL])
 * @return the market name of a device or `fallback` if the device is unknown.
 */

fun getDeviceName(codename: String?, model: String?, fallback: String): String {
    // ----------------------------------------------------------------------------
    // Acer
    if (codename != null && codename == "acer_S57" || model != null && model == "S57") {
        return "Liquid Jade Z"
    }
    if (codename != null && codename == "acer_t08" || model != null && model == "T08") {
        return "Liquid Zest Plus"
    }
    // ----------------------------------------------------------------------------
    // Asus
    if (codename != null && (codename == "grouper" || codename == "tilapia")) {
        return "Nexus 7 (2012)"
    }
    if (codename != null && (codename == "deb" || codename == "flo")) {
        return "Nexus 7 (2013)"
    }
    // ----------------------------------------------------------------------------
    // Google
    if (codename != null && codename == "sailfish") {
        return "Pixel"
    }
    if (codename != null && codename == "marlin") {
        return "Pixel XL"
    }
    if (codename != null && codename == "dragon") {
        return "Pixel C"
    }
    if (codename != null && codename == "walleye") {
        return "Pixel 2"
    }
    if (codename != null && codename == "taimen") {
        return "Pixel 2 XL"
    }
    if (codename != null && codename == "blueline") {
        return "Pixel 3"
    }
    if (codename != null && codename == "crosshatch") {
        return "Pixel 3 XL"
    }
    if (codename != null && codename == "sargo") {
        return "Pixel 3a"
    }
    if (codename != null && codename == "bonito") {
        return "Pixel 3a XL"
    }
    if (codename != null && codename == "flame") {
        return "Pixel 4"
    }
    if (codename != null && codename == "coral") {
        return "Pixel 4 XL"
    }
    if (codename != null && codename == "flounder") {
        return "Nexus 9"
    }
    // ----------------------------------------------------------------------------
    // Huawei
    if (codename != null && codename == "HWBND-H" ||
        model != null && (model == "BND-L21" || model == "BND-L24" || model == "BND-L31")
    ) {
        return "Honor 7X"
    }
    if (codename != null && codename == "HWBKL" ||
        model != null && (model == "BKL-L04" || model == "BKL-L09")
    ) {
        return "Honor View 10"
    }
    if (codename != null && codename == "HWALP" ||
        model != null && (
            model == "ALP-AL00" || model == "ALP-L09" || model == "ALP-L29" ||
                model == "ALP-TL00"
            )
    ) {
        return "Mate 10"
    }
    if (codename != null && codename == "HWMHA" ||
        model != null && (
            model == "MHA-AL00" || model == "MHA-L09" || model == "MHA-L29" ||
                model == "MHA-TL00"
            )
    ) {
        return "Mate 9"
    }
    if (codename != null && codename == "angler") {
        return "Nexus 6P"
    }
    // ----------------------------------------------------------------------------
    // LGE
    if (codename != null && codename == "g2" || model != null && (
        model == "LG-D800" ||
            model == "LG-D801" ||
            model == "LG-D802" ||
            model == "LG-D802T" ||
            model == "LG-D802TR" ||
            model == "LG-D803" ||
            model == "LG-D805" ||
            model == "LG-D806" ||
            model == "LG-F320K" ||
            model == "LG-F320L" ||
            model == "LG-F320S" ||
            model == "LG-LS980" ||
            model == "VS980 4G"
        )
    ) {
        return "LG G2"
    }
    if (codename != null && codename == "g3" || model != null && (
        model == "AS985" ||
            model == "LG-AS990" ||
            model == "LG-D850" ||
            model == "LG-D851" ||
            model == "LG-D852" ||
            model == "LG-D852G" ||
            model == "LG-D855" ||
            model == "LG-D856" ||
            model == "LG-D857" ||
            model == "LG-D858" ||
            model == "LG-D858HK" ||
            model == "LG-D859" ||
            model == "LG-F400K" ||
            model == "LG-F400L" ||
            model == "LG-F400S" ||
            model == "LGL24" ||
            model == "LGLS990" ||
            model == "LGUS990" ||
            model == "LGV31" ||
            model == "VS985 4G"
        )
    ) {
        return "LG G3"
    }
    if (codename != null && codename == "p1" || model != null && (
        model == "AS986" ||
            model == "LG-AS811" ||
            model == "LG-AS991" ||
            model == "LG-F500K" ||
            model == "LG-F500L" ||
            model == "LG-F500S" ||
            model == "LG-H810" ||
            model == "LG-H811" ||
            model == "LG-H812" ||
            model == "LG-H815" ||
            model == "LG-H818" ||
            model == "LG-H819" ||
            model == "LGLS991" ||
            model == "LGUS991" ||
            model == "LGV32" ||
            model == "VS986"
        )
    ) {
        return "LG G4"
    }
    if (codename != null && codename == "h1" ||
        model != null && (
            model == "LG-F700K" ||
                model == "LG-F700L" ||
                model == "LG-F700S" ||
                model == "LG-H820" ||
                model == "LG-H820PR" ||
                model == "LG-H830" ||
                model == "LG-H831" ||
                model == "LG-H850" ||
                model == "LG-H858" ||
                model == "LG-H860" ||
                model == "LG-H868" ||
                model == "LGAS992" ||
                model == "LGLS992" ||
                model == "LGUS992" ||
                model == "RS988" ||
                model == "VS987"
            )
    ) {
        return "LG G5"
    }
    if (codename != null && codename == "lucye" || model != null && (
        model == "LG-AS993" ||
            model == "LG-H870" || model == "LG-H870AR" || model == "LG-H870DS" ||
            model == "LG-H870I" || model == "LG-H870S" || model == "LG-H871" ||
            model == "LG-H871S" || model == "LG-H872" ||
            model == "LG-H872PR" || model == "LG-H873" || model == "LG-LS993" ||
            model == "LGM-G600K" || model == "LGM-G600L" || model == "LGM-G600S" ||
            model == "LGUS997" || model == "VS988"
        )
    ) {
        return "LG G6"
    }
    if (codename != null && codename == "flashlmdd" ||
        model != null && (model == "LM-V500" || model == "LM-V500N")
    ) {
        return "LG V50 ThinQ"
    }
    if (codename != null && codename == "mako") {
        return "Nexus 4"
    }
    if (codename != null && codename == "hammerhead") {
        return "Nexus 5"
    }
    if (codename != null && codename == "bullhead") {
        return "Nexus 5X"
    }
    // ----------------------------------------------------------------------------
    // Motorola
    if (codename != null && codename == "griffin" ||
        model != null && (model == "XT1650" || model == "XT1650-05")
    ) {
        return "Moto Z"
    }
    if (codename != null && codename == "shamu") {
        return "Nexus 6"
    }
    // ----------------------------------------------------------------------------
    // Nokia
    if (codename != null && (
        codename == "RHD" || codename == "ROO" || codename == "ROON_sprout" ||
            codename == "ROO_sprout"
        )
    ) {
        return "Nokia 3.1 Plus"
    }
    if (codename != null && codename == "CTL_sprout") {
        return "Nokia 7.1"
    }
    // ----------------------------------------------------------------------------
    // OnePlus
    if (codename != null && codename == "OnePlus3" || model != null && model == "ONEPLUS A3000") {
        return "OnePlus3"
    }
    if (codename != null && codename == "OnePlus3T" || model != null && model == "ONEPLUS A3000") {
        return "OnePlus3T"
    }
    if (codename != null && codename == "OnePlus5" || model != null && model == "ONEPLUS A5000") {
        return "OnePlus5"
    }
    if (codename != null && codename == "OnePlus5T" || model != null && model == "ONEPLUS A5010") {
        return "OnePlus5T"
    }
    if (codename != null && codename == "OnePlus6" ||
        model != null && (model == "ONEPLUS A6000" || model == "ONEPLUS A6003")
    ) {
        return "OnePlus 6"
    }
    if (codename != null && (codename == "OnePlus6T" || codename == "OnePlus6TSingle") ||
        model != null && model == "ONEPLUS A6013"
    ) {
        return "OnePlus 6T"
    }
    if (codename != null && codename == "OnePlus7" ||
        model != null && model == "GM1905"
    ) {
        return "OnePlus 7"
    }
    if (codename != null && (
        codename == "OP7ProNRSpr" || codename == "OnePlus7Pro" ||
            codename == "OnePlus7ProTMO"
        ) || model != null && (
            model == "GM1915" ||
                model == "GM1917" || model == "GM1925"
            )
    ) {
        return "OnePlus 7 Pro"
    }
    // ----------------------------------------------------------------------------
    // Samsung
    if (codename != null && (
        codename == "a53g" ||
            codename == "a5lte" ||
            codename == "a5ltechn" ||
            codename == "a5ltectc" ||
            codename == "a5ltezh" ||
            codename == "a5ltezt" ||
            codename == "a5ulte" ||
            codename == "a5ultebmc" ||
            codename == "a5ultektt" ||
            codename == "a5ultelgt" ||
            codename == "a5ulteskt"
        ) || model != null && (
            model == "SM-A5000" ||
                model == "SM-A5009" ||
                model == "SM-A500F" ||
                model == "SM-A500F1" ||
                model == "SM-A500FU" ||
                model == "SM-A500G" ||
                model == "SM-A500H" ||
                model == "SM-A500K" ||
                model == "SM-A500L" ||
                model == "SM-A500M" ||
                model == "SM-A500S" ||
                model == "SM-A500W" ||
                model == "SM-A500X" ||
                model == "SM-A500XZ" ||
                model == "SM-A500Y" ||
                model == "SM-A500YZ"
            )
    ) {
        return "Galaxy A5"
    }
    if (codename != null && codename == "vivaltods5m" || model != null && (
        model == "SM-G313HU" ||
            model == "SM-G313HY" ||
            model == "SM-G313M" ||
            model == "SM-G313MY"
        )
    ) {
        return "Galaxy Ace 4"
    }
    if (codename != null && (
        codename == "GT-S6352" ||
            codename == "GT-S6802" ||
            codename == "GT-S6802B" ||
            codename == "SCH-I579" ||
            codename == "SCH-I589" ||
            codename == "SCH-i579" ||
            codename == "SCH-i589"
        ) || model != null && (
            model == "GT-S6352" ||
                model == "GT-S6802" ||
                model == "GT-S6802B" ||
                model == "SCH-I589" ||
                model == "SCH-i579" ||
                model == "SCH-i589"
            )
    ) {
        return "Galaxy Ace Duos"
    }
    if (codename != null && (
        codename == "GT-S7500" ||
            codename == "GT-S7500L" ||
            codename == "GT-S7500T" ||
            codename == "GT-S7500W" ||
            codename == "GT-S7508"
        ) || model != null && (
            model == "GT-S7500" ||
                model == "GT-S7500L" ||
                model == "GT-S7500T" ||
                model == "GT-S7500W" ||
                model == "GT-S7508"
            )
    ) {
        return "Galaxy Ace Plus"
    }
    if (codename != null && (
        codename == "heat3gtfnvzw" ||
            codename == "heatnfc3g" ||
            codename == "heatqlte"
        ) || model != null && (
            model == "SM-G310HN" ||
                model == "SM-G357FZ" ||
                model == "SM-S765C" ||
                model == "SM-S766C"
            )
    ) {
        return "Galaxy Ace Style"
    }
    if (codename != null && (
        codename == "vivalto3g" ||
            codename == "vivalto3mve3g" ||
            codename == "vivalto5mve3g" ||
            codename == "vivaltolte" ||
            codename == "vivaltonfc3g"
        ) || model != null && (
            model == "SM-G313F" ||
                model == "SM-G313HN" ||
                model == "SM-G313ML" ||
                model == "SM-G313MU" ||
                model == "SM-G316H" ||
                model == "SM-G316HU" ||
                model == "SM-G316M" ||
                model == "SM-G316MY"
            )
    ) {
        return "Galaxy Ace4"
    }
    if (codename != null && (
        codename == "core33g" ||
            codename == "coreprimelte" ||
            codename == "coreprimelteaio" ||
            codename == "coreprimeltelra" ||
            codename == "coreprimeltespr" ||
            codename == "coreprimeltetfnvzw" ||
            codename == "coreprimeltevzw" ||
            codename == "coreprimeve3g" ||
            codename == "coreprimevelte" ||
            codename == "cprimeltemtr" ||
            codename == "cprimeltetmo" ||
            codename == "rossalte" ||
            codename == "rossaltectc" ||
            codename == "rossaltexsa"
        ) || model != null && (
            model == "SAMSUNG-SM-G360AZ" ||
                model == "SM-G3606" ||
                model == "SM-G3608" ||
                model == "SM-G3609" ||
                model == "SM-G360F" ||
                model == "SM-G360FY" ||
                model == "SM-G360GY" ||
                model == "SM-G360H" ||
                model == "SM-G360HU" ||
                model == "SM-G360M" ||
                model == "SM-G360P" ||
                model == "SM-G360R6" ||
                model == "SM-G360T" ||
                model == "SM-G360T1" ||
                model == "SM-G360V" ||
                model == "SM-G361F" ||
                model == "SM-G361H" ||
                model == "SM-G361HU" ||
                model == "SM-G361M" ||
                model == "SM-S820L"
            )
    ) {
        return "Galaxy Core Prime"
    }
    if (codename != null && (
        codename == "kanas" ||
            codename == "kanas3g" ||
            codename == "kanas3gcmcc" ||
            codename == "kanas3gctc" ||
            codename == "kanas3gnfc"
        ) || model != null && (
            model == "SM-G3556D" ||
                model == "SM-G3558" ||
                model == "SM-G3559" ||
                model == "SM-G355H" ||
                model == "SM-G355HN" ||
                model == "SM-G355HQ" ||
                model == "SM-G355M"
            )
    ) {
        return "Galaxy Core2"
    }
    if (codename != null && (
        codename == "e53g" ||
            codename == "e5lte" ||
            codename == "e5ltetfnvzw" ||
            codename == "e5ltetw"
        ) || model != null && (
            model == "SM-E500F" ||
                model == "SM-E500H" ||
                model == "SM-E500M" ||
                model == "SM-E500YZ" ||
                model == "SM-S978L"
            )
    ) {
        return "Galaxy E5"
    }
    if (codename != null && (
        codename == "e73g" ||
            codename == "e7lte" ||
            codename == "e7ltechn" ||
            codename == "e7ltectc" ||
            codename == "e7ltehktw"
        ) || model != null && (
            model == "SM-E7000" ||
                model == "SM-E7009" ||
                model == "SM-E700F" ||
                model == "SM-E700H" ||
                model == "SM-E700M"
            )
    ) {
        return "Galaxy E7"
    }
    if (codename != null && (
        codename == "SCH-I629" ||
            codename == "nevis" ||
            codename == "nevis3g" ||
            codename == "nevis3gcmcc" ||
            codename == "nevisds" ||
            codename == "nevisnvess" ||
            codename == "nevisp" ||
            codename == "nevisvess" ||
            codename == "nevisw"
        ) || model != null && (
            model == "GT-S6790" ||
                model == "GT-S6790E" ||
                model == "GT-S6790L" ||
                model == "GT-S6790N" ||
                model == "GT-S6810" ||
                model == "GT-S6810B" ||
                model == "GT-S6810E" ||
                model == "GT-S6810L" ||
                model == "GT-S6810M" ||
                model == "GT-S6810P" ||
                model == "GT-S6812" ||
                model == "GT-S6812B" ||
                model == "GT-S6812C" ||
                model == "GT-S6812i" ||
                model == "GT-S6818" ||
                model == "GT-S6818V" ||
                model == "SCH-I629"
            )
    ) {
        return "Galaxy Fame"
    }
    if (codename != null && codename == "grandprimelteatt" || model != null && model == "SAMSUNG-SM-G530A") {
        return "Galaxy Go Prime"
    }
    if (codename != null && (
        codename == "baffinlite" ||
            codename == "baffinlitedtv" ||
            codename == "baffinq3g"
        ) || model != null && (
            model == "GT-I9060" ||
                model == "GT-I9060L" ||
                model == "GT-I9063T" ||
                model == "GT-I9082C" ||
                model == "GT-I9168" ||
                model == "GT-I9168I"
            )
    ) {
        return "Galaxy Grand Neo"
    }
    if (codename != null && (
        codename == "fortuna3g" ||
            codename == "fortuna3gdtv" ||
            codename == "fortunalte" ||
            codename == "fortunaltectc" ||
            codename == "fortunaltezh" ||
            codename == "fortunaltezt" ||
            codename == "fortunave3g" ||
            codename == "gprimelteacg" ||
            codename == "gprimeltecan" ||
            codename == "gprimeltemtr" ||
            codename == "gprimeltespr" ||
            codename == "gprimeltetfnvzw" ||
            codename == "gprimeltetmo" ||
            codename == "gprimelteusc" ||
            codename == "grandprimelte" ||
            codename == "grandprimelteaio" ||
            codename == "grandprimeve3g" ||
            codename == "grandprimeve3gdtv" ||
            codename == "grandprimevelte" ||
            codename == "grandprimevelteltn" ||
            codename == "grandprimeveltezt"
        ) || model != null && (
            model == "SAMSUNG-SM-G530AZ" ||
                model == "SM-G5306W" ||
                model == "SM-G5308W" ||
                model == "SM-G5309W" ||
                model == "SM-G530BT" ||
                model == "SM-G530F" ||
                model == "SM-G530FZ" ||
                model == "SM-G530H" ||
                model == "SM-G530M" ||
                model == "SM-G530MU" ||
                model == "SM-G530P" ||
                model == "SM-G530R4" ||
                model == "SM-G530R7" ||
                model == "SM-G530T" ||
                model == "SM-G530T1" ||
                model == "SM-G530W" ||
                model == "SM-G530Y" ||
                model == "SM-G531BT" ||
                model == "SM-G531F" ||
                model == "SM-G531H" ||
                model == "SM-G531M" ||
                model == "SM-G531Y" ||
                model == "SM-S920L" ||
                model == "gprimelteacg"
            )
    ) {
        return "Galaxy Grand Prime"
    }
    if (codename != null && (
        codename == "ms013g" ||
            codename == "ms013gdtv" ||
            codename == "ms013gss" ||
            codename == "ms01lte" ||
            codename == "ms01ltektt" ||
            codename == "ms01ltelgt" ||
            codename == "ms01lteskt"
        ) || model != null && (
            model == "SM-G710" ||
                model == "SM-G7102" ||
                model == "SM-G7102T" ||
                model == "SM-G7105" ||
                model == "SM-G7105H" ||
                model == "SM-G7105L" ||
                model == "SM-G7106" ||
                model == "SM-G7108" ||
                model == "SM-G7109" ||
                model == "SM-G710K" ||
                model == "SM-G710L" ||
                model == "SM-G710S"
            )
    ) {
        return "Galaxy Grand2"
    }
    if (codename != null && (
        codename == "j13g" ||
            codename == "j13gtfnvzw" ||
            codename == "j1lte" ||
            codename == "j1nlte" ||
            codename == "j1qltevzw" ||
            codename == "j1xlte" ||
            codename == "j1xlteaio" ||
            codename == "j1xlteatt" ||
            codename == "j1xltecan" ||
            codename == "j1xqltespr" ||
            codename == "j1xqltetfnvzw"
        ) || model != null && (
            model == "SAMSUNG-SM-J120A" ||
                model == "SAMSUNG-SM-J120AZ" ||
                model == "SM-J100F" ||
                model == "SM-J100FN" ||
                model == "SM-J100G" ||
                model == "SM-J100H" ||
                model == "SM-J100M" ||
                model == "SM-J100ML" ||
                model == "SM-J100MU" ||
                model == "SM-J100VPP" ||
                model == "SM-J100Y" ||
                model == "SM-J120F" ||
                model == "SM-J120FN" ||
                model == "SM-J120M" ||
                model == "SM-J120P" ||
                model == "SM-J120W" ||
                model == "SM-S120VL" ||
                model == "SM-S777C"
            )
    ) {
        return "Galaxy J1"
    }
    if (codename != null && (
        codename == "j1acelte" ||
            codename == "j1acelteltn" ||
            codename == "j1acevelte" ||
            codename == "j1pop3g"
        ) || model != null && (
            model == "SM-J110F" ||
                model == "SM-J110G" ||
                model == "SM-J110H" ||
                model == "SM-J110L" ||
                model == "SM-J110M" ||
                model == "SM-J111F" ||
                model == "SM-J111M"
            )
    ) {
        return "Galaxy J1 Ace"
    }
    if (codename != null && (
        codename == "j53g" ||
            codename == "j5lte" ||
            codename == "j5ltechn" ||
            codename == "j5ltekx" ||
            codename == "j5nlte" ||
            codename == "j5ylte"
        ) || model != null && (
            model == "SM-J5007" ||
                model == "SM-J5008" ||
                model == "SM-J500F" ||
                model == "SM-J500FN" ||
                model == "SM-J500G" ||
                model == "SM-J500H" ||
                model == "SM-J500M" ||
                model == "SM-J500N0" ||
                model == "SM-J500Y"
            )
    ) {
        return "Galaxy J5"
    }
    if (codename != null && (
        codename == "j75ltektt" ||
            codename == "j7e3g" ||
            codename == "j7elte" ||
            codename == "j7ltechn"
        ) || model != null && (
            model == "SM-J7008" ||
                model == "SM-J700F" ||
                model == "SM-J700H" ||
                model == "SM-J700K" ||
                model == "SM-J700M"
            )
    ) {
        return "Galaxy J7"
    }
    if (codename != null && codename == "a50" ||
        model != null && (
            model == "SM-A505F" || model == "SM-A505FM" || model == "SM-A505FN" ||
                model == "SM-A505G" || model == "SM-A505GN" || model == "SM-A505GT" ||
                model == "SM-A505N" || model == "SM-A505U" || model == "SM-A505U1" ||
                model == "SM-A505W" || model == "SM-A505YN" || model == "SM-S506DL"
            )
    ) {
        return "Galaxy A50"
    }
    if (codename != null && (
        codename == "a6elteaio" || codename == "a6elteatt" ||
            codename == "a6eltemtr" || codename == "a6eltespr" || codename == "a6eltetmo" ||
            codename == "a6elteue" || codename == "a6lte" || codename == "a6lteks"
        ) ||
        model != null && (
            model == "SM-A600A" || model == "SM-A600AZ" || model == "SM-A600F" ||
                model == "SM-A600FN" || model == "SM-A600G" || model == "SM-A600GN" ||
                model == "SM-A600N" || model == "SM-A600P" || model == "SM-A600T" ||
                model == "SM-A600T1" || model == "SM-A600U"
            )
    ) {
        return "Galaxy A6"
    }
    if (codename != null && (
        codename == "SC-01J" || codename == "SCV34" ||
            codename == "gracelte" || codename == "graceltektt" || codename == "graceltelgt" ||
            codename == "gracelteskt" || codename == "graceqlteacg" || codename == "graceqlteatt" ||
            codename == "graceqltebmc" || codename == "graceqltechn" || codename == "graceqltedcm" ||
            codename == "graceqltelra" || codename == "graceqltespr" ||
            codename == "graceqltetfnvzw" || codename == "graceqltetmo" ||
            codename == "graceqlteue" || codename == "graceqlteusc" ||
            codename == "graceqltevzw"
        ) ||
        model != null && (
            model == "SAMSUNG-SM-N930A" || model == "SC-01J" || model == "SCV34" ||
                model == "SGH-N037" || model == "SM-N9300" || model == "SM-N930F" ||
                model == "SM-N930K" || model == "SM-N930L" || model == "SM-N930P" ||
                model == "SM-N930R4" || model == "SM-N930R6" || model == "SM-N930R7" ||
                model == "SM-N930S" || model == "SM-N930T" || model == "SM-N930U" ||
                model == "SM-N930V" || model == "SM-N930VL" || model == "SM-N930W8" ||
                model == "SM-N930X"
            )
    ) {
        return "Galaxy Note7"
    }
    if (codename != null && (
        codename == "maguro" ||
            codename == "toro" ||
            codename == "toroplus"
        ) || model != null && model == "Galaxy X"
    ) {
        return "Galaxy Nexus"
    }
    if (codename != null && (
        codename == "lt033g" ||
            codename == "lt03ltektt" ||
            codename == "lt03ltelgt" ||
            codename == "lt03lteskt" ||
            codename == "p4notelte" ||
            codename == "p4noteltektt" ||
            codename == "p4noteltelgt" ||
            codename == "p4notelteskt" ||
            codename == "p4noteltespr" ||
            codename == "p4notelteusc" ||
            codename == "p4noteltevzw" ||
            codename == "p4noterf" ||
            codename == "p4noterfktt" ||
            codename == "p4notewifi" ||
            codename == "p4notewifi43241any" ||
            codename == "p4notewifiany" ||
            codename == "p4notewifiktt" ||
            codename == "p4notewifiww"
        ) || model != null && (
            model == "GT-N8000" ||
                model == "GT-N8005" ||
                model == "GT-N8010" ||
                model == "GT-N8013" ||
                model == "GT-N8020" ||
                model == "SCH-I925" ||
                model == "SCH-I925U" ||
                model == "SHV-E230K" ||
                model == "SHV-E230L" ||
                model == "SHV-E230S" ||
                model == "SHW-M480K" ||
                model == "SHW-M480W" ||
                model == "SHW-M485W" ||
                model == "SHW-M486W" ||
                model == "SM-P601" ||
                model == "SM-P602" ||
                model == "SM-P605K" ||
                model == "SM-P605L" ||
                model == "SM-P605S" ||
                model == "SPH-P600"
            )
    ) {
        return "Galaxy Note 10.1"
    }
    if (codename != null && (
        codename == "SC-01G" ||
            codename == "SCL24" ||
            codename == "tbeltektt" ||
            codename == "tbeltelgt" ||
            codename == "tbelteskt" ||
            codename == "tblte" ||
            codename == "tblteatt" ||
            codename == "tbltecan" ||
            codename == "tbltechn" ||
            codename == "tbltespr" ||
            codename == "tbltetmo" ||
            codename == "tblteusc" ||
            codename == "tbltevzw"
        ) || model != null && (
            model == "SAMSUNG-SM-N915A" ||
                model == "SC-01G" ||
                model == "SCL24" ||
                model == "SM-N9150" ||
                model == "SM-N915F" ||
                model == "SM-N915FY" ||
                model == "SM-N915G" ||
                model == "SM-N915K" ||
                model == "SM-N915L" ||
                model == "SM-N915P" ||
                model == "SM-N915R4" ||
                model == "SM-N915S" ||
                model == "SM-N915T" ||
                model == "SM-N915T3" ||
                model == "SM-N915V" ||
                model == "SM-N915W8" ||
                model == "SM-N915X"
            )
    ) {
        return "Galaxy Note Edge"
    }
    if (codename != null && (
        codename == "v1a3g" ||
            codename == "v1awifi" ||
            codename == "v1awifikx" ||
            codename == "viennalte" ||
            codename == "viennalteatt" ||
            codename == "viennaltekx" ||
            codename == "viennaltevzw"
        ) || model != null && (
            model == "SAMSUNG-SM-P907A" ||
                model == "SM-P900" ||
                model == "SM-P901" ||
                model == "SM-P905" ||
                model == "SM-P905F0" ||
                model == "SM-P905M" ||
                model == "SM-P905V"
            )
    ) {
        return "Galaxy Note Pro 12.2"
    }
    if (codename != null && (
        codename == "tre3caltektt" ||
            codename == "tre3caltelgt" ||
            codename == "tre3calteskt" ||
            codename == "tre3g" ||
            codename == "trelte" ||
            codename == "treltektt" ||
            codename == "treltelgt" ||
            codename == "trelteskt" ||
            codename == "trhplte" ||
            codename == "trlte" ||
            codename == "trlteatt" ||
            codename == "trltecan" ||
            codename == "trltechn" ||
            codename == "trltechnzh" ||
            codename == "trltespr" ||
            codename == "trltetmo" ||
            codename == "trlteusc" ||
            codename == "trltevzw"
        ) || model != null && (
            model == "SAMSUNG-SM-N910A" ||
                model == "SM-N9100" ||
                model == "SM-N9106W" ||
                model == "SM-N9108V" ||
                model == "SM-N9109W" ||
                model == "SM-N910C" ||
                model == "SM-N910F" ||
                model == "SM-N910G" ||
                model == "SM-N910H" ||
                model == "SM-N910K" ||
                model == "SM-N910L" ||
                model == "SM-N910P" ||
                model == "SM-N910R4" ||
                model == "SM-N910S" ||
                model == "SM-N910T" ||
                model == "SM-N910T2" ||
                model == "SM-N910T3" ||
                model == "SM-N910U" ||
                model == "SM-N910V" ||
                model == "SM-N910W8" ||
                model == "SM-N910X" ||
                model == "SM-N916K" ||
                model == "SM-N916L" ||
                model == "SM-N916S"
            )
    ) {
        return "Galaxy Note4"
    }
    if (codename != null && (
        codename == "noblelte" ||
            codename == "noblelteacg" ||
            codename == "noblelteatt" ||
            codename == "nobleltebmc" ||
            codename == "nobleltechn" ||
            codename == "nobleltecmcc" ||
            codename == "nobleltehk" ||
            codename == "nobleltektt" ||
            codename == "nobleltelgt" ||
            codename == "nobleltelra" ||
            codename == "noblelteskt" ||
            codename == "nobleltespr" ||
            codename == "nobleltetmo" ||
            codename == "noblelteusc" ||
            codename == "nobleltevzw"
        ) || model != null && (
            model == "SAMSUNG-SM-N920A" ||
                model == "SM-N9200" ||
                model == "SM-N9208" ||
                model == "SM-N920C" ||
                model == "SM-N920F" ||
                model == "SM-N920G" ||
                model == "SM-N920I" ||
                model == "SM-N920K" ||
                model == "SM-N920L" ||
                model == "SM-N920P" ||
                model == "SM-N920R4" ||
                model == "SM-N920R6" ||
                model == "SM-N920R7" ||
                model == "SM-N920S" ||
                model == "SM-N920T" ||
                model == "SM-N920V" ||
                model == "SM-N920W8" ||
                model == "SM-N920X"
            )
    ) {
        return "Galaxy Note5"
    }
    if (codename != null && (
        codename == "SC-01J" ||
            codename == "SCV34" ||
            codename == "gracelte" ||
            codename == "graceltektt" ||
            codename == "graceltelgt" ||
            codename == "gracelteskt" ||
            codename == "graceqlteacg" ||
            codename == "graceqlteatt" ||
            codename == "graceqltebmc" ||
            codename == "graceqltechn" ||
            codename == "graceqltedcm" ||
            codename == "graceqltelra" ||
            codename == "graceqltespr" ||
            codename == "graceqltetfnvzw" ||
            codename == "graceqltetmo" ||
            codename == "graceqlteue" ||
            codename == "graceqlteusc" ||
            codename == "graceqltevzw"
        ) || model != null && (
            model == "SAMSUNG-SM-N930A" ||
                model == "SC-01J" ||
                model == "SCV34" ||
                model == "SGH-N037" ||
                model == "SM-N9300" ||
                model == "SM-N930F" ||
                model == "SM-N930K" ||
                model == "SM-N930L" ||
                model == "SM-N930P" ||
                model == "SM-N930R4" ||
                model == "SM-N930R6" ||
                model == "SM-N930R7" ||
                model == "SM-N930S" ||
                model == "SM-N930T" ||
                model == "SM-N930U" ||
                model == "SM-N930V" ||
                model == "SM-N930VL" ||
                model == "SM-N930W8" ||
                model == "SM-N930X"
            )
    ) {
        return "Galaxy Note7"
    }
    if (codename != null && (
        codename == "SC-01K" || codename == "SCV37" ||
            codename == "greatlte" || codename == "greatlteks" || codename == "greatqlte" ||
            codename == "greatqltechn" || codename == "greatqltecmcc" ||
            codename == "greatqltecs" || codename == "greatqlteue"
        ) ||
        model != null && (
            model == "SC-01K" || model == "SCV37" || model == "SM-N9500" ||
                model == "SM-N9508" || model == "SM-N950F" || model == "SM-N950N" ||
                model == "SM-N950U" || model == "SM-N950U1" || model == "SM-N950W" ||
                model == "SM-N950XN"
            )
    ) {
        return "Galaxy Note8"
    }
    if (codename != null && (
        codename == "o5lte" ||
            codename == "o5ltechn" ||
            codename == "o5prolte" ||
            codename == "on5ltemtr" ||
            codename == "on5ltetfntmo" ||
            codename == "on5ltetmo"
        ) || model != null && (
            model == "SM-G5500" ||
                model == "SM-G550FY" ||
                model == "SM-G550T" ||
                model == "SM-G550T1" ||
                model == "SM-G550T2" ||
                model == "SM-S550TL"
            )
    ) {
        return "Galaxy On5"
    }
    if (codename != null && (
        codename == "o7lte" ||
            codename == "o7ltechn" ||
            codename == "on7elte"
        ) || model != null && (
            model == "SM-G6000" ||
                model == "SM-G600F" ||
                model == "SM-G600FY"
            )
    ) {
        return "Galaxy On7"
    }
    if (codename != null && (
        codename == "GT-I9000" ||
            codename == "GT-I9000B" ||
            codename == "GT-I9000M" ||
            codename == "GT-I9000T" ||
            codename == "GT-I9003" ||
            codename == "GT-I9003L" ||
            codename == "GT-I9008L" ||
            codename == "GT-I9010" ||
            codename == "GT-I9018" ||
            codename == "GT-I9050" ||
            codename == "SC-02B" ||
            codename == "SCH-I500" ||
            codename == "SCH-S950C" ||
            codename == "SCH-i909" ||
            codename == "SGH-I897" ||
            codename == "SGH-T959V" ||
            codename == "SGH-T959W" ||
            codename == "SHW-M110S" ||
            codename == "SHW-M190S" ||
            codename == "SPH-D700" ||
            codename == "loganlte"
        ) || model != null && (
            model == "GT-I9000" ||
                model == "GT-I9000B" ||
                model == "GT-I9000M" ||
                model == "GT-I9000T" ||
                model == "GT-I9003" ||
                model == "GT-I9003L" ||
                model == "GT-I9008L" ||
                model == "GT-I9010" ||
                model == "GT-I9018" ||
                model == "GT-I9050" ||
                model == "GT-S7275" ||
                model == "SAMSUNG-SGH-I897" ||
                model == "SC-02B" ||
                model == "SCH-I500" ||
                model == "SCH-S950C" ||
                model == "SCH-i909" ||
                model == "SGH-T959V" ||
                model == "SGH-T959W" ||
                model == "SHW-M110S" ||
                model == "SHW-M190S" ||
                model == "SPH-D700"
            )
    ) {
        return "Galaxy S"
    }
    if (codename != null && (
        codename == "kylechn" ||
            codename == "kyleopen" ||
            codename == "kyletdcmcc"
        ) || model != null && (model == "GT-S7562" || model == "GT-S7568")
    ) {
        return "Galaxy S Duos"
    }
    if (codename != null && codename == "kyleprods" || model != null && (model == "GT-S7582" || model == "GT-S7582L")) {
        return "Galaxy S Duos2"
    }
    if (codename != null && codename == "vivalto3gvn" || model != null && model == "SM-G313HZ") {
        return "Galaxy S Duos3"
    }
    if (codename != null && (
        codename == "SC-03E" ||
            codename == "c1att" ||
            codename == "c1ktt" ||
            codename == "c1lgt" ||
            codename == "c1skt" ||
            codename == "d2att" ||
            codename == "d2can" ||
            codename == "d2cri" ||
            codename == "d2dcm" ||
            codename == "d2lteMetroPCS" ||
            codename == "d2lterefreshspr" ||
            codename == "d2ltetmo" ||
            codename == "d2mtr" ||
            codename == "d2spi" ||
            codename == "d2spr" ||
            codename == "d2tfnspr" ||
            codename == "d2tfnvzw" ||
            codename == "d2tmo" ||
            codename == "d2usc" ||
            codename == "d2vmu" ||
            codename == "d2vzw" ||
            codename == "d2xar" ||
            codename == "m0" ||
            codename == "m0apt" ||
            codename == "m0chn" ||
            codename == "m0cmcc" ||
            codename == "m0ctc" ||
            codename == "m0ctcduos" ||
            codename == "m0skt" ||
            codename == "m3" ||
            codename == "m3dcm"
        ) || model != null && (
            model == "GT-I9300" ||
                model == "GT-I9300T" ||
                model == "GT-I9305" ||
                model == "GT-I9305N" ||
                model == "GT-I9305T" ||
                model == "GT-I9308" ||
                model == "Gravity" ||
                model == "GravityQuad" ||
                model == "SAMSUNG-SGH-I747" ||
                model == "SC-03E" ||
                model == "SC-06D" ||
                model == "SCH-I535" ||
                model == "SCH-I535PP" ||
                model == "SCH-I939" ||
                model == "SCH-I939D" ||
                model == "SCH-L710" ||
                model == "SCH-R530C" ||
                model == "SCH-R530M" ||
                model == "SCH-R530U" ||
                model == "SCH-R530X" ||
                model == "SCH-S960L" ||
                model == "SCH-S968C" ||
                model == "SGH-I747M" ||
                model == "SGH-I748" ||
                model == "SGH-T999" ||
                model == "SGH-T999L" ||
                model == "SGH-T999N" ||
                model == "SGH-T999V" ||
                model == "SHV-E210K" ||
                model == "SHV-E210L" ||
                model == "SHV-E210S" ||
                model == "SHW-M440S" ||
                model == "SPH-L710" ||
                model == "SPH-L710T"
            )
    ) {
        return "Galaxy S3"
    }
    if (codename != null && (
        codename == "golden" ||
            codename == "goldenlteatt" ||
            codename == "goldenltebmc" ||
            codename == "goldenltevzw" ||
            codename == "goldenve3g"
        ) || model != null && (
            model == "GT-I8190" ||
                model == "GT-I8190L" ||
                model == "GT-I8190N" ||
                model == "GT-I8190T" ||
                model == "GT-I8200L" ||
                model == "SAMSUNG-SM-G730A" ||
                model == "SM-G730V" ||
                model == "SM-G730W8"
            )
    ) {
        return "Galaxy S3 Mini"
    }
    if (codename != null && (codename == "goldenve3g" || codename == "goldenvess3g") || model != null && (
        model == "GT-I8200" ||
            model == "GT-I8200N" ||
            model == "GT-I8200Q"
        )
    ) {
        return "Galaxy S3 Mini Value Edition"
    }
    if (codename != null && (
        codename == "s3ve3g" ||
            codename == "s3ve3gdd" ||
            codename == "s3ve3gds" ||
            codename == "s3ve3gdsdd"
        ) || model != null && (
            model == "GT-I9300I" ||
                model == "GT-I9301I" ||
                model == "GT-I9301Q"
            )
    ) {
        return "Galaxy S3 Neo"
    }
    if (codename != null && (
        codename == "SC-04E" ||
            codename == "ja3g" ||
            codename == "ja3gduosctc" ||
            codename == "jaltektt" ||
            codename == "jaltelgt" ||
            codename == "jalteskt" ||
            codename == "jflte" ||
            codename == "jflteMetroPCS" ||
            codename == "jflteaio" ||
            codename == "jflteatt" ||
            codename == "jfltecan" ||
            codename == "jfltecri" ||
            codename == "jfltecsp" ||
            codename == "jfltelra" ||
            codename == "jflterefreshspr" ||
            codename == "jfltespr" ||
            codename == "jfltetfnatt" ||
            codename == "jfltetfntmo" ||
            codename == "jfltetmo" ||
            codename == "jflteusc" ||
            codename == "jfltevzw" ||
            codename == "jfltevzwpp" ||
            codename == "jftdd" ||
            codename == "jfvelte" ||
            codename == "jfwifi" ||
            codename == "jsglte" ||
            codename == "ks01lte" ||
            codename == "ks01ltektt" ||
            codename == "ks01ltelgt"
        ) || model != null && (
            model == "GT-I9500" ||
                model == "GT-I9505" ||
                model == "GT-I9505X" ||
                model == "GT-I9506" ||
                model == "GT-I9507" ||
                model == "GT-I9507V" ||
                model == "GT-I9508" ||
                model == "GT-I9508C" ||
                model == "GT-I9508V" ||
                model == "GT-I9515" ||
                model == "GT-I9515L" ||
                model == "SAMSUNG-SGH-I337" ||
                model == "SAMSUNG-SGH-I337Z" ||
                model == "SC-04E" ||
                model == "SCH-I545" ||
                model == "SCH-I545L" ||
                model == "SCH-I545PP" ||
                model == "SCH-I959" ||
                model == "SCH-R970" ||
                model == "SCH-R970C" ||
                model == "SCH-R970X" ||
                model == "SGH-I337M" ||
                model == "SGH-M919" ||
                model == "SGH-M919N" ||
                model == "SGH-M919V" ||
                model == "SGH-S970G" ||
                model == "SHV-E300K" ||
                model == "SHV-E300L" ||
                model == "SHV-E300S" ||
                model == "SHV-E330K" ||
                model == "SHV-E330L" ||
                model == "SM-S975L" ||
                model == "SPH-L720" ||
                model == "SPH-L720T"
            )
    ) {
        return "Galaxy S4"
    }
    if (codename != null && (
        codename == "serrano3g" ||
            codename == "serranods" ||
            codename == "serranolte" ||
            codename == "serranoltebmc" ||
            codename == "serranoltektt" ||
            codename == "serranoltekx" ||
            codename == "serranoltelra" ||
            codename == "serranoltespr" ||
            codename == "serranolteusc" ||
            codename == "serranoltevzw" ||
            codename == "serranove3g" ||
            codename == "serranovelte" ||
            codename == "serranovolteatt"
        ) || model != null && (
            model == "GT-I9190" ||
                model == "GT-I9192" ||
                model == "GT-I9192I" ||
                model == "GT-I9195" ||
                model == "GT-I9195I" ||
                model == "GT-I9195L" ||
                model == "GT-I9195T" ||
                model == "GT-I9195X" ||
                model == "GT-I9197" ||
                model == "SAMSUNG-SGH-I257" ||
                model == "SCH-I435" ||
                model == "SCH-I435L" ||
                model == "SCH-R890" ||
                model == "SGH-I257M" ||
                model == "SHV-E370D" ||
                model == "SHV-E370K" ||
                model == "SPH-L520"
            )
    ) {
        return "Galaxy S4 Mini"
    }
    if (codename != null && (
        codename == "SC-01L" || codename == "SCV40" ||
            codename == "crownlte" || codename == "crownlteks" || codename == "crownqltechn" ||
            codename == "crownqltecs" || codename == "crownqltesq" || codename == "crownqlteue"
        ) ||
        model != null && (
            model == "SC-01L" || model == "SCV40" || model == "SM-N9600" ||
                model == "SM-N960F" || model == "SM-N960N" || model == "SM-N960U" ||
                model == "SM-N960U1" || model == "SM-N960W"
            )
    ) {
        return "Galaxy Note9"
    }
    if (codename != null && (
        codename == "SC-03L" || codename == "SCV41" || codename == "beyond1" ||
            codename == "beyond1q"
        ) ||
        model != null && (
            model == "SC-03L" || model == "SCV41" || model == "SM-G9730" ||
                model == "SM-G9738" || model == "SM-G973C" || model == "SM-G973F" ||
                model == "SM-G973N" || model == "SM-G973U" || model == "SM-G973U1" || model == "SM-G973W"
            )
    ) {
        return "Galaxy S10"
    }
    if (codename != null && (
        codename == "SC-04L" || codename == "SCV42" || codename == "beyond2" ||
            codename == "beyond2q"
        ) || model != null && (
            model == "SC-04L" || model == "SCV42" ||
                model == "SM-G9750" || model == "SM-G9758" || model == "SM-G975F" ||
                model == "SM-G975N" || model == "SM-G975U" || model == "SM-G975U1" || model == "SM-G975W"
            )
    ) {
        return "Galaxy S10+"
    }
    if (codename != null && (codename == "beyond0" || codename == "beyond0q") ||
        model != null && (
            model == "SM-G9700" || model == "SM-G9708" || model == "SM-G970F" ||
                model == "SM-G970N" || model == "SM-G970U" || model == "SM-G970U1" ||
                model == "SM-G970W"
            )
    ) {
        return "Galaxy S10e"
    }
    if (codename != null && (
        codename == "SC-04F" || codename == "SCL23" || codename == "k3g" ||
            codename == "klte" || codename == "klteMetroPCS" || codename == "klteacg" ||
            codename == "klteaio" || codename == "klteatt" || codename == "kltecan" ||
            codename == "klteduoszn" || codename == "kltektt" || codename == "kltelgt" ||
            codename == "kltelra" || codename == "klteskt" || codename == "kltespr" ||
            codename == "kltetfnvzw" || codename == "kltetmo" || codename == "klteusc" ||
            codename == "kltevzw" || codename == "kwifi" || codename == "lentisltektt" ||
            codename == "lentisltelgt" || codename == "lentislteskt"
        ) ||
        model != null && (
            model == "SAMSUNG-SM-G900A" || model == "SAMSUNG-SM-G900AZ" ||
                model == "SC-04F" || model == "SCL23" || model == "SM-G9006W" || model == "SM-G9008W" ||
                model == "SM-G9009W" || model == "SM-G900F" || model == "SM-G900FQ" ||
                model == "SM-G900H" || model == "SM-G900I" || model == "SM-G900K" ||
                model == "SM-G900L" || model == "SM-G900M" || model == "SM-G900MD" ||
                model == "SM-G900P" || model == "SM-G900R4" || model == "SM-G900R6" ||
                model == "SM-G900R7" || model == "SM-G900S" || model == "SM-G900T" ||
                model == "SM-G900T1" || model == "SM-G900T3" || model == "SM-G900T4" ||
                model == "SM-G900V" || model == "SM-G900W8" || model == "SM-G900X" ||
                model == "SM-G906K" || model == "SM-G906L" || model == "SM-G906S" || model == "SM-S903VL"
            )
    ) {
        return "Galaxy S5"
    }
    if (codename != null && (codename == "s5neolte" || codename == "s5neoltecan") ||
        model != null && (model == "SM-G903F" || model == "SM-G903M" || model == "SM-G903W")
    ) {
        return "Galaxy S5 Neo"
    }
    if (codename != null && (
        codename == "SC-05G" || codename == "zeroflte" ||
            codename == "zeroflteacg" || codename == "zeroflteaio" || codename == "zeroflteatt" ||
            codename == "zerofltebmc" || codename == "zerofltechn" || codename == "zerofltectc" ||
            codename == "zerofltektt" || codename == "zerofltelgt" || codename == "zerofltelra" ||
            codename == "zerofltemtr" || codename == "zeroflteskt" || codename == "zerofltespr" ||
            codename == "zerofltetfnvzw" || codename == "zerofltetmo" ||
            codename == "zeroflteusc" || codename == "zerofltevzw"
        ) ||
        model != null && (
            model == "SAMSUNG-SM-G920A" || model == "SAMSUNG-SM-G920AZ" ||
                model == "SC-05G" || model == "SM-G9200" || model == "SM-G9208" ||
                model == "SM-G9209" || model == "SM-G920F" || model == "SM-G920I" ||
                model == "SM-G920K" || model == "SM-G920L" || model == "SM-G920P" ||
                model == "SM-G920R4" || model == "SM-G920R6" || model == "SM-G920R7" ||
                model == "SM-G920S" || model == "SM-G920T" || model == "SM-G920T1" ||
                model == "SM-G920V" || model == "SM-G920W8" || model == "SM-G920X" ||
                model == "SM-S906L" || model == "SM-S907VL"
            )
    ) {
        return "Galaxy S6"
    }
    if (codename != null && (
        codename == "404SC" || codename == "SC-04G" || codename == "SCV31" ||
            codename == "zerolte" || codename == "zerolteacg" || codename == "zerolteatt" ||
            codename == "zeroltebmc" || codename == "zeroltechn" || codename == "zeroltektt" ||
            codename == "zeroltelra" || codename == "zerolteskt" || codename == "zeroltespr" ||
            codename == "zeroltetmo" || codename == "zerolteusc" || codename == "zeroltevzw"
        ) ||
        model != null && (
            model == "404SC" || model == "SAMSUNG-SM-G925A" || model == "SC-04G" ||
                model == "SCV31" || model == "SM-G9250" || model == "SM-G925I" || model == "SM-G925K" ||
                model == "SM-G925P" || model == "SM-G925R4" || model == "SM-G925R6" ||
                model == "SM-G925R7" || model == "SM-G925S" || model == "SM-G925T" ||
                model == "SM-G925V" || model == "SM-G925W8" || model == "SM-G925X"
            )
    ) {
        return "Galaxy S6 Edge"
    }
    if (codename != null && (
        codename == "zenlte" || codename == "zenlteatt" ||
            codename == "zenltebmc" || codename == "zenltechn" || codename == "zenltektt" ||
            codename == "zenltekx" || codename == "zenltelgt" || codename == "zenlteskt" ||
            codename == "zenltespr" || codename == "zenltetmo" || codename == "zenlteusc" ||
            codename == "zenltevzw"
        ) ||
        model != null && (
            model == "SAMSUNG-SM-G928A" || model == "SM-G9280" ||
                model == "SM-G9287C" || model == "SM-G928C" || model == "SM-G928G" ||
                model == "SM-G928I" || model == "SM-G928K" || model == "SM-G928L" ||
                model == "SM-G928N0" || model == "SM-G928P" || model == "SM-G928R4" ||
                model == "SM-G928S" || model == "SM-G928T" || model == "SM-G928V" ||
                model == "SM-G928W8" || model == "SM-G928X"
            )
    ) {
        return "Galaxy S6 Edge+"
    }
    if (codename != null && (
        codename == "herolte" || codename == "heroltebmc" ||
            codename == "heroltektt" || codename == "heroltelgt" || codename == "herolteskt" ||
            codename == "heroqlteacg" || codename == "heroqlteaio" || codename == "heroqlteatt" ||
            codename == "heroqltecctvzw" || codename == "heroqltechn" || codename == "heroqltelra" ||
            codename == "heroqltemtr" || codename == "heroqltespr" || codename == "heroqltetfnvzw" ||
            codename == "heroqltetmo" || codename == "heroqlteue" || codename == "heroqlteusc" ||
            codename == "heroqltevzw"
        ) ||
        model != null && (
            model == "SAMSUNG-SM-G930A" || model == "SAMSUNG-SM-G930AZ" ||
                model == "SM-G9300" || model == "SM-G9308" || model == "SM-G930F" || model == "SM-G930K" ||
                model == "SM-G930L" || model == "SM-G930P" || model == "SM-G930R4" || model == "SM-G930R6" ||
                model == "SM-G930R7" || model == "SM-G930S" || model == "SM-G930T" || model == "SM-G930T1" ||
                model == "SM-G930U" || model == "SM-G930V" || model == "SM-G930VC" || model == "SM-G930VL" ||
                model == "SM-G930W8" || model == "SM-G930X"
            )
    ) {
        return "Galaxy S7"
    }
    if (codename != null && (
        codename == "SC-02H" || codename == "SCV33" ||
            codename == "hero2lte" || codename == "hero2ltebmc" || codename == "hero2ltektt" ||
            codename == "hero2lteskt" || codename == "hero2qlteatt" ||
            codename == "hero2qltecctvzw" || codename == "hero2qltespr" ||
            codename == "hero2qltetmo" || codename == "hero2qlteusc" || codename == "hero2qltevzw"
        ) ||
        model != null && (
            model == "SAMSUNG-SM-G935A" || model == "SC-02H" ||
                model == "SCV33" || model == "SM-G935K" || model == "SM-G935P" ||
                model == "SM-G935R4" || model == "SM-G935S" || model == "SM-G935T" ||
                model == "SM-G935V" || model == "SM-G935VC" || model == "SM-G935W8" ||
                model == "SM-G935X"
            )
    ) {
        return "Galaxy S7 Edge"
    }
    if (codename != null && (
        codename == "SC-02J" || codename == "SCV36" ||
            codename == "dreamlte" || codename == "dreamlteks" || codename == "dreamqltecan" ||
            codename == "dreamqltechn" || codename == "dreamqltecmcc" || codename == "dreamqltesq" ||
            codename == "dreamqlteue"
        ) ||
        model != null && (
            model == "SC-02J" || model == "SCV36" || model == "SM-G9500" ||
                model == "SM-G9508" || model == "SM-G950F" || model == "SM-G950N" ||
                model == "SM-G950U" || model == "SM-G950U1" || model == "SM-G950W"
            )
    ) {
        return "Galaxy S8"
    }
    if (codename != null && (
        codename == "SC-03J" || codename == "SCV35" ||
            codename == "dream2lte" || codename == "dream2lteks" || codename == "dream2qltecan" ||
            codename == "dream2qltechn" || codename == "dream2qltesq" || codename == "dream2qlteue"
        ) ||
        model != null && (
            model == "SC-03J" || model == "SCV35" || model == "SM-G9550" ||
                model == "SM-G955F" || model == "SM-G955N" || model == "SM-G955U" ||
                model == "SM-G955U1" || model == "SM-G955W"
            )
    ) {
        return "Galaxy S8+"
    }
    if (codename != null && (
        codename == "SC-02K" || codename == "SCV38" ||
            codename == "starlte" || codename == "starlteks" || codename == "starqltechn" ||
            codename == "starqltecmcc" || codename == "starqltecs" || codename == "starqltesq" ||
            codename == "starqlteue"
        ) ||
        model != null && (
            model == "SC-02K" || model == "SCV38" || model == "SM-G9600" ||
                model == "SM-G9608" || model == "SM-G960F" || model == "SM-G960N" ||
                model == "SM-G960U" || model == "SM-G960U1" || model == "SM-G960W"
            )
    ) {
        return "Galaxy S9"
    }
    if (codename != null && (
        codename == "SC-03K" || codename == "SCV39" ||
            codename == "star2lte" || codename == "star2lteks" || codename == "star2qltechn" ||
            codename == "star2qltecs" || codename == "star2qltesq" || codename == "star2qlteue"
        ) ||
        model != null && (
            model == "SC-03K" || model == "SCV39" || model == "SM-G9650" ||
                model == "SM-G965F" || model == "SM-G965N" || model == "SM-G965U" ||
                model == "SM-G965U1" || model == "SM-G965W"
            )
    ) {
        return "Galaxy S9+"
    }
    if (codename != null && (
        codename == "GT-P7500" ||
            codename == "GT-P7500D" ||
            codename == "GT-P7503" ||
            codename == "GT-P7510" ||
            codename == "SC-01D" ||
            codename == "SCH-I905" ||
            codename == "SGH-T859" ||
            codename == "SHW-M300W" ||
            codename == "SHW-M380K" ||
            codename == "SHW-M380S" ||
            codename == "SHW-M380W"
        ) || model != null && (
            model == "GT-P7500" ||
                model == "GT-P7500D" ||
                model == "GT-P7503" ||
                model == "GT-P7510" ||
                model == "SC-01D" ||
                model == "SCH-I905" ||
                model == "SGH-T859" ||
                model == "SHW-M300W" ||
                model == "SHW-M380K" ||
                model == "SHW-M380S" ||
                model == "SHW-M380W"
            )
    ) {
        return "Galaxy Tab 10.1"
    }
    if (codename != null && (
        codename == "GT-P6200" ||
            codename == "GT-P6200L" ||
            codename == "GT-P6201" ||
            codename == "GT-P6210" ||
            codename == "GT-P6211" ||
            codename == "SC-02D" ||
            codename == "SGH-T869" ||
            codename == "SHW-M430W"
        ) || model != null && (
            model == "GT-P6200" ||
                model == "GT-P6200L" ||
                model == "GT-P6201" ||
                model == "GT-P6210" ||
                model == "GT-P6211" ||
                model == "SC-02D" ||
                model == "SGH-T869" ||
                model == "SHW-M430W"
            )
    ) {
        return "Galaxy Tab 7.0 Plus"
    }
    if (codename != null && (
        codename == "gteslteatt" ||
            codename == "gtesltebmc" ||
            codename == "gtesltelgt" ||
            codename == "gteslteskt" ||
            codename == "gtesltetmo" ||
            codename == "gtesltetw" ||
            codename == "gtesltevzw" ||
            codename == "gtesqltespr" ||
            codename == "gtesqlteusc"
        ) || model != null && (
            model == "SAMSUNG-SM-T377A" ||
                model == "SM-T375L" ||
                model == "SM-T375S" ||
                model == "SM-T3777" ||
                model == "SM-T377P" ||
                model == "SM-T377R4" ||
                model == "SM-T377T" ||
                model == "SM-T377V" ||
                model == "SM-T377W"
            )
    ) {
        return "Galaxy Tab E 8.0"
    }
    if (codename != null && (
        codename == "gtel3g" ||
            codename == "gtelltevzw" ||
            codename == "gtelwifi" ||
            codename == "gtelwifichn" ||
            codename == "gtelwifiue"
        ) || model != null && (
            model == "SM-T560" ||
                model == "SM-T560NU" ||
                model == "SM-T561" ||
                model == "SM-T561M" ||
                model == "SM-T561Y" ||
                model == "SM-T562" ||
                model == "SM-T567V"
            )
    ) {
        return "Galaxy Tab E 9.6"
    }
    if (codename != null && (
        codename == "403SC" ||
            codename == "degas2wifi" ||
            codename == "degas2wifibmwchn" ||
            codename == "degas3g" ||
            codename == "degaslte" ||
            codename == "degasltespr" ||
            codename == "degasltevzw" ||
            codename == "degasvelte" ||
            codename == "degasveltechn" ||
            codename == "degaswifi" ||
            codename == "degaswifibmwzc" ||
            codename == "degaswifidtv" ||
            codename == "degaswifiopenbnn" ||
            codename == "degaswifiue"
        ) || model != null && (
            model == "403SC" ||
                model == "SM-T230" ||
                model == "SM-T230NT" ||
                model == "SM-T230NU" ||
                model == "SM-T230NW" ||
                model == "SM-T230NY" ||
                model == "SM-T230X" ||
                model == "SM-T231" ||
                model == "SM-T232" ||
                model == "SM-T235" ||
                model == "SM-T235Y" ||
                model == "SM-T237P" ||
                model == "SM-T237V" ||
                model == "SM-T239" ||
                model == "SM-T2397" ||
                model == "SM-T239C" ||
                model == "SM-T239M"
            )
    ) {
        return "Galaxy Tab4 7.0"
    }
    if (codename != null && (
        codename == "gvlte" ||
            codename == "gvlteatt" ||
            codename == "gvltevzw" ||
            codename == "gvltexsp" ||
            codename == "gvwifijpn" ||
            codename == "gvwifiue"
        ) || model != null && (
            model == "SAMSUNG-SM-T677A" ||
                model == "SM-T670" ||
                model == "SM-T677" ||
                model == "SM-T677V"
            )
    ) {
        return "Galaxy View"
    }
    if (codename != null && codename == "manta") {
        return "Nexus 10"
    }
    // ----------------------------------------------------------------------------
    // Sony
    if (codename != null && (codename == "D2104" || codename == "D2105") || model != null && (model == "D2104" || model == "D2105")) {
        return "Xperia E1 dual"
    }
    if (codename != null && (
        codename == "D2202" ||
            codename == "D2203" ||
            codename == "D2206" ||
            codename == "D2243"
        ) || model != null && (
            model == "D2202" ||
                model == "D2203" ||
                model == "D2206" ||
                model == "D2243"
            )
    ) {
        return "Xperia E3"
    }
    if (codename != null && (
        codename == "E5603" ||
            codename == "E5606" ||
            codename == "E5653"
        ) || model != null && (
            model == "E5603" ||
                model == "E5606" ||
                model == "E5653"
            )
    ) {
        return "Xperia M5"
    }
    if (codename != null && (
        codename == "E5633" ||
            codename == "E5643" ||
            codename == "E5663"
        ) || model != null && (
            model == "E5633" ||
                model == "E5643" ||
                model == "E5663"
            )
    ) {
        return "Xperia M5 Dual"
    }
    if (codename != null && codename == "LT26i" || model != null && model == "LT26i") {
        return "Xperia S"
    }
    if (codename != null && (
        codename == "D5303" ||
            codename == "D5306" ||
            codename == "D5316" ||
            codename == "D5316N" ||
            codename == "D5322"
        ) || model != null && (
            model == "D5303" ||
                model == "D5306" ||
                model == "D5316" ||
                model == "D5316N" ||
                model == "D5322"
            )
    ) {
        return "Xperia T2 Ultra"
    }
    if (codename != null && codename == "txs03" || model != null && (model == "SGPT12" || model == "SGPT13")) {
        return "Xperia Tablet S"
    }
    if (codename != null && (
        codename == "SGP311" ||
            codename == "SGP312" ||
            codename == "SGP321" ||
            codename == "SGP351"
        ) || model != null && (
            model == "SGP311" ||
                model == "SGP312" ||
                model == "SGP321" ||
                model == "SGP351"
            )
    ) {
        return "Xperia Tablet Z"
    }
    if (codename != null && (
        codename == "D6502" ||
            codename == "D6503" ||
            codename == "D6543" ||
            codename == "SO-03F"
        ) || model != null && (
            model == "D6502" ||
                model == "D6503" ||
                model == "D6543" ||
                model == "SO-03F"
            )
    ) {
        return "Xperia Z2"
    }
    if (codename != null && (
        codename == "802SO" || codename == "J8110" || codename == "J8170" ||
            codename == "J9110" || codename == "J9180" || codename == "SO-03L" || codename == "SOV40"
        ) ||
        model != null && (
            model == "802SO" || model == "J8110" || model == "J8170" ||
                model == "J9110" || model == "J9180" || model == "SO-03L" || model == "SOV40"
            )
    ) {
        return "Xperia 1"
    }
    if (codename != null && (
        codename == "I3113" || codename == "I3123" || codename == "I4113" ||
            codename == "I4193"
        ) ||
        model != null && (
            model == "I3113" || model == "I3123" || model == "I4113" ||
                model == "I4193"
            )
    ) {
        return "Xperia 10"
    }
    if (codename != null && (
        codename == "I3213" || codename == "I3223" || codename == "I4213" ||
            codename == "I4293"
        ) ||
        model != null && (
            model == "I3213" || model == "I3223" || model == "I4213" ||
                model == "I4293"
            )
    ) {
        return "Xperia 10 Plus"
    }
    if (codename != null && (
        codename == "702SO" || codename == "H8216" || codename == "H8266" ||
            codename == "H8276" || codename == "H8296" || codename == "SO-03K" || codename == "SOV37"
        ) ||
        model != null && (
            model == "702SO" || model == "H8216" || model == "H8266" ||
                model == "H8276" || model == "H8296" || model == "SO-03K" || model == "SOV37"
            )
    ) {
        return "Xperia XZ2"
    }
    if (codename != null && (
        codename == "SGP311" ||
            codename == "SGP312" ||
            codename == "SGP321" ||
            codename == "SGP351"
        ) || model != null && (
            model == "SGP311" ||
                model == "SGP312" ||
                model == "SGP321" ||
                model == "SGP351"
            )
    ) {
        return "Xperia Tablet Z"
    }
    if (codename != null && codename == "txs03" || model != null && (model == "SGPT12" || model == "SGPT13")) {
        return "Xperia Tablet S"
    }
    if (codename != null && (
        codename == "H8116" || codename == "H8166" || codename == "SO-04K" ||
            codename == "SOV38"
        ) ||
        model != null && (model == "H8116" || model == "H8166" || model == "SO-04K" || model == "SOV38")
    ) {
        return "Xperia XZ2 Premium"
    }
    if (codename != null && (
        codename == "401SO" ||
            codename == "D6603" ||
            codename == "D6616" ||
            codename == "D6643" ||
            codename == "D6646" ||
            codename == "D6653" ||
            codename == "SO-01G" ||
            codename == "SOL26" ||
            codename == "leo"
        ) || model != null && (
            model == "401SO" ||
                model == "D6603" ||
                model == "D6616" ||
                model == "D6643" ||
                model == "D6646" ||
                model == "D6653" ||
                model == "SO-01G" ||
                model == "SOL26"
            )
    ) {
        return "Xperia Z3"
    }
    if (codename != null && (
        codename == "402SO" ||
            codename == "SO-03G" ||
            codename == "SOV31"
        ) || model != null && (
            model == "402SO" ||
                model == "SO-03G" ||
                model == "SOV31"
            )
    ) {
        return "Xperia Z4"
    }
    if (codename != null && (
        codename == "E5803" ||
            codename == "E5823" ||
            codename == "SO-02H"
        ) || model != null && (
            model == "E5803" ||
                model == "E5823" ||
                model == "SO-02H"
            )
    ) {
        return "Xperia Z5 Compact"
    }
    if (codename != null && (
        codename == "801SO" || codename == "H8416" ||
            codename == "H9436" || codename == "H9493" || codename == "SO-01L" || codename == "SOV39"
        ) ||
        model != null && (
            model == "801SO" || model == "H8416" || model == "H9436" ||
                model == "H9493" || model == "SO-01L" || model == "SOV39"
            )
    ) {
        return "Xperia XZ3"
    }
    // ----------------------------------------------------------------------------
    // Sony Ericsson
    if (codename != null && (codename == "LT26i" || codename == "SO-02D") || model != null && (model == "LT26i" || model == "SO-02D")) {
        return "Xperia S"
    }
    return if (codename != null && (
        codename == "SGP311" ||
            codename == "SGP321" ||
            codename == "SGP341" ||
            codename == "SO-03E"
        ) || model != null && (
            model == "SGP311" ||
                model == "SGP321" ||
                model == "SGP341" ||
                model == "SO-03E"
            )
    ) {
        "Xperia Tablet Z"
    } else fallback
}
