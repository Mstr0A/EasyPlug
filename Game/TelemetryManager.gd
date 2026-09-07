# TelemetryManager.gd (Autoload Singleton)
extends Node

var socket = WebSocketPeer.new()
var ktor_ws_url = "ws://127.0.0.1:8080/session/demo/start"
var is_connected = false

func _ready():
	parse_cmdline_and_config()
	print("[TelemetryManager] Connecting to WebSocket: ", ktor_ws_url)
	socket.connect_to_url(ktor_ws_url)

func parse_cmdline_and_config():
	# 1. Check local config.json
	var exe_dir = OS.get_executable_path().get_base_dir()
	var config_path = exe_dir + "/config.json"
	
	if FileAccess.file_exists(config_path):
		var file = FileAccess.open(config_path, FileAccess.READ)
		var json = JSON.parse_string(file.get_as_text())
		if json and json.has("server_ip"):
			ktor_ws_url = "ws://" + json["server_ip"] + ":8080/api/v1/telemetry"

	# 2. CLI flag overrides config file
	for arg in OS.get_cmdline_args():
		if arg.begins_with("--server="):
			var target_ip = arg.replace("--server=", "").strip_edges()
			ktor_ws_url = "ws://" + target_ip + ":8080/api/v1/telemetry"

func _process(_delta):
	socket.poll()
	var state = socket.get_ready_state()
	is_connected = (state == WebSocketPeer.STATE_OPEN)

# All bots and player objects send payloads through this single connection
func send_telemetry(payload_dict: Dictionary):
	if is_connected:
		var json_string = JSON.stringify(payload_dict)
		socket.send_text(json_string)
