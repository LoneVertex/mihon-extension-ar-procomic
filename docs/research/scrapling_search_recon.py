#!/usr/bin/env python3
"""
Stage 3C Scrapling recon script: Capture search API calls from procomic.pro
Uses StealthyFetcher (browser-based) to observe what XHR calls the site makes
when a search is performed.

Run ONCE as a dev recon tool. Never shipped to or run on-device.
"""
import sys

try:
    from scrapling.fetchers import StealthyFetcher
    import asyncio
    import re

    async def capture_search_api():
        print("Loading procomic.pro/ar/series with StealthyFetcher...")
        
        StealthyFetcher.adaptive = True
        
        # Capture the initial series page
        page = StealthyFetcher.fetch(
            "https://procomic.pro/ar/series",
            headless=True,
            network_idle=True,
            timeout=30000,
        )
        
        print(f"Status: {page.status}")
        print(f"Page content length: {len(page.html)}")
        
        # Look for series data in the fully rendered page
        slugs = re.findall(r'"slug":"([^"]+)"', page.html)
        print(f"\nFound {len(set(slugs))} unique series slugs in rendered page")
        for s in list(set(slugs))[:5]:
            print(f"  {s}")
        
        # Look for API calls in rendered HTML
        api_calls = re.findall(r'(https?://[^"]*api[^"]*)', page.html)
        print(f"\nAPI-like URLs in rendered HTML: {len(api_calls)}")
        for u in set(api_calls)[:5]:
            print(f"  {u}")
            
        print("\nDone. See findings above.")
        
    asyncio.get_event_loop().run_until_complete(capture_search_api())
    
except Exception as e:
    print(f"Error: {e}")
    import traceback; traceback.print_exc()
