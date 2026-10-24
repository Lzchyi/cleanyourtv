# App Library

Enable **Show App Library shortcut** in Launcher settings for an app-sized
Library tile directly below the dock, in the first regular Home row. Select the
tile and press OK to open the library. The optional tile is off by default;
turning it off keeps saved choices. With **Visible rows = 0**, Home shows only a
narrow preview of the row; moving focus down reveals the tile without reserving
a full extra row.

Down moves focus between Home rows and stops at the final row. It does not open
the library, including when Home is empty. Use the Library tile or a supported
Apps-button action. Back returns to the previous Home focus and scroll position,
with a nearby tile as fallback if an app has disappeared.

The library fills the screen over the selected wallpaper. Its vertically
scrolling landscape grid uses six columns on a typical 1080p Sony X9000H layout,
five on narrower layouts, and fewer when needed. Cards use each app's original
TV banner, with its original icon as fallback, and compact labels underneath.
Apps sort alphabetically by display name using the configured locale, with
package names breaking equivalent-name ties. Home membership and installation
dates do not change the order.

Installed apps with a TV or ordinary Android launch activity are included,
preferring the TV entry when both exist. Services and apps without a launch
activity are excluded. Ordinary Android apps may still need touch or a mouse.

- **OK:** open the selected app.
- **Hold OK or press Menu:** open the app's context menu: Open, Add to Home or
  Remove from Home, Hide app or Unhide app, and App info. Only applicable actions
  appear; hidden apps offer Unhide without changing their saved Home choices.
- **Hide app:** remove the app from Home and the normal library view. When any
  installed apps are hidden, a **Hidden apps** button appears in the header.
  Press Up from the first grid row to reach it, then choose an app and Unhide.
- **Back:** close the context menu first, then the library.

The standard `ACTION_ALL_APPS` intent opens the library. `KEYCODE_ALL_APPS`
opens it when the launcher is in the foreground and receives that key. Some TV
firmware intercepts the key or sends a different code, so dedicated Apps-button
support is not universal.

An optional `apps_key_library` build setting reuses the existing
`HomeKeyService` in Apps-only mode for the standard key and a matching Sony Apps
button. This requires the service to be enabled in Android Accessibility; in
this mode it leaves unrelated Home, Settings and app keys alone. Physical remote
compatibility must be checked on the target device; software-injected input does
not establish that a physical Apps button works. Selecting the Library tile does
not require this service.

Removing an app from Home keeps it installed and available in the library.
Explicit Home removals override theme defaults until re-added. Hiding is stored
separately: unhiding restores the app's previous Home membership and ordering.
There is no uninstall action.

Choices are stored in the launcher's private `library_apps.txt`,
`library_home_removed.txt` and `library_hidden.txt`, with at most 128 entries in
each. Files are written atomically; unreadable choices are not overwritten by a
failed edit. Choices survive restarts and app updates. Missing or disabled
packages are omitted until they return. Theme files, dock ordering and module-card
decisions are not edited by library actions.

The existing package receiver refreshes the catalogue after app changes. Labels
are cached by app update and locale; metadata and visible-card artwork load off
the UI thread. The artwork cache retains at most 24 entries per opening; visible
views retain their own drawables, and app-owned bitmap resources are not manually
recycled. Focus and popup transitions respect animation preferences, reduced
motion and disabled system animations. The Library adds no polling, network
access, backdrop capture, service, permission or dependency. The optional Apps-key
route uses the existing service described above.
