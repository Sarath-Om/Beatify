#!/usr/bin/env python3
# yt_rss_reader.py
# Top 4000 YouTube music channels ke latest videos, RSS feeds se fetch karta hai.
# Chalane ka tareeqa:  python3 yt_rss_reader.py
# Zaroori: youtube_music_channels.opml isi folder mein hona chahiye.
# Output: latest_videos.csv (channel ke latest videos)
#         failed_feeds.csv (jin channels ka feed abhi nahi mila)

import csv
import time
import xml.etree.ElementTree as ET
import urllib.error
import urllib.request

OPML_FILE = "youtube_music_channels.opml"
OUT_FILE = "latest_videos.csv"
FAILED_FILE = "failed_feeds.csv"
DELAY = 0.5        # har feed ke beech wait (seconds), YouTube rate-limit se bachne ke liye
PER_CHANNEL = 5    # channel ke latest kitne videos chahiye
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
NS = {"a": "http://www.w3.org/2005/Atom", "yt": "http://www.youtube.com/xml/schemas/2015"}


def load_channels(opml_path):
    """OPML se (channel name, channel id) ki list nikalta hai."""
    tree = ET.parse(opml_path)
    chans = []
    for outline in tree.iter("outline"):
        url = outline.attrib.get("xmlUrl", "")
        if "channel_id=" in url:
            cid = url.split("channel_id=")[1].split("&")[0]
            chans.append((outline.attrib.get("title", ""), cid))
    return chans


def fetch_feed(channel_id):
    """YouTube ka official Atom feed: har channel ke latest 15 videos."""
    url = f"https://www.youtube.com/feeds/videos.xml?channel_id={channel_id}"
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=20) as r:
        return ET.fromstring(r.read())


def main():
    chans = load_channels(OPML_FILE)
    print(f"{len(chans)} channels load hue {OPML_FILE} se")

    rows, failed = [], []
    for i, (name, cid) in enumerate(chans, 1):
        try:
            root = fetch_feed(cid)
            for e in root.findall("a:entry", NS)[:PER_CHANNEL]:
                link_el = e.find("a:link", NS)
                rows.append({
                    "Channel": name,
                    "Video ID": e.findtext("yt:videoId", namespaces=NS),
                    "Title": e.findtext("a:title", namespaces=NS),
                    "Link": link_el.attrib.get("href", "") if link_el is not None else "",
                    "Published": e.findtext("a:published", namespaces=NS),
                })
        except urllib.error.HTTPError as e:
            failed.append((name, cid, f"HTTP {e.code}"))
        except Exception as e:
            failed.append((name, cid, type(e).__name__))
        if i % 100 == 0:
            print(f"... {i}/{len(chans)} channels check ho gaye")
        time.sleep(DELAY)

    with open(OUT_FILE, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["Channel", "Video ID", "Title", "Link", "Published"])
        w.writeheader()
        w.writerows(rows)
    print(f"Done: {len(rows)} videos -> {OUT_FILE}")

    if failed:
        with open(FAILED_FILE, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["Channel", "Channel ID", "Error"])
            w.writerows(failed)
        print(f"{len(failed)} feeds fail hue -> {FAILED_FILE}")
        print("Note: HTTP 404 aksar temporary hota hai; thodi der baad script dobara chala dena.")


if __name__ == "__main__":
    main()
