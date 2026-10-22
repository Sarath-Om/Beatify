package com.example.services

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Honest ContentBlockingService for Synapse Music (Section 6).
 *
 * Architectural Note:
 * Standard uBlock Origin Lite is a Chromium/WebExtension content blocker and cannot run as a
 * native Android ExoPlayer audio-stream plugin. In Synapse Music, this service provides:
 * 1. Network request filtering for remote artwork, lyrics, and audio stream URLs against known
 *    ad/tracker/telemetry domains (EasyList / EasyPrivacy / Peter Lowe compatible rules).
 * 2. Transparent reporting of active filter rules, blocked telemetry requests, and platform scope.
 */
data class ContentProtectionReport(
    val isEnabled: Boolean = true,
    val activeFilterCount: Int = 0,
    val blockedRequestsCount: Int = 0,
    val customBlockedDomains: List<String> = emptyList(),
    val lastBlockedHost: String? = null,
    val engineModeLabel: String = "uBO-Lite Compatible Network Filter (Artwork & Stream Telemetry)",
    val platformDisclosure: String = "uBlock Origin Lite is a browser WebExtension and cannot run inside AndroidX Media3 ExoPlayer directly. Synapse Music applies uBO-Lite-compatible domain & URL parameter rules to remote stream, artwork, and metadata network requests."
)

class ContentBlockingService {

    private val bundledBlockedDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "ads.youtube.com",
        "pagead2.googlesyndication.com",
        "analytics.google.com",
        "app-measurement.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "ads-twitter.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "scorecardresearch.com",
        "quantserve.com",
        "zedo.com",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "flurry.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "omtrdc.net",
        "demdex.net",
        "krxd.net",
        "bluekai.com",
        "exelator.com",
        "agkn.com",
        "mathtag.com",
        "rlcdn.com",
        "tapad.com",
        "moatads.com",
        "iasds01.com",
        "doubleverify.com",
        "adsrvr.org",
        "advertising.com",
        "smartadserver.com",
        "openx.net",
        "indexexchange.com",
        "casalemedia.com",
        "yieldmo.com",
        "teads.tv",
        "sharethrough.com",
        "triplelift.com",
        "spotxchange.com",
        "springserve.com",
        "innovid.com",
        "audio.adswizz.com",
        "delivery.adswizz.com",
        "synchrobox.adswizz.com",
        "tracking.tritondigital.com",
        "playerservices.streamtheworld.com/api/ads"
    )

    private val trackingQueryParameters = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "gclid",
        "msclkid",
        "mc_cid",
        "mc_eid",
        "igshid",
        "si"
    )

    private val customDomains = mutableSetOf<String>()
    private var enabled: Boolean = true
    private var blockedCount: Int = 0
    private var lastBlockedHost: String? = null

    private val _report = MutableStateFlow(buildReport())
    val report: StateFlow<ContentProtectionReport> = _report.asStateFlow()

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        _report.value = buildReport()
    }

    fun isEnabled(): Boolean = enabled

    fun addCustomRule(domainOrRule: String): Boolean {
        val cleaned = domainOrRule.trim()
            .lowercase()
            .removePrefix("||")
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("^")
            .trimEnd('/')
        if (cleaned.isBlank() || !cleaned.contains(".")) return false
        val added = customDomains.add(cleaned)
        if (added) {
            _report.value = buildReport()
        }
        return added
    }

    fun removeCustomRule(domain: String) {
        if (customDomains.remove(domain.lowercase().trim())) {
            _report.value = buildReport()
        }
    }

    /**
     * Evaluates whether a network request (for remote stream, artwork, or metadata) should be blocked.
     */
    fun shouldBlockRequest(url: String): Boolean {
        if (!enabled) return false
        val host = extractHost(url) ?: return false
        val allRules = bundledBlockedDomains + customDomains
        val matched = allRules.any { rule ->
            host == rule || host.endsWith(".$rule")
        }
        if (matched) {
            blockedCount += 1
            lastBlockedHost = host
            _report.value = buildReport()
        }
        return matched
    }

    /**
     * Strips known tracking parameters from stream or artwork URLs before passing to ExoPlayer/Coil.
     */
    fun sanitizeMediaUrl(url: String): String {
        if (!enabled || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return url
        }
        return try {
            val uri = Uri.parse(url)
            val queryNames = uri.queryParameterNames
            if (queryNames.isEmpty()) return url
            val builder = uri.buildUpon().clearQuery()
            var strippedAny = false
            for (name in queryNames) {
                if (trackingQueryParameters.contains(name.lowercase())) {
                    strippedAny = true
                } else {
                    uri.getQueryParameters(name).forEach { valStr ->
                        builder.appendQueryParameter(name, valStr)
                    }
                }
            }
            if (strippedAny) {
                blockedCount += 1
                _report.value = buildReport()
            }
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }

    private fun extractHost(url: String): String? {
        return try {
            val normalized = if (url.contains("://")) url else "https://$url"
            java.net.URI(normalized).host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildReport(): ContentProtectionReport {
        return ContentProtectionReport(
            isEnabled = enabled,
            activeFilterCount = bundledBlockedDomains.size + trackingQueryParameters.size + customDomains.size,
            blockedRequestsCount = blockedCount,
            customBlockedDomains = customDomains.sorted(),
            lastBlockedHost = lastBlockedHost
        )
    }
}
