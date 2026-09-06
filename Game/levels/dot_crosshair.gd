extends Control

@export var dot_color: Color = Color.GREEN # Change to WHITE, RED, etc.
@export var dot_radius: float = 3.0       # Size of the dot

func _draw():
	# Automatically finds the exact center of the screen
	var center = size / 2
	draw_circle(center, dot_radius, dot_color)

func _process(_delta):
	# Keeps the crosshair perfectly updated if the window size changes
	queue_redraw()
