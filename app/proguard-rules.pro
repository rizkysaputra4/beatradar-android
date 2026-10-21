# kotlinx.serialization, Room, WorkManager, OkHttp and Coil ship their own R8 rules, so app classes need none here.
# Don't add broad -keep rules for com.boimin.beatradar.**: they stop R8 from renaming and shrinking the app's code.
