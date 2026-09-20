# A.11 Grimoire GUI-scale hotfix

This is an overlay patch for the current A.11 source tree, intended to be
applied after `blood-on-the-sharktower-A11-smaller-perceived-role-and-empty-slot-hotfix.zip`.

The Grimoire was laid out in Minecraft's already-scaled screen coordinates. On
a high-resolution display, `Auto` can choose a GUI scale larger than 4, which
reduces the logical screen canvas and pulls the circular Grim into the centre.

`AssignRolesScreen` now uses GUI Scale 4 as its high-scale reference. When the
active GUI scale is greater than 4, the screen lays itself out on a larger
virtual canvas and scales that canvas back down for rendering. Mouse events are
mapped through the same transform so the visual layout and click targets stay
in sync.

The patch deliberately leaves scales 1-4 untouched. This avoids enlarging the
Grim on smaller windows while fixing the Auto/high-scale regression shown in
testing.
