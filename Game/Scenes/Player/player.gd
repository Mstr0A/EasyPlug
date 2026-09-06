extends CharacterBody3D

var player_id: String = "Player"
var was_respawned: bool = false # Explicit flag for telemetry reset

@onready var gunRay = $Head/Camera3d/RayCast3d as RayCast3D
@onready var Cam = $Head/Camera3d as Camera3D
@export var _bullet_scene : PackedScene
var mouseSensibility = 1200

const SPEED = 5.0
const JUMP_VELOCITY = 4.5

# Health and UI references
var max_health = 100
var health = 100
@onready var kill_feed = $CanvasLayer/killfeed
@onready var hp_bar = $CanvasLayer/HPBar as ProgressBar

# Killfeed Timed Queue System
var kill_feed_queue: Array = []
const MESSAGE_DURATION: float = 4.0

# 10 Hz Telemetry Accumulator
var telemetry_timer: float = 0.0
const TELEMETRY_INTERVAL: float = 0.1 # 10 times per second (100ms)

var gravity = ProjectSettings.get_setting("physics/3d/default_gravity")

func _ready():
	Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
	gunRay.add_exception(self)
	add_to_group("player")
		
	if hp_bar:
		hp_bar.max_value = max_health
		hp_bar.value = health

func _process(delta):
	if not kill_feed_queue.is_empty():
		var needs_refresh = false
		for i in range(kill_feed_queue.size() - 1, -1, -1):
			kill_feed_queue[i]["time_left"] -= delta
			if kill_feed_queue[i]["time_left"] <= 0:
				kill_feed_queue.remove_at(i)
				needs_refresh = true
		
		if needs_refresh:
			refresh_kill_feed_display()

func _physics_process(delta):
	if not is_on_floor():
		velocity.y -= gravity * delta

	if Input.is_action_just_pressed("Jump") and is_on_floor():
		velocity.y = JUMP_VELOCITY
		
	if Input.is_action_just_pressed("Shoot"):
		shoot()
		
	var input_dir = Input.get_vector("moveLeft", "moveRight", "moveUp", "moveDown")
	var direction = (transform.basis * Vector3(input_dir.x, 0, input_dir.y)).normalized()
	if direction:
		velocity.x = direction.x * SPEED
		velocity.z = direction.z * SPEED
	else:
		velocity.x = move_toward(velocity.x, 0, SPEED)
		velocity.z = move_toward(velocity.z, 0, SPEED)

	move_and_slide()
	
	# Lock telemetry dispatch to exactly 10 Hz (0.1s interval)
	telemetry_timer += delta
	if telemetry_timer >= TELEMETRY_INTERVAL:
		telemetry_timer -= TELEMETRY_INTERVAL
		send_telemetry_to_ktor()

func _input(event):
	if event is InputEventMouseMotion:
		rotation.y -= event.relative.x / mouseSensibility
		$Head/Camera3d.rotation.x -= event.relative.y / mouseSensibility
		$Head/Camera3d.rotation.x = clamp($Head/Camera3d.rotation.x, deg_to_rad(-90), deg_to_rad(90))

func shoot():
	if not gunRay.is_colliding():
		return
		
	var hit_pos = gunRay.get_collision_point()
	var camera_basis = Cam.global_transform.basis
	var muzzle_offset = (-camera_basis.y * 0.25) + (camera_basis.x * 0.2)
	var start_pos = gunRay.global_position + muzzle_offset
	
	spawn_laser_tracer(start_pos, hit_pos)
		
	var collider = gunRay.get_collider()
	if collider and collider.has_method("take_damage"):
		collider.take_damage(25, player_id)
		
	if _bullet_scene:
		var bulletInst = _bullet_scene.instantiate() as Node3D
		bulletInst.set_as_top_level(true)
		get_parent().add_child(bulletInst)
		bulletInst.global_transform.origin = hit_pos
		bulletInst.look_at((hit_pos + gunRay.get_collision_normal()), Vector3.BACK)

func spawn_laser_tracer(start_global: Vector3, end_global: Vector3) -> void:
	var total_distance = start_global.distance_to(end_global)
	if total_distance < 0.1:
		return

	var laser_speed = 45.0
	var bolt_length = 1.8
	
	var tracer = MeshInstance3D.new()
	var mesh = CylinderMesh.new()
	mesh.top_radius = 0.035
	mesh.bottom_radius = 0.035
	mesh.height = min(bolt_length, total_distance)
	tracer.mesh = mesh

	var mat = StandardMaterial3D.new()
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.albedo_color = Color(1, 0.2, 0.2, 0.9)
	mat.emission_enabled = true
	mat.emission = Color(1, 0.2, 0.2)
	mat.emission_energy_multiplier = 8.0
	tracer.material_override = mat

	get_tree().root.add_child(tracer)

	var dir = (end_global - start_global).normalized()
	var travel_start = start_global + (dir * (mesh.height / 2.0))
	var travel_end = end_global - (dir * (mesh.height / 2.0))

	tracer.global_position = travel_start
	tracer.look_at(end_global, Vector3.UP)
	tracer.rotate_object_local(Vector3.RIGHT, PI / 2.0)

	var travel_time = total_distance / laser_speed
	var tween = get_tree().create_tween()
	tween.tween_property(tracer, "global_position", travel_end, travel_time)
	tween.chain().tween_callback(tracer.queue_free)

func take_damage(amount: int, attacker_name: String = "Environment"):
	health -= amount
	if hp_bar:
		hp_bar.value = health
		
	if health <= 0:
		update_kill_feed(attacker_name + " [eliminated] " + player_id)
		health = max_health
		
		if hp_bar:
			hp_bar.value = health
			
		velocity = Vector3.ZERO
		global_position = Vector3(randf_range(-12, 12), 3, randf_range(-12, 12))
		
		# Mark for telemetry reset on next payload
		was_respawned = true

func update_kill_feed(message: String):
	kill_feed_queue.append({
		"text": message,
		"time_left": MESSAGE_DURATION
	})
	refresh_kill_feed_display()

func refresh_kill_feed_display():
	if kill_feed:
		var full_text = ""
		for entry in kill_feed_queue:
			full_text += entry["text"] + "\n"
		kill_feed.text = full_text

func send_telemetry_to_ktor() -> void:
	var payload = {
		"playerId": player_id,
		"posX": global_position.x,
		"posY": global_position.y,
		"posZ": global_position.z,
		"cheaterProfile": "HUMAN_PLAYER",
		"groundTruthSpeedHack": false,
		"isRespawn": was_respawned,
		"health": float(health),
		"timestamp": Time.get_unix_time_from_system()
	}

	TelemetryManager.send_telemetry(payload)
	was_respawned = false # Reset flag after dispatching
