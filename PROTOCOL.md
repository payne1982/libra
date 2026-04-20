# Runtastic Libra BLE Protocol (reverse engineered)

## BLE Connection

**Device discovery:** scan for device name containing `libra`, `Libra`, or `LIBRA`.

**GATT characteristics:**
| UUID | Type | Purpose |
|------|------|---------|
| `0000ffe1-0000-1000-8000-00805f9b34fb` | Write | Send commands to the scale |
| `0000ffe2-0000-1000-8000-00805f9b34fb` | Notify | Receive notifications from the scale |
| `00002902-0000-1000-8000-00805f9b34fb` | CCCD descriptor | Enable notifications (write `0x0100`) |

---

## Packet format — app to scale (write on FFE1)

Commands are 20-byte packets (zero-padded). General structure:

```
[opcode][payload...]
```

For Libra commands (GET/SET), the expected response byte is used to match
the corresponding incoming notification.

**Mandatory ACK** — send on FFE1 after EVERY received sub-packet (both 0x59 and 0x42).
Without the ACK the scale will not send the next sub-packet and the protocol stalls.
```
[0xF7][0xF1][data[1]][data[2]][data[3]]
```

---

## Command opcodes (responses received on FFE1/FFE2)

All notifications from the scale begin with `[0xF7][opcode]` or with the
opcode byte directly.

| Opcode (dec) | Hex  | Command                    |
|-------------|------|----------------------------|
| 51          | 0x33 | getUserList                |
| 53          | 0x35 | updateUser                 |
| 64          | 0x40 | takeUserMeasurement        |
| 65          | 0x41 | getUserMeasurements        |
| 67          | 0x43 | deleteUserMeasurements     |
| 68          | 0x44 | setUserWeightAndBf         |
| 70          | 0x46 | getUnknownMeasurements     |
| 73          | 0x49 | deleteUnknownMeasurement   |
| 75          | 0x4B | assignMeasurementToUser    |
| 77          | 0x4D | setUnit (kg/lb)            |
| 79          | 0x4F | getScaleStatus             |
| 83          | 0x53 | finishMeasurement (alt)    |
| 86          | 0x56 | createUser                 |
| 88          | 0x58 | **liveWeight**             |
| 89          | 0x59 | **finishMeasurement**      |
| 17          | 0x11 | liveHeartRate              |
| 21          | 0x15 | beginFirmwareUpdate        |
| 224 (-32)   | 0xE0 | scaleSleep / sessionEnd    |
| 225 (-31)   | 0xE1 | getModuleVersion           |
| 226 (-30)   | 0xE2 | getRemoteTimestamp         |
| 246 (-10)   | 0xF6 | connectionInterval         |
| 249 (-7)    | 0xF9 | setDateTime                |
| 252 (-4)    | 0xFC | getTxPower                 |
| 254 (-2)    | 0xFE | getSlowAdvInterval         |

---

## Notifications from the scale

### Live weight — `liveWeightCallback` (opcode 0x58)

```
byte[0] = 0xF7
byte[1] = 0x58
byte[2] = unit_flag   (0 = unstable reading, != 0 = stable/final)
byte[3] = weight_hi
byte[4] = weight_lo
```

**Weight calculation:**
```
weight_kg = (byte[3] * 256 + byte[4]) / 20.0
```

### Full measurement — `finishMeasurementCallback` (opcode 0x59)

Arrives in **3 sub-packets**. Each packet has `byte[3]` set to 1, 2, or 3.

**Packet 1** (`byte[3] == 1`):
```
byte[0] = 0xF7
byte[1] = 0x59
byte[2] = ?
byte[3] = 0x01
byte[4] = flag         (measurement status)
byte[5..12] = user_id  (long, 8 bytes, little-endian)
```

**Packet 2** (`byte[3] == 2`):
```
byte[0] = 0xF7
byte[1] = 0x59
byte[2] = ?
byte[3] = 0x02
byte[4..7] = timestamp_unix  (unsigned int, seconds)
byte[8..9]  = weight raw      → weight_kg = (int16 & 0xFFFF) / 20.0
byte[10..11]= impedance       → ohm = int16
byte[12..13]= body_fat_pct    → % = (int16 & 0xFFFF) / 10.0
byte[14]    = bone_msb        (high byte for packet 3)
```

**Packet 3** (`byte[3] == 3`):
```
byte[0] = 0xF7
byte[1] = 0x59
byte[2] = ?
byte[3] = 0x03
byte[4]     = bone_lsb
byte[5..6]  = muscle_pct       → % = (int16 & 0xFFFF) / 10.0
byte[7..8]  = bone_mass        → kg = (int16 & 0xFFFF) / 20.0
byte[9..10] = bmr              → kcal/day = int16
byte[11..12]= amr              → kcal/day = int16
byte[13..14]= bmi              → = (int16 & 0xFFFF) / 10.0
```

**Cross-packet note:** `bone_water_pct = ((bone_lsb & 0xFF) | (bone_msb << 8)) / 10.0`
This field maps to `bodyWaterContent` in the DB (%).

---

## Full measurement field mapping → DB

| DB field          | Source               | Formula                            |
|------------------|----------------------|------------------------------------|
| `userId`         | pkt1 byte[5..12]     | readLong()                         |
| `timeStamp`      | pkt2 byte[4..7]      | readInt() (Unix sec → ms in app)   |
| `weight`         | pkt2 byte[8..9]      | (int16 & 0xFFFF) / 20.0 kg         |
| `impedance`      | pkt2 byte[10..11]    | int16 Ω                            |
| `bodyFat`        | pkt2 byte[12..13]    | fat% * weight / 100.0 kg           |
| `bodyWaterContent`| pkt3 cross-byte     | ((lsb\|msb<<8) & 0xFFFF) / 10.0 % |
| `muscleMass`     | pkt3 byte[5..6]      | muscle% * weight / 100.0 kg        |
| `boneMass`       | pkt3 byte[7..8]      | (int16 & 0xFFFF) / 20.0 kg         |
| `bmr`            | pkt3 byte[9..10]     | int16 kcal/day                     |
| `amr`            | pkt3 byte[11..12]    | int16 kcal/day                     |
| `bodyMassIndex`  | pkt3 byte[13..14]    | (int16 & 0xFFFF) / 10.0            |

---

## Full protocol flow (confirmed from C1471ah + C0614 + C1057)

```
1. setCharacteristicNotification(FFE1, true)
   setCharacteristicNotification(FFE2, true)
   write(FFE1, {0xF6, 0x01})                    ← init

2. scale responds 0xF6... → wait 200ms → send getUserList
   write(FFE1, {0xF7, 0x33})
   **IMPORTANT:** on some Android devices the BLE stack is not ready to receive
   notifications immediately after the 0xF6 response. Without a ~200ms delay,
   the scale sends the 0x33/0x34 response before the app can receive it, then
   times out after ~19 seconds and sends E0 01 instead.

3. scale responds getUserList:
   - header: data[2]==0x33, data[3]=status(1=no users), data[4]=count
   - user packets: data[1]==0x34, data[3]=index(1-based), data[4..11]=userId(8 BE), data[12..14]=initials
   → after receiving all: send setDataTime

4. setDataTime: write(FFE1, {0xF9, timestamp_unix[4 BE]})
   → no notification; wait for onCharacteristicWrite → send setUnit

5. setUnit: write(FFE1, {0xF7, 0x4D, 0x01})   (0x01 = kg)
   → no notification; wait for onCharacteristicWrite → setupComplete

6. setupComplete:
   - if no user found → createUser, then getUserMeasurements or takeUserMeasurement
   - if user found + sync → getUserMeasurements
   - if user found + weighing → takeUserMeasurement

6a. createUser: write(FFE1, {0xF7, 0x31, userId[8 BE], initials[3], year-1900, month, day, height, activ|gender128})
    scale responds data[2]==0x31, data[3]=status
    **STATUS CODES (from C1484au.java):**
    - 0 = user created OK
    - 1 = ? (valid, proceed)
    - 2 = user already exists, profile updated
    - 3 = user already exists (returned when userFound=true) — NOT an error, proceed
    - default = real error
    All cases 0-3 call mo699() (proceed to next step).
    **IMPORTANT:** must ALWAYS be sent before 0x40, even if the user already
    exists on the scale. Without it, the scale won't show the name and body comp = 0.

6b. getUserMeasurements: write(FFE1, {0xF7, 0x41, userId[8 BE]})
    - scale may first respond with data[2]==0x4D (wait/retry) → resend 0x41 after 200ms
    - header: data[2]==0x41, data[3]=totalSubPkts, data[4]=status(1=no measurements)
    - sub-packets (data[1]==0x42): data[2]=totalSubPkts, data[3]=currentIdx (1-based)
      PAYLOAD starts at data[4]:
      - odd sub (currentIdx%2==1): data[4..7]=timestamp BE, data[8..9]=weight/20 BE,
        data[10..11]=impedance BE, data[12..13]=fatPct/10 BE, data[14]=boneMsb
      - even sub (currentIdx%2==0): data[4]=bodyWaterLsb, data[5..6]=musclePct/10 BE,
        data[7..8]=boneMass/20 BE, data[9..10]=bmr BE, data[11..12]=amr BE, data[13..14]=bmi/10 BE
    - mandatory ACK after EVERY 0x42: write(FFE1, {0xF7, 0xF1, data[1], data[2], data[3]})
    - done when currentIdx (data[3]) == totalSubPkts (data[2])

6c. takeUserMeasurement: write(FFE1, {0xF7, 0x40, userId[8 BE]})
    scale responds data[2]==0x40, data[3]=0 → ready

7. scale sends live weight spontaneously: 0xF7 0x58 (ignore in SYNC mode)
   scale sends measurement in 3 sub-packets: 0xF7 0x59
   mandatory ACK for pkt1 and pkt2: write(FFE1, {0xF7, 0xF1, data[1], data[2], data[3]})
```

## getUserMeasurements — 0x42 sub-packet format (Big Endian)

Sub-packet header: `[0xF7][0x42][totalSubPkts][currentIdx]`  
The payload (measurement data) starts at **data[4]** — data[3] is the current index, NOT data.

**Odd sub-packet** (data[3] % 2 == 1):
```
data[4..7]  = Unix timestamp (int, 4 bytes BE) → seconds
data[8..9]  = weight raw → kg = value / 20.0
data[10..11]= impedance → Ω
data[12..13]= body fat → % = value / 10.0
data[14]    = boneMsb (MSB for bodyWater in the even packet)
```

**Even sub-packet** (data[3] % 2 == 0):
```
data[4]     = bodyWaterLsb → bodyWaterPct = ((lsb | boneMsb<<8)) / 10.0
data[5..6]  = muscle → % = value / 10.0
data[7..8]  = bone mass → kg = value / 20.0
data[9..10] = BMR → kcal/day
data[11..12]= AMR → kcal/day
data[13..14]= BMI → value / 10.0
```

**Completion:** when `data[3] (currentIdx) == totalSubPkts` (from data[2] of the sub-pkt or the 0x41 header).

**IMPORTANT — common mistake:** passing `data[2]` (totalSubPkts) as idx instead of `data[3]` (currentIdx)
causes all sub-packets to be treated as "even" (idx always == total == 2) and
`onMeasurementsDownloaded()` to be called after the first packet instead of the last.

---

## Special signals from the scale

### 0xE0 — Session end / scale sleep
```
[0xE0][0x01]
```
The scale sends this in multiple situations:
- After `getUnknownMeasurements (0x46)` with no unknown measurements → sync complete (`onAllDone()`)
- During `WAIT_USER_LIST` (~19s after getUserList) → scale timed out waiting for app response.
  This happens when the app misses the 0x33/0x34 packets (BLE timing). Retry getUserList up to
  3 times with 500ms delay. If all retries fail → show connection error.
- During `WAIT_SETUP` → scale timed out waiting for user action (user selection screen).
  → Disconnect and show connection error.

**Scale timeout:** ~19 seconds. If the app does not respond to a request within 19 seconds,
the scale sends E0 01 and considers the session ended.

### 0x4D — getMeasurements retry
```
[0xF7][0xF0][0x4D][0x00]
```
Temporary response to `getUserMeasurements (0x41)`: the scale is still processing.
→ Resend the 0x41 command after ~200ms.

### 0x46 getUnknownMeasurements — response header
```
[0xF7][0xF0][0x46][status][count]
```
- `status=1` or `count=0` → no unknown measurements → `onAllDone()`
- `count>0` → followed by `count` sub-packets 0x47 with a format similar to 0x42

---

## User management on the scale

The scale maintains internal user profiles. Relevant commands:

- **createUser (0x56):** create profile with height, age, gender
- **updateUser (0x35):** update existing profile
- **getUserList (0x33):** list stored users
- **deleteUser:** remove user
- **setUserWeightAndBf (0x44):** set reference weight/fat for the profile
- **getUserMeasurements (0x41):** download measurement history from the scale
- **getUnknownMeasurements (0x46):** unassigned measurements (user not recognised)
- **assignMeasurementToUser (0x4B):** assign measurement to a user

---

## Key decompiled source files

| File | Role |
|------|------|
| `o/C1370.java` | BLE connection manager (scan, connect, GATT callback) |
| `o/C1152.java` | Libra protocol (write/read on FFE1/FFE2, framing) |
| `o/C1057.java` | GATT command queue (GattQueueWorker) |
| `o/C0614.java` | Dispatcher for notifications received from the scale |
| `o/C0968.java` | Parser `finishMeasurement` (3 packets → C0705) |
| `o/C1156.java` | Parser `liveWeight` → C0764 |
| `o/C0705.java` | Full measurement data class |
| `o/C0764.java` | Live weight data class |
| `o/C1332.java` | takeUserMeasurement command |
| `o/C0950.java` | createUser command (0x31) — mo2695() returns opcode |
| `o/C0693.java` | User profile data class (height, age, gender, activity) |
| `o/C1484au.java` | createUser callback — switch on status 0/1/2/3, all call mo699() |
| `com/runtastic/android/btle/libra/LibraBroadcastReceiver.java` | BLE broadcast receiver → UI |
| `com/runtastic/android/libra/contentProvider/tables/Measurement.java` | DB model + fromValues() |
| `com/runtastic/android/libra/fragments/LibraMeasurementFragment.java` | Measurement UI + mo323() |
