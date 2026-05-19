# Fully Kiosk v0.5.0 rollback

- Decision: Fully Kiosk v0.5.0 removes MQTT support after v0.4.x attempts failed at the broker handshake layer
- Rationale: Cloud-poll architecture worked reliably; MQTT added complexity without benefit
- Future: If MQTT is revisited, prototype against a verified broker (e.g., test.mosquitto.org) before integrating into the driver
