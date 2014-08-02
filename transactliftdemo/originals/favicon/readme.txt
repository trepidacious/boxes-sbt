Favicons are all derived from master/faviconMaster.svg, for Inkscape.

First the Icon and Round Bg layers are exported as bitmap, using page area, at square sizes 16, 32, 48, 64 and 128 for the favicon.ico, in the ico folder.
These images are opened in Gimp using File->Open as Layers... and the resulting xcf is saved as gimpLayers.xcf. This can then be exported as favicon.ico in windows .ico format.

Second the Icon and Square Bg layers are exported at 60, 72, 120, 152, 256 for the apple icons. These are named as found in the apple folder.

Finally the Icon and Round Bg layers are set visible, and the Inkscape document is saved as Plain SVG, favicon.svg in the svg directory.

The favicon.ico and the contents of the apple folder should be placed in the root of the web server static folder, nothing else should be needed for them, although you can add link rels to specifically link them from pages, in the <head>, e.g. just under the title:

<link rel="apple-touch-icon" href="apple-touch-icon-60x60.png" />
<link rel="apple-touch-icon" sizes="76x76" href="apple-touch-icon-76x76.png" />
<link rel="apple-touch-icon" sizes="120x120" href="apple-touch-icon-120x120.png" />
<link rel="apple-touch-icon" sizes="152x152" href="apple-touch-icon-152x152.png" />
<link rel="shortcut icon" href="favicon.ico" />

For the favicon.svg, you need the following, but nothing actually seems to support it:

<link rel="icon" type="image/svg+xml" href="favicon.svg">

