# TileView3

### Basic premise
- there's an image larger than the viewport.  
- use the width and height of the view that contains this image, plus it's scroll position, to determine space we call the viewport
- divide the corners of the viewport (left, top, right, bottom) by the size of the cell, which provide the start and end row and column
```
image is 10,000 square
viewport is 100 square
viewport is at 50x and 100y
so viewport = (left 50, top 100, right 150, bottom 2000)
cell/tile size is 25
so grid is start column 50/25=2, end column is 150/25=6, start row is 100/25=4, end row = 200/25=8
iterate through that grid, and that's the tile to display
for (int i = grid.columns.start; i < grid.columns.end; i++) {
  for (int j = grid.rows.start; j < grid.rows.end; j++) {
    ...
```
- as long as there's only the one size, scale is uniformly applied - just multiple cell/tile size by the current scale, and your grid remains intact
- at every half way point, the image is 50% or small, so subsample it to save memory.  remember that this quarters the amount of memory.  do this at every half: 50%, 25, 12.5, 6.25, 3.625, etc
- when you add another detail level, things get a little more complicated, but not terribly...

### Detail/Zoom Levels
- subsampling starts over at the last provided detail level, so it's no longer directly taken from the scale 
- your grid now has to be "unscaled" - now it's tile size * scale (as it was before), but now must be unscaled by an amount equal to the emulation of the detail level.
- this unscaled value is effectively the inverse of the scale, so 50% scale would be *2, 25% would be *4, 12.5% would be *8
- however, this is a constant value, so anything from 50% to 26% would be *2, so a better descript might be "zoom left shift one" or (zoom << 1)
this works fairly well as is at this point, but on large screens with high density, you end up with _lots_ of very small tiles, unless you provide a ton of detail levels.
if you have a single detail level and let the program subsample for you, the total memory footprint stays (about) the same, but the number of times the disk or caches are hit is very high, and this can appear very slow
to remedy this, we use "patching".  with patching, things get a lot more complicated.

### Patching
- "patching" is basically grabbing as many subsampled tiles as needed to create a full sized tiles, stitching them together into a single bitmap, and stuffing that in the cache
- a very important number here is the "image subsample".  this is distince from the "zoom sample" described above
- the image subsampe is derived from the _distance_ you are from the last provided detail level.  so if you have only 1 detail level (0) and you're at 20%, you're at image subsample 4 (50% would be 2, 25% would be 4, 12.5% would be 8, etc)
- to do that, your grid math has to change a little.  you now have to round down to the nearest image subsample on the left and top, and round up to the nearest image subsample on the bottom and right, and skip a number of columns and rows equal to the image subsample
```
scale is 20%, with 1 defined detail level, so image subsample is 4 (last defined zoom - actual zoom << 1)
image is 10,000 square
viewport is 100 square
viewport is at 50x and 100y
so viewport = (left 50, top 100, right 150, bottom 2000)
cell/tile size is 25
so grid is start column 50/25=2, end column is 150/25=6, start row is 100/25=4, end row = 200/25=8
but now we have to round, so start column becomes 0, end column becomes 8, start row stays at 4 and end row stays at 8
for (int i = grid.columns.start; i < grid.columns.end; i += imageSample) {
  for (int j = grid.rows.start; j < grid.rows.end; j += imageSample) {
    // in this example, you'd only have tiles: (0,4), (4,4), (8,4), (0,8), (4,8), (8,8)
    ...
```
for each of those tiles, we grab the last good detail level and fill in the blanks, so tile (0,4) would draw 
```
(0,0), (0,1), (0,2), (0,3)
(1,0), (1,1), (1,2), (1,3)
(2,0), (2,1), (2,2), (2,3)
(3,0), (3,1), (3,2), (3,3)
```
use a diskcache for these so we don't have to decode 4x the data that we need - once we've stitched a bitmap together, that goes in both the memory cache and the disk cache

the next thing that comes up is when changing between zoom levels, if we just swap out levels, the screen will be blank for a second, regardless of whether that's a defined, supplied detail level, or just subsampling the tiles.  since View.onDraw uses a Canvas, we need to redraw the entire thing each time invalidation occurs.  That means if you switch between zoom level, the canvas will clear the old tiles before drawing the new tiles.  This behavior is generally good (and what ensures that we're generally only going to drawing as much bitmap data as we need to fill the screen, even if we do a bad job of cleaning up), but in this case the visual effect is jarring.
to remedy this, we:
- populate a Region instance with the virtual viewport (scaled)
- for each fully decoded tile in the current (the detail level we just entered), punch a hole in that Region.  what's left is pixels that will remain unfilled after we draw current tiles.
- for each fully decoded and drawn tiles from the previous detail level - the detail level we're exiting - we use Region.quickReject to determine if that previous tile intersects any part of the virtual viewport that will not be filled with current tile pixel data
  - if a previous tile does intersect this unfilled region, we need to draw it.
  - if a previous tile does *not* intersect this unfilled region, that means we have completely covered that area with pixel data from the new level, and can both destroy the previous tile (and it's bitmap), as well as remove it from the list of previous tiles we're testing
    - since we remove every previous tile that's covered by current tiles, this set will become empty once the current viewport is filled with current pixel data.

the next issue is caching and bitmap re-use

### Bitmap Caching and Re-use
emphasis on _and reuse_ - the reuse bit is as important (maybe more) than the traditional "caching" bit.
for now, we're only using files from disk (assets), so the only thing that goes into disk-cache are the stitched tiles.  however, we do use an aggressive memory cache (defaults to one-quarter of availalbe ram).  this serves as both a straight bitmap cache, and a pool of re-usable objects. *when we first go to decode a tile, we check if that exact bitmap is in the memory cache - if it is, great, use it - if it's not, grab the oldest bitmap in the cache and re-use the underlying bitmap (BitmapFactory.Options.inBitmap)* - this significantly speeds up the decode process.