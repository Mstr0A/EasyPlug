extends CharacterBody3D

const BASE_SPEED = 5.0
const JUMP_VELOCITY = 4.5
var current_speed = BASE_SPEED
var gravity = ProjectSettings.get_setting("physics/3d/default_gravity")

var bot_id: String
var target_direction = Vector3.ZERO
var change_dir_timer = 0.0

# Human-like Movement Dynamics
var strafe_vector: Vector3 = Vector3.ZERO
var strafe_timer: float = 0.0
var jump_timer: float = 0.0
var pause_timer: float = 0.0
var is_micro_pausing: bool = false

# AI States
enum AIState { WANDERING, CHASING }
var current_state = AIState.WANDERING
var state_timer = 0.0

# Cheater Archetype System
enum CheaterType { LEGIT, SPEEDSTER }
@export var profile: CheaterType = CheaterType.LEGIT

# Cheat States
var is_speed_hacking = false
var burst_timer = 0.0

# Target Management
var current_target: Node3D = null
var player_node: Node3D = null

# Combat Stats & Telemetry Reset
var max_health = 100
var health = 100
var fire_rate = 0.3
var fire_timer = 0.0
var was_respawned: bool = false # Tracks instant teleportation on death

# 10 Hz Telemetry Accumulator
var telemetry_timer: float = 0.0
const TELEMETRY_INTERVAL: float = 0.1 # 10 Hz (10 times per second)

@onready var mesh_instance = $MeshInstance3D
@onready var shooting_ray = $ShootingRay as RayCast3D
@onready var laser_visual = $LaserVisual as MeshInstance3D

func _ready():
	bot_id = "BOT_" + str(randi() % 999)
	add_to_group("bot")
	
	assign_cheater_profile()
	pick_new_direction()
	
	if mesh_instance:
		mesh_instance.material_override = StandardMaterial3D.new()
		
	if shooting_ray:
		shooting_ray.add_exception(self)
		shooting_ray.hit_from_inside = true
		shooting_ray.target_position = Vector3(0, 0, -50)
		shooting_ray.collision_mask = 0xFFFFFFFF
		
	player_node = get_tree().get_first_node_in_group("player")

func assign_cheater_profile():
	var roll = randf()
	if roll < 0.50:
		profile = CheaterType.LEGIT
	else:
		profile = CheaterType.SPEEDSTER

func _process(delta):
	if not laser_visual or not shooting_ray:
		return
		
	var mesh = laser_visual.mesh as ImmediateMesh
	if not mesh:
		return
		
	mesh.clear_surfaces()
	
	if current_state == AIState.CHASING and shooting_ray.is_colliding():
		var start_point = laser_visual.to_local(shooting_ray.global_position)
		var end_point = laser_visual.to_local(shooting_ray.get_collision_point())
		
		mesh.surface_begin(Mesh.PRIMITIVE_LINES)
		mesh.surface_add_vertex(start_point)
		mesh.surface_add_vertex(end_point)
		mesh.surface_end()
		
		laser_visual.visible = true
	else:
		laser_visual.visible = false

func _physics_process(delta):
	# 1. Update Profile-Driven Cheats
	update_cheat_state(delta)

	# 2. Target Scanning
	state_timer -= delta
	if state_timer <= 0:
		state_timer = randf_range(2.0, 4.0)
		current_target = find_closest_target()
		
		if current_target:
			current_state = AIState.CHASING
		else:
			current_state = AIState.WANDERING
			pick_new_direction()

	# 3. Humanized Movement & Simple Facing Execution
	jump_timer -= delta

	if current_state == AIState.CHASING and is_instance_valid(current_target):
		process_combat_movement(delta)
		# Face towards target horizontally
		var target_pos = current_target.global_position
		look_at(Vector3(target_pos.x, global_position.y, target_pos.z), Vector3.UP)
	else:
		process_wander_movement(delta)

	# 4. Movement Physics with Inertia & Smooth Acceleration
	if not is_on_floor():
		velocity.y -= gravity * delta

	if target_direction != Vector3.ZERO and not is_micro_pausing:
		var accel = 12.0 if is_on_floor() else 3.0
		velocity.x = move_toward(velocity.x, target_direction.x * current_speed, accel * delta)
		velocity.z = move_toward(velocity.z, target_direction.z * current_speed, accel * delta)
	else:
		velocity.x = move_toward(velocity.x, 0, 25.0 * delta)
		velocity.z = move_toward(velocity.z, 0, 25.0 * delta)

	move_and_slide()

	if is_on_wall():
		strafe_vector = -strafe_vector
		pick_new_direction()

	# 5. Shooting Loop
	if current_state == AIState.CHASING:
		fire_timer -= delta
		if fire_timer <= 0:
			fire_at_target()
			fire_timer = fire_rate

	# 6. Telemetry Emission (Strict 10 Hz Lock)
	telemetry_timer += delta
	if telemetry_timer >= TELEMETRY_INTERVAL:
		telemetry_timer -= TELEMETRY_INTERVAL
		send_telemetry_to_ktor()

func process_combat_movement(delta: float):
	var dist_to_target = global_position.distance_to(current_target.global_position)
	var forward_dir = (current_target.global_position - global_position)
	forward_dir.y = 0
	forward_dir = forward_dir.normalized()
	
	var right_dir = forward_dir.cross(Vector3.UP).normalized()
	
	strafe_timer -= delta
	if strafe_timer <= 0:
		strafe_timer = randf_range(0.3, 0.9)
		
		var roll = randf()
		if roll < 0.35:
			strafe_vector = right_dir
		elif roll < 0.70:
			strafe_vector = -right_dir
		elif roll < 0.85:
			strafe_vector = forward_dir * 0.5 + right_dir * 0.5
		else:
			strafe_vector = -forward_dir * 0.4

	if dist_to_target > 8.0:
		target_direction = (forward_dir * 0.7 + strafe_vector * 0.6).normalized()
	elif dist_to_target < 3.0:
		target_direction = (-forward_dir * 0.6 + strafe_vector * 0.6).normalized()
	else:
		target_direction = strafe_vector.normalized()

	if is_on_floor() and jump_timer <= 0 and randf() < 0.08:
		velocity.y = JUMP_VELOCITY
		jump_timer = randf_range(2.0, 5.0)

func process_wander_movement(delta: float):
	pause_timer -= delta
	if pause_timer <= 0:
		pause_timer = randf_range(3.0, 7.0)
		is_micro_pausing = (randf() < 0.25)
		if is_micro_pausing:
			await get_tree().create_timer(randf_range(0.4, 1.2)).timeout
			is_micro_pausing = false

	change_dir_timer -= delta
	if change_dir_timer <= 0:
		pick_new_direction()
		if is_on_floor() and randf() < 0.15:
			velocity.y = JUMP_VELOCITY

func update_cheat_state(delta: float):
	match profile:
		CheaterType.LEGIT:
			is_speed_hacking = false

		CheaterType.SPEEDSTER:
			burst_timer -= delta
			if burst_timer <= 0:
				is_speed_hacking = !is_speed_hacking
				burst_timer = randf_range(1.0, 3.0)

	current_speed = (BASE_SPEED * 3.5) if is_speed_hacking else BASE_SPEED
	update_visual_color()

func find_closest_target() -> Node3D:
	var closest: Node3D = null
	var min_dist = INF
	var potential_targets = []
	
	if player_node and is_instance_valid(player_node):
		potential_targets.append(player_node)
		
	for b in get_tree().get_nodes_in_group("bot"):
		if b != self and is_instance_valid(b):
			potential_targets.append(b)
			
	for t in potential_targets:
		var dist = global_position.distance_to(t.global_position)
		if dist < min_dist and dist < 20.0 and t.get("health") != null and t.health > 0:
			min_dist = dist
			closest = t
			
	return closest

func pick_new_direction():
	var random_angle = randf() * TAU
	target_direction = Vector3(cos(random_angle), 0, sin(random_angle)).normalized()
	change_dir_timer = randf_range(2.0, 4.0)

func update_visual_color():
	if mesh_instance and mesh_instance.material_override:
		var mat = mesh_instance.material_override as StandardMaterial3D
		if is_speed_hacking:
			mat.albedo_color = Color.RED
		else:
			mat.albedo_color = Color.WHITE

func fire_at_target():
	if shooting_ray and shooting_ray.is_colliding():
		var hit_pos = shooting_ray.get_collision_point()
		var bot_basis = global_transform.basis
		var muzzle_offset = (-bot_basis.y * 0.25) + (bot_basis.x * 0.2)
		var start_pos = shooting_ray.global_position + muzzle_offset
		
		spawn_laser_tracer(start_pos, hit_pos)
		
		var collider = shooting_ray.get_collider()
		if collider and collider.has_method("take_damage") and collider != self:
			collider.take_damage(15, bot_id)

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
	mat.albedo_color = Color(1, 1, 0.2, 0.9)
	mat.emission_enabled = true
	mat.emission = Color(1, 1, 0.2)
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
	
	if health <= 0:
		if player_node and player_node.has_method("update_kill_feed"):
			player_node.update_kill_feed(attacker_name + " [eliminated] " + bot_id)
		respawn()

func respawn():
	health = max_health
	global_position = Vector3(randf_range(-12, 12), 2, randf_range(-12, 12))
	current_state = AIState.WANDERING
	current_target = null
	assign_cheater_profile()
	
	was_respawned = true

func send_telemetry_to_ktor():
	var current_velocity_mag = velocity.length()
	var actual_speed_hack = current_velocity_mag > 7.0

	var payload = {
		"playerId": bot_id,
		"posX": global_position.x,
		"posY": global_position.y,
		"posZ": global_position.z,
		"cheaterProfile": CheaterType.keys()[profile],
		"groundTruthSpeedHack": actual_speed_hack,
		"isRespawn": was_respawned,
		"health": float(health),
		"timestamp": Time.get_unix_time_from_system()
	}
	
	TelemetryManager.send_telemetry(payload)
	was_respawned = false
