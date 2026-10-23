package com.example.services

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * Diagnostic record for a matched request filter rule (Section 6).
 * Never stores cookies, authorization headers, or personal search query strings.
 */
data class FilterDiagnosticEntry(
    val ruleId: String,
    val category: String,
    val normalizedHost: String,
    val action: String, // "BLOCKED" or "ALLOWED_ESSENTIAL"
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Honest ContentProtectionReport for Beatify (Section 6).
 */
data class ContentProtectionReport(
    val isEnabled: Boolean = true,
    val activeFilterCount: Int = 0,
    val allowlistRuleCount: Int = 0,
    val blockedRequestsCount: Int = 0,
    val allowedEssentialCount: Int = 0,
    val customBlockedDomains: List<String> = emptyList(),
    val lastBlockedHost: String? = null,
    val lastMatchedRuleId: String? = null,
    val recentDiagnostics: List<FilterDiagnosticEntry> = emptyList(),
    val filterSourceStatus: String = "Active • Maintained Host & Path Rule Engine",
    val engineModeLabel: String = "Beatify Domain & WebView Request Filter (music.youtube.com + Stream Telemetry)",
    val platformDisclosure: String = "Beatify uses a dedicated URL & WebViewClient request-filtering engine with strict domain-boundary matching and an explicit allowlist for essential authentication, artwork, and Media3/InnerTube playback endpoints. It does not claim to embed the Chromium uBlock Origin Lite browser extension."
)

/**
 * Modular Request-Filtering Service for `https://music.youtube.com/` WebView requests
 * and remote media/artwork telemetry (Section 6).
 */
class ContentBlockingService {

    data class FilterRule(
        val id: String,
        val hostPattern: String,
        val pathPrefix: String? = null,
        val category: String
    )

    // Explicit allowlist for essential authentication, playback, artwork, and API endpoints (Section 6)
    private val essentialAllowlistRules = listOf(
        FilterRule("allow_ytm_player_api", "music.youtube.com", "/youtubei/v1/player", "allowed-essential"),
        FilterRule("allow_ytm_search_api", "music.youtube.com", "/youtubei/v1/search", "allowed-essential"),
        FilterRule("allow_ytm_browse_api", "music.youtube.com", "/youtubei/v1/browse", "allowed-essential"),
        FilterRule("allow_ytm_next_api", "music.youtube.com", "/youtubei/v1/next", "allowed-essential"),
        FilterRule("allow_googlevideo_stream", "googlevideo.com", "/videoplayback", "allowed-essential"),
        FilterRule("allow_google_accounts", "accounts.google.com", null, "allowed-essential"),
        FilterRule("allow_ytimg_artwork", "i.ytimg.com", null, "allowed-essential"),
        FilterRule("allow_googleusercontent_art", "lh3.googleusercontent.com", null, "allowed-essential"),
        FilterRule("allow_itunes_search", "itunes.apple.com", null, "allowed-essential"),
        FilterRule("allow_itunes_audio", "itunes.apple.com", "/search", "allowed-essential"),
        FilterRule("allow_mzstatic_audio", "mzstatic.com", null, "allowed-essential")
    )

    // Maintained host & path block rules for ad-serving, tracking, and telemetry endpoints
    private val bundledBlockRules = listOf(
        FilterRule("block_doubleclick", "doubleclick.net", null, "ad"),
        FilterRule("block_googlesyndication", "googlesyndication.com", null, "ad"),
        FilterRule("block_googleadservices", "googleadservices.com", null, "ad"),
        FilterRule("block_adservice_google", "adservice.google.com", null, "ad"),
        FilterRule("block_ads_youtube", "ads.youtube.com", null, "ad"),
        FilterRule("block_ytm_pagead_path", "music.youtube.com", "/pagead/", "ad"),
        FilterRule("block_ytm_ptracking_path", "music.youtube.com", "/ptracking", "telemetry"),
        FilterRule("block_ytm_api_stats_ads", "music.youtube.com", "/api/stats/ads", "ad"),
        FilterRule("block_ytm_api_stats_atr", "music.youtube.com", "/api/stats/atr", "telemetry"),
        FilterRule("block_youtube_pagead_path", "youtube.com", "/pagead/", "ad"),
        FilterRule("block_youtube_api_stats_ads", "youtube.com", "/api/stats/ads", "ad"),
        FilterRule("block_pagead2", "pagead2.googlesyndication.com", null, "ad"),
        FilterRule("block_google_analytics", "analytics.google.com", null, "tracker"),
        FilterRule("block_app_measurement", "app-measurement.com", null, "telemetry"),
        FilterRule("block_facebook_connect", "connect.facebook.net", null, "tracker"),
        FilterRule("block_facebook_pixel", "pixel.facebook.com", null, "tracker"),
        FilterRule("block_twitter_ads", "ads-twitter.com", null, "ad"),
        FilterRule("block_tiktok_analytics", "analytics.tiktok.com", null, "tracker"),
        FilterRule("block_tiktok_ads", "ads.tiktok.com", null, "ad"),
        FilterRule("block_scorecardresearch", "scorecardresearch.com", null, "tracker"),
        FilterRule("block_quantserve", "quantserve.com", null, "tracker"),
        FilterRule("block_zedo", "zedo.com", null, "ad"),
        FilterRule("block_adnxs", "adnxs.com", null, "ad"),
        FilterRule("block_rubicon", "rubiconproject.com", null, "ad"),
        FilterRule("block_pubmatic", "pubmatic.com", null, "ad"),
        FilterRule("block_criteo", "criteo.com", null, "ad"),
        FilterRule("block_taboola", "taboola.com", null, "ad"),
        FilterRule("block_outbrain", "outbrain.com", null, "ad"),
        FilterRule("block_flurry", "flurry.com", null, "telemetry"),
        FilterRule("block_mixpanel", "mixpanel.com", null, "telemetry"),
        FilterRule("block_amplitude", "amplitude.com", null, "telemetry"),
        FilterRule("block_segment", "segment.io", null, "telemetry"),
        FilterRule("block_branch", "branch.io", null, "tracker"),
        FilterRule("block_adjust", "adjust.com", null, "tracker"),
        FilterRule("block_appsflyer", "appsflyer.com", null, "tracker"),
        FilterRule("block_omtrdc", "omtrdc.net", null, "tracker"),
        FilterRule("block_demdex", "demdex.net", null, "tracker"),
        FilterRule("block_krxd", "krxd.net", null, "tracker"),
        FilterRule("block_bluekai", "bluekai.com", null, "tracker"),
        FilterRule("block_moatads", "moatads.com", null, "ad"),
        FilterRule("block_doubleverify", "doubleverify.com", null, "ad"),
        FilterRule("block_adsrvr", "adsrvr.org", null, "ad"),
        FilterRule("block_adswizz_audio", "audio.adswizz.com", null, "ad"),
        FilterRule("block_adswizz_delivery", "delivery.adswizz.com", null, "ad"),
        FilterRule("block_adswizz_synchro", "synchrobox.adswizz.com", null, "ad"),
        FilterRule("block_tritondigital", "tracking.tritondigital.com", null, "tracker"),
        FilterRule("block_streamtheworld_ads", "playerservices.streamtheworld.com", "/api/ads", "ad")
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
    private var allowedEssentialCount: Int = 0
    private var lastBlockedHost: String? = null
    private var lastMatchedRuleId: String? = null
    private val diagnosticsQueue = ArrayDeque<FilterDiagnosticEntry>()

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
     * Normalizes and validates an HTTP/HTTPS URL and evaluates it against the allowlist and blocklist.
     * Fails safely (returns false = allow) if the URL is malformed or filter evaluation encounters an error.
     */
    fun shouldBlockRequest(url: String): Boolean {
        if (!enabled) return false
        val parsed = normalizeHttpUrl(url) ?: return false
        val host = parsed.first
        val path = parsed.second

        // 1. Check explicit allowlist first so essential playback, login, and artwork never break
        val allowedRule = essentialAllowlistRules.firstOrNull { rule ->
            matchesDomainBoundary(host, rule.hostPattern) &&
                (rule.pathPrefix == null || path.startsWith(rule.pathPrefix))
        }
        if (allowedRule != null) {
            allowedEssentialCount += 1
            recordDiagnostic(
                FilterDiagnosticEntry(
                    ruleId = allowedRule.id,
                    category = allowedRule.category,
                    normalizedHost = host,
                    action = "ALLOWED_ESSENTIAL"
                )
            )
            _report.value = buildReport()
            return false
        }

        // 2. Check bundled host + path block rules
        val matchedBundled = bundledBlockRules.firstOrNull { rule ->
            matchesDomainBoundary(host, rule.hostPattern) &&
                (rule.pathPrefix == null || path.startsWith(rule.pathPrefix))
        }
        if (matchedBundled != null) {
            blockedCount += 1
            lastBlockedHost = host
            lastMatchedRuleId = matchedBundled.id
            recordDiagnostic(
                FilterDiagnosticEntry(
                    ruleId = matchedBundled.id,
                    category = matchedBundled.category,
                    normalizedHost = host,
                    action = "BLOCKED"
                )
            )
            _report.value = buildReport()
            return true
        }

        // 3. Check user-configured custom domain rules
        val matchedCustom = customDomains.firstOrNull { customRule ->
            val ruleHost = customRule.substringBefore("/")
            val rulePath = if (customRule.contains("/")) "/" + customRule.substringAfter("/") else null
            matchesDomainBoundary(host, ruleHost) &&
                (rulePath == null || path.startsWith(rulePath))
        }
        if (matchedCustom != null) {
            val customRuleId = "custom_${matchedCustom.replace('.', '_')}"
            blockedCount += 1
            lastBlockedHost = host
            lastMatchedRuleId = customRuleId
            recordDiagnostic(
                FilterDiagnosticEntry(
                    ruleId = customRuleId,
                    category = "custom-rule",
                    normalizedHost = host,
                    action = "BLOCKED"
                )
            )
            _report.value = buildReport()
            return true
        }

        return false
    }

    /**
     * Domain-boundary matching (Section 6):
     * Matches exact domain (`ads.youtube.com`) or subdomain (`sub.ads.youtube.com`),
     * never substring-only matches (`myads.youtube.com` does NOT match `ads.youtube.com`).
     */
    internal fun matchesDomainBoundary(host: String, domainPattern: String): Boolean {
        val cleanHost = host.lowercase().trimEnd('.')
        val cleanPattern = domainPattern.lowercase().trimEnd('.')
        return cleanHost == cleanPattern || cleanHost.endsWith(".$cleanPattern")
    }

    /**
     * Normalizes and validates HTTP/HTTPS URLs, returning `(normalizedHost, normalizedPath)`.
     */
    internal fun normalizeHttpUrl(rawUrl: String): Pair<String, String>? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase()?.removePrefix("www.")?.trimEnd('.') ?: return null
            if (host.isBlank() || !host.contains(".")) return null
            val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
            host to path
        } catch (_: Exception) {
            null
        }
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

    /**
     * Creates a `WebViewClient` override that intercepts network requests for `https://music.youtube.com/`
     * and blocks known ad-tracking domains while preserving playback and login requests.
     */
    fun createFilteringWebViewClient(
        onPageStartedCallback: (String) -> Unit = {},
        onRequestBlockedCallback: (String, String?) -> Unit = { _, _ -> }
    ): BeatifyFilteringWebViewClient {
        return BeatifyFilteringWebViewClient(
            contentBlockingService = this,
            onPageStartedCallback = onPageStartedCallback,
            onRequestBlockedCallback = onRequestBlockedCallback
        )
    }

    private fun recordDiagnostic(entry: FilterDiagnosticEntry) {
        synchronized(diagnosticsQueue) {
            diagnosticsQueue.addFirst(entry)
            while (diagnosticsQueue.size > 15) {
                diagnosticsQueue.removeLast()
            }
        }
    }

    private fun buildReport(): ContentProtectionReport {
        val recentList = synchronized(diagnosticsQueue) { diagnosticsQueue.toList() }
        return ContentProtectionReport(
            isEnabled = enabled,
            activeFilterCount = bundledBlockRules.size + trackingQueryParameters.size + customDomains.size,
            allowlistRuleCount = essentialAllowlistRules.size,
            blockedRequestsCount = blockedCount,
            allowedEssentialCount = allowedEssentialCount,
            customBlockedDomains = customDomains.sorted(),
            lastBlockedHost = lastBlockedHost,
            lastMatchedRuleId = lastMatchedRuleId,
            recentDiagnostics = recentList
        )
    }
}

/**
 * Android `WebViewClient` override that intercepts WebView network requests targeting
 * `https://music.youtube.com/` and blocks known ad-tracking domains and telemetry paths
 * while allowing essential audio, artwork, and authentication endpoints (Section 6).
 */
class BeatifyFilteringWebViewClient(
    private val contentBlockingService: ContentBlockingService,
    private val onPageStartedCallback: (String) -> Unit = {},
    private val onRequestBlockedCallback: (String, String?) -> Unit = { _, _ -> }
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val urlString = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
        if (contentBlockingService.shouldBlockRequest(urlString)) {
            val report = contentBlockingService.report.value
            onRequestBlockedCallback(report.lastBlockedHost ?: urlString, report.lastMatchedRuleId)
            return createEmptyBlockedResponse()
        }
        return super.shouldInterceptRequest(view, request)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        if (!url.isNullOrBlank() && contentBlockingService.shouldBlockRequest(url)) {
            val report = contentBlockingService.report.value
            onRequestBlockedCallback(report.lastBlockedHost ?: url, report.lastMatchedRuleId)
            return createEmptyBlockedResponse()
        }
        return super.shouldInterceptRequest(view, url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank()) {
            onPageStartedCallback(url)
        }
    }

    private fun createEmptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
