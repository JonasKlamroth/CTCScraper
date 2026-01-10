import requests
import xml.etree.ElementTree as ET
import json
import re
import os

# --- Configuration ---
CTC_CHANNEL_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=UCC-UOdK8-mIjxBQm_ot1T-Q"
# This URL points to the file published via GitHub Pages
PUZZLES_URL = "https://jonasklamroth.github.io/CTCScraper/puzzles.json"

OUTPUT_FILE = "puzzles.json"

# --- Scraper Logic ---

def extract_sudoku_pad_links(description):
    regex = r"https://sudokupad\.app/\S+"
    return re.findall(regex, description)

def get_sudoku_pad_deeplink(initial_url):
    return initial_url.replace("sudokupad.app/", "sudokupad.svencodes.com/puzzle/")

def fetch_newest_puzzles():
    try:
        response = requests.get(CTC_CHANNEL_URL)
        response.raise_for_status()
        root = ET.fromstring(response.content)
        
        # Atom feed namespaces
        ns = {
            'atom': 'http://www.w3.org/2005/Atom',
            'media': 'http://search.yahoo.com/mrss/'
        }
        
        entries = root.findall('atom:entry', ns)
        print(f"Found {len(entries)} entries in the feed.")
        
        new_puzzles = []
        for entry in entries:
            title_elem = entry.find('atom:title', ns)
            title = title_elem.text if title_elem is not None else "Unknown Title"
            
            link_elem = entry.find('atom:link', ns)
            video_url = link_elem.attrib['href'] if link_elem is not None else ""
            
            # media:group contains thumbnail and description
            media_group = entry.find('media:group', ns)
            thumbnail_url = ""
            description = ""
            if media_group is not None:
                thumb_elem = media_group.find('media:thumbnail', ns)
                thumbnail_url = thumb_elem.attrib['url'] if thumb_elem is not None else ""
                desc_elem = media_group.find('media:description', ns)
                description = desc_elem.text if desc_elem is not None else ""
            
            published_elem = entry.find('atom:published', ns)
            published = published_elem.text if published_elem is not None else ""

            # Extract views and rating
            views = "0"
            rating = "0"
            
            # Check for media:community/media:statistics and media:starRating
            if media_group is not None:
                media_community = media_group.find('media:community', ns)
                if media_community is not None:
                    stats = media_community.find('media:statistics', ns)
                    if stats is not None:
                        views = stats.attrib.get('views', "0")
                    
                    star_rating = media_community.find('media:starRating', ns)
                    if star_rating is not None:
                        rating = star_rating.attrib.get('average', "0")

            # Extract links
            sudoku_pad_links = extract_sudoku_pad_links(description)

            if sudoku_pad_links:
                deeplinks = [get_sudoku_pad_deeplink(link) for link in sudoku_pad_links]
                puzzle = {
                    "title": title,
                    "sudokuPadLinks": deeplinks,
                    "thumbnailUrl": thumbnail_url,
                    "published": published,
                    "videoUrl": video_url,
                    "description": description,
                    "views": views,
                    "rating": rating
                }
                new_puzzles.append(puzzle)
            else:
                print(f"No SudokuPad link found for '{title}'")
                    
        return new_puzzles
    except Exception as e:
        print(f"Error fetching RSS feed: {e}")
        return []

def download_existing_puzzles():
    try:
        response = requests.get(PUZZLES_URL)
        if response.status_code == 200:
            return response.json()
    except Exception as e:
        print(f"Could not download existing puzzles from {PUZZLES_URL}: {e}")
    return []

def merge_and_save(new_puzzles):
    # Try to download existing puzzles first
    existing_puzzles = download_existing_puzzles()
    if not existing_puzzles:
        print("No existing puzzles found. Starting with an empty list.")
    
    # If download failed, try local file
    if not existing_puzzles and os.path.exists(OUTPUT_FILE):
        with open(OUTPUT_FILE, 'r', encoding='utf-8') as f:
            try:
                existing_puzzles = json.load(f)
            except json.JSONDecodeError:
                existing_puzzles = []

    # Map existing puzzles by videoUrl for easy lookup and state preservation
    puzzles_dict = {p['videoUrl']: p for p in existing_puzzles if 'videoUrl' in p}
    
    # Add new puzzles if not already present, preserving existing ones
    added_count = 0
    for puzzle in new_puzzles:
        vurl = puzzle['videoUrl']
        if vurl not in puzzles_dict:
            puzzles_dict[vurl] = puzzle
            added_count += 1
        else:
            # Update dynamic fields
            puzzles_dict[vurl].update({
                "views": puzzle["views"],
                "rating": puzzle["rating"],
                "sudokuPadLinks": puzzle["sudokuPadLinks"],
                "title": puzzle["title"]
            })
            
    # Sort by published date descending
    combined_list = list(puzzles_dict.values())
    combined_list.sort(key=lambda x: x['published'], reverse=True)

    # Save back to file
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(combined_list, f, indent=4, ensure_ascii=False)
    print(f"Saved {len(combined_list)} puzzles to {OUTPUT_FILE}")
    
    print(f"Successfully merged. Added {added_count} new entries. Total: {len(combined_list)}")

if __name__ == "__main__":
    puzzles = fetch_newest_puzzles()
    if puzzles:
        merge_and_save(puzzles)
