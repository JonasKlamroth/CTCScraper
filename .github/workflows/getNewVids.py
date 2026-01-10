import requests
import xml.etree.ElementTree as ET
import json
import re
import os

# --- Configuration ---
CTC_CHANNEL_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=UCC-UOdK8-mIjxBQm_ot1T-Q"
# This URL points to the file published via GitHub Pages
PUZZLES_URL = "https://klamroth.github.io/CTCScraper/puzzles.json"
OUTPUT_FILE = "puzzles.json"

# --- Scraper Logic ---

def extract_sudoku_pad_links(description):
    regex = r"https://sudokupad\.app/\S+"
    return re.findall(regex, description)

def get_sudoku_pad_deeplink(initial_url):
    return initial_url.replace("sudokupad.app/", "sudokupad.svencodes.com/puzzle/")

def get_video_length(video_url):
    try:
        response = requests.get(video_url)
        response.raise_for_status()
        html = response.text
        regex = r'"lengthSeconds":"(\d+)"'
        match = re.search(regex, html)
        if match:
            length = int(match.group(1))
            if length is None:
                print(f"No length found for {video_url}")
                print(f"HTML: {html}")
            return length
        else:
            return int(match.group(1))
    except Exception as e:
        print(f"Error fetching video length for {video_url}: {e}")
    return None

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
            title = entry.find('atom:title', ns).text
            video_url = entry.find('atom:link', ns).attrib['href']
            
            # media:group contains thumbnail and description
            media_group = entry.find('media:group', ns)
            thumbnail_url = media_group.find('media:thumbnail', ns).attrib['url']
            description = media_group.find('media:description', ns).text
            published = entry.find('atom:published', ns).text

            # Extract views and rating
            views = "0"
            rating = "0"
            
            # Check for media:community/media:statistics and media:starRating
            media_community = media_group.find('media:community', ns)
            if media_community is not None:
                stats = media_community.find('media:statistics', ns)
                if stats is not None:
                    views = stats.attrib.get('views', "0")
                
                star_rating = media_community.find('media:starRating', ns)
                if star_rating is not None:
                    rating = star_rating.attrib.get('average', "0")

            # Fetch video length and extract links
            video_length = get_video_length(video_url)
            sudoku_pad_links = extract_sudoku_pad_links(description)

            if sudoku_pad_links and video_length is not None:
                deeplinks = [get_sudoku_pad_deeplink(link) for link in sudoku_pad_links]
                puzzle = {
                    "title": title,
                    "sudokuPadLinks": deeplinks,
                    "thumbnailUrl": thumbnail_url,
                    "videoLength": video_length,
                    "published": published,
                    "videoUrl": video_url,
                    "description": description,
                    "views": views,
                    "rating": rating
                }
                new_puzzles.append(puzzle)
            else:
                if not sudoku_pad_links:
                    print(f"No SudokuPad link found for '{title}'")
                if video_length is None:
                    print(f"Could not find video length for '{title}'")
                    
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
