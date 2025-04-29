<p align="center">
  <img src="images/logo.png" alt="MuCuteClient Logo" width="200"/>
</p>

# MuCuteClient: A Utility Client for Minecraft Bedrock

**MuCuteClient** is an open-source utility client made for **Minecraft Bedrock Edition**. It uses a **MITM (Man-in-the-Middle)** approach to provide powerful gameplay enhancements — **without modifying the game’s memory or requiring root access.**

---

## 🔧 Features

- **Non-Intrusive Enhancement**: Works without altering the Minecraft client, keeping your installation clean and untouched.  
- **Advanced Packet Control**: Enables in-depth control and manipulation of packet-level data.  
- **Smooth Performance**: Designed for stable connections and low-lag interaction.  
- **User-Friendly Interface**: Clean and intuitive UI built for mobile usability.  
- **Cross-Platform Support**: Compatible with all **Minecraft Bedrock Edition** platforms via Android MITM control.

---

## 📱 Platform Support

- Supports all **Minecraft Bedrock Edition platforms** through MITM:
  - **Android**
  - **iOS**
  - **Windows 10 & 11**
  - **Nintendo Switch**
  - **Xbox (limited support)**

---

## 🛠️ How to Build

To build MuCuteClient using **Android Studio**, follow these steps:

1. **First compile MuCuteRelay**:  
   Follow the build instructions here:  
   [https://github.com/OpenMITM/MuCuteRelay](https://github.com/OpenMITM/MuCuteRelay)

2. After compiling MuCuteRelay, locate the generated file:
   ```
   MuCuteRelay/build/libs/MuCuteRelay.jar
   ```

3. **Copy `MuCuteRelay.jar` into this project**:  
   Place the `.jar` file inside:
   ```
   app/libs/
   ```

4. **Open MuCuteClient in Android Studio**.

5. **Assemble the APK**:  
   Go to **Build → Assemble 'app'**.  
   After building, the APK will be located at:
   ```
   app/build/outputs/apk/debug/
   ```

---

## License

MuCuteClient is licensed under the **GNU General Public License v3.0 (GPLv3)**.

### ✅ Permitted Uses

- Personal use and modification.  
- Creating content (e.g., videos or showcases) using MuCuteClient.  
- Redistributing the original or modified source code, provided you include the same GPLv3 license and make the source code available.

### ❌ Prohibited Uses

- Distributing modified versions **without** including source code and the GPLv3 license.  
- Using the MuCuteClient name or logo in a way that implies official affiliation or endorsement.  
- Claiming ownership of the project or its original source code.

For full license information, visit:  
[https://www.gnu.org/licenses/gpl-3.0.en.html](https://www.gnu.org/licenses/gpl-3.0.en.html)

---

## 🤝 Contributions

We welcome and appreciate contributions from the community!  
Whether it's code, bug reports, suggestions, or documentation — every bit helps improve **MuCuteClient**.

---

## ❤️ Special Thanks

- **CaiMuCheng** – Package, UI & Relay  
- **LodingGlue** – Modules  
- **MrPokeG** – Modules & UI  
- **lyssadev** – Partial TXPacker Logic  
- **Answer2** – Functions  
- **Hax0r** – Partial ProtoHax Source Code

---

## ⚠️ Disclaimer

MuCuteClient is not affiliated with Mojang Studios, Microsoft, or any official Minecraft development team.

Use MuCuteClient at your **Own Risk**.  
We are **not responsible** for any bans, penalties, or issues that may result from using this client.

**Always follow server rules and respect community standards.**
