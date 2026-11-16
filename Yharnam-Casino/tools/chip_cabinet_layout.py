"""Cabinet and stock layout in Minecraft's 16 units per block. Front is +Z."""

FRAME = (
    ("Lower plinth", (1,0,3.1), (15,1.35,12.2), "edge"),
    ("Plinth lip", (1.25,1.35,3.3), (14.75,1.95,12.0), "trim"),
    ("Back boards", (1.8,1.95,3.7), (14.2,29.0,4.55), "back"),
    ("Left upright", (1.5,1.95,3.8), (2.65,29.0,11.4), "oak"),
    ("Right upright", (13.35,1.95,3.8), (14.5,29.0,11.4), "oak"),
    ("Left molding", (1.45,2.0,11.4), (2.45,29.1,11.85), "edge"),
    ("Right molding", (13.55,2.0,11.4), (14.55,29.1,11.85), "edge"),
    ("Cupboard block", (2.65,1.95,4.55), (13.35,7.55,11.25), "oak"),
    ("Left cupboard door", (2.73,2.35,11.25), (7.92,7.22,11.6), "edge"),
    ("Right cupboard door", (8.08,2.35,11.25), (13.27,7.22,11.6), "edge"),
    ("Left door inset", (3.18,2.83,11.6), (7.40,6.79,11.69), "oak"),
    ("Right door inset", (8.6,2.83,11.6), (12.82,6.79,11.69), "oak"),
    ("Left brass pull", (6.84,4.32,11.69), (7.38,5.07,12.0), "trim"),
    ("Right brass pull", (8.62,4.32,11.69), (9.16,5.07,12.0), "trim"),
    ("Low shelf", (2.55,7.55,4.50), (13.45,8.23,11.72), "oak"),
    ("Low shelf edge", (2.50,7.54,11.72), (13.50,8.22,12.04), "edge"),
    ("Middle shelf", (2.55,14.43,4.50), (13.45,15.1,11.72), "oak"),
    ("Middle shelf edge", (2.50,14.42,11.72), (13.50,15.09,12.04), "edge"),
    ("High shelf", (2.55,21.31,4.50), (13.45,21.98,11.72), "oak"),
    ("High shelf edge", (2.50,21.30,11.72), (13.50,21.97,12.04), "edge"),
    ("Cornice frieze", (1.47,28.20,3.55), (14.53,29.02,11.65), "oak"),
    ("Cornice molding", (1.2,29.02,3.4), (14.8,29.55,11.98), "trim"),
    ("Crown cap", (.95,29.55,3.2), (15.05,30.30,12.33), "edge"),
    ("Left crown bead", (1.85,28.0,11.85), (2.52,29.01,12.04), "edge"),
    ("Right crown bead", (13.48,28.0,11.85), (14.15,29.01,12.04), "edge"),
)

# Ordered by chip denomination. The rendering atlas has one column per position.
TRAYS = (
    (4.25,8.235), (8,8.235), (11.75,8.235),
    (5.53,15.105), (10.47,15.105),
    (5.53,21.985), (10.47,21.985),
)
DENOMINATIONS = (1,5,10,50,100,500,1000)
LANES = 6
CHIP_RADIUS = .23
LANE_SPACING = .55
CHIP_THICKNESS = .060


def tray_pieces():
    for value,(x,y) in zip(DENOMINATIONS,TRAYS):
        yield (f"Tray base {value}",(x-1.78,y,6.6),(x+1.78,y+.035,11.67),"edge")
        yield (f"Felt tray {value}",(x-1.65,y+.035,6.76),(x+1.65,y+.10,11.48),"felt")
        yield (f"Tray front {value}",(x-1.78,y+.035,11.48),(x+1.78,y+.23,11.67),"rail")
        yield (f"Tray back {value}",(x-1.78,y+.035,6.60),(x+1.78,y+.37,6.76),"rail")
        for divider in range(LANES+1):
            wall = x+(divider-LANES/2)*LANE_SPACING
            half_width = .13 if divider in (0,LANES) else .066
            yield (f"Tray {value} divider {divider}",(wall-half_width,y+.10,6.76),
                   (wall+half_width,y+.38,11.48),"rail")


def lane_center(tray, lane):
    x,y = TRAYS[tray]
    return x+(lane-(LANES-1)/2)*LANE_SPACING,y+.10+CHIP_RADIUS,11.43


BOXES = FRAME + tuple(tray_pieces())
