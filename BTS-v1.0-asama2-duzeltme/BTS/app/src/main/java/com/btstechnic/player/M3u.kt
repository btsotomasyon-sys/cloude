package com.btstechnic.player

enum class Kind { LIVE, MOVIE, SERIES }
data class Entry(val name: String, val url: String, val group: String, val logo: String, val kind: Kind, val referer: String = "", val ua: String = "")

object Library {
    @Volatile var all: List<Entry> = emptyList()
    fun count(k: Kind) = all.count { it.kind == k }
}

object M3u {
    private val attr = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")
    private val ep = Regex("""\bS\d{1,3}\s*E\d{1,3}\b|\b\d{1,3}x\d{1,3}\b""", RegexOption.IGNORE_CASE)
    private val gMovie = Regex(""".*\b(movie|movies|film|filmler|vod)\b.*""", RegexOption.IGNORE_CASE)
    private val gSeries = Regex(""".*\b(series|dizi|diziler)\b.*""", RegexOption.IGNORE_CASE)
    private val file = Regex(""".*\.(mp4|mkv|avi|mov)$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Entry> {
        val out = ArrayList<Entry>(); var name = ""; var group = ""; var logo = ""; var pending = false; var ref = ""; var ua = ""
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val l = raw.trim(); if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF")) {
                pending = true
                val a = attr.findAll(l).associate { m -> m.groupValues[1].lowercase() to m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] } }
                group = a["group-title"] ?: ""; logo = a["tvg-logo"] ?: ""
                name = a["tvg-name"]?.takeIf { it.isNotBlank() } ?: l.substringAfterLast(',').trim()
            } else if (l.startsWith("#EXTVLCOPT:http-referrer=")) { ref = l.substringAfter("=").trim() }
            else if (l.startsWith("#EXTVLCOPT:http-user-agent=")) { ua = l.substringAfter("=").trim() }
            else if (l.startsWith("#EXTGRP:")) { if (group.isEmpty()) group = l.substringAfter(':').trim() }
            else if (!l.startsWith("#") && pending) {
                out.add(Entry(name, l, group, logo, kind(l, name, group), ref, ua)); pending = false; name = ""; group = ""; logo = ""; ref = ""; ua = ""
            }
        }
        return out
    }

    private fun kind(u: String, n: String, g: String) = when {
        "/series/" in u || ep.containsMatchIn(n) || gSeries.matches(g) -> Kind.SERIES
        "/movie/" in u || gMovie.matches(g) || file.matches(u) -> Kind.MOVIE
        else -> Kind.LIVE
    }
}
