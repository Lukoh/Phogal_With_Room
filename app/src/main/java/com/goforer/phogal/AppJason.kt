package com.goforer.phogal

import kotlinx.serialization.json.Json

/**
 * By default, Kotlinx Serialization throws an [UnknownKeyException] and crashes the app
 * when it encounters a key in the JSON data that is not defined in your data class (DTO).
 * This means the moment the backend adds a new field, it can trigger a catastrophic app crash.
 *
 * Setting `ignoreUnknownKeys = true` allows the parser to gracefully ignore any undefined keys
 * and proceed with the parsing.
 *
 * To maintain a consistent JSON parsing policy across the entire app, this configuration
 * is declared as a singleton `object`.
 */
object AppJson {
    val mapper = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
}