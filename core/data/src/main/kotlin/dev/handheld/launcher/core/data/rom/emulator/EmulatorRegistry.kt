package dev.handheld.launcher.core.data.rom.emulator

/** Independently authored integration facts; source and compatibility limits are in F16 evidence. */
object EmulatorRegistry {
    private fun words(value: String) = value.split(' ').filter(String::isNotBlank).toSet()
    private fun core(id: String, title: String, platforms: String, extensions: String) =
        RetroArchCore(id, title, words(platforms), words(extensions))

    val retroArchCores: List<RetroArchCore> = listOf(
        core("fceumm", "FCEUmm", "nes", "fds nes unif unf"),
        core("nestopia", "Nestopia UE", "nes", "nes fds unf unif"),
        core("snes9x", "Snes9x", "snes", "smc sfc swc fig bs st"),
        core("bsnes", "bsnes", "snes", "smc sfc swc fig gb gbc bs"),
        core("mupen64plus_next_gles3", "Mupen64Plus-Next GLES3", "n64", "n64 v64 z64 ndd bin u1"),
        core("parallel_n64", "ParaLLEl N64", "n64", "n64 v64 z64 bin u1 ndd"),
        core("gambatte", "Gambatte", "gb gbc", "gb gbc dmg"),
        core("sameboy", "SameBoy", "gb gbc", "gb gbc"),
        core("mgba", "mGBA", "gba gb gbc", "gb gbc gba"),
        core("melonds", "melonDS", "nds", "nds ids dsi"),
        core("desmume", "DeSmuME", "nds", "nds ids bin"),
        core("pcsx_rearmed", "PCSX ReARMed", "psx", "bin cue img mdf pbp toc cbn m3u ccd chd iso exe"),
        core("mednafen_psx_hw", "Beetle PSX HW", "psx", "cue toc m3u ccd exe pbp chd bin"),
        core("ppsspp", "PPSSPP", "psp", "elf iso cso prx pbp chd"),
        core("genesis_plus_gx", "Genesis Plus GX", "megadrive mastersystem gamegear segacd", "mdx md smd gen bin cue iso sms bms gg sg 68k sgd chd m3u"),
        core("picodrive", "PicoDrive", "megadrive mastersystem segacd sega32x", "bin gen smd md 32x cue iso chd sms gg sg sc m3u 68k sgd pco"),
        core("mednafen_saturn", "Beetle Saturn", "saturn", "ccd chd cue toc m3u zip"),
        core("yabause", "Yabause", "saturn", "bin ccd chd cue iso mds zip m3u"),
        core("flycast", "Flycast", "dreamcast naomi atomiswave", "chd cdi elf bin cue gdi lst zip dat 7z m3u"),
        core("fbneo", "FinalBurn Neo", "arcade neogeo", "zip 7z cue ccd"),
        core("mame2003_plus", "MAME 2003-Plus", "arcade", "zip"),
        core("mame", "MAME", "arcade", "cmd zip 7z"),
        core("neocd", "NeoCD", "neogeocd", "cue chd"),
        core("mednafen_pce_fast", "Beetle PCE Fast", "pce pcecd supergrafx", "pce cue ccd chd toc m3u"),
        core("mednafen_supergrafx", "Beetle SuperGrafx", "supergrafx pce", "pce sgx cue ccd chd"),
        core("stella", "Stella", "atari2600", "a26 bin"),
        core("a5200", "a5200", "atari5200", "a52 bin"),
        core("prosystem", "ProSystem", "atari7800", "a78 bin cdf"),
        core("handy", "Handy", "atarilynx", "lnx lyx o"),
        core("virtualjaguar", "Virtual Jaguar", "atarijaguar", "j64 jag rom abs cof bin prg cue cdi"),
        core("mednafen_wswan", "Beetle WonderSwan", "wonderswan wonderswancolor", "ws wsc pc2 pcv2"),
        core("mednafen_ngp", "Beetle NeoPop", "neogeopocket neogeopocketcolor", "ngp ngc ngpc npc"),
        core("bluemsx", "blueMSX", "msx msx2 colecovision", "rom ri mx1 mx2 dsk col sg sc sf cas m3u"),
        core("fmsx", "fMSX", "msx msx2", "rom mx1 mx2 dsk fdi cas m3u"),
        core("freeintv", "FreeIntv", "intellivision", "int bin rom"),
        core("vecx", "vecx", "vectrex", "bin vec"),
        core("opera", "Opera", "3do", "iso bin chd cue"),
        core("puae", "PUAE", "amiga", "adf adz dms fdi ipf hdf hdz lha slave info cue ccd nrg mds iso chd uae m3u zip 7z rp9"),
        core("vice_x64sc", "VICE x64sc", "c64", "d64 d71 d80 d81 d82 g64 g41 x64 t64 tap prg p00 crt bin zip gz d6z d7z d8z g6z g4z x6z cmd m3u vfl vsf nib nbz d2m d4m"),
        core("fuse", "Fuse", "zxspectrum", "tzx tap z80 rzx scl trd dsk dck sna szx zip ipf"),
        core("cap32", "Caprice32", "amstradcpc", "dsk sna zip tap cdt voc cpr m3u"),
        core("dosbox_pure", "DOSBox Pure", "dos", "zip dosz exe com bat iso chd cue ins img ima vhd jrc tc m3u m3u8 conf"),
        core("scummvm", "ScummVM", "scummvm", "scummvm"),
    )

    internal val profiles: List<EmulatorProfile> = buildList {
        add(EmulatorProfile("gamenative", "app.gamenative", "GameNative", words("windows"),
            dev.handheld.launcher.core.domain.rom.scan.PcGameShortcut.sources.keys, "app.gamenative.MainActivity", EmulatorContract.GAMENATIVE))
        for (pkg in listOf("gamehub.lite", "emuready.gamehub.lite")) {
            add(EmulatorProfile(pkg, pkg, if (pkg == "gamehub.lite") "GameHub Lite" else "GameHub Lite (EmuReady)",
                words("windows"), words("steam"), "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity", EmulatorContract.GAMEHUB_STEAM))
        }
        fun view(id: String, pkg: String, name: String, platforms: String, extensions: String, activity: String) {
            add(EmulatorProfile(id, pkg, name, words(platforms), words(extensions), activity))
        }
        for ((id, pkg, name) in listOf(
            Triple("ppsspp", "org.ppsspp.ppsspp", "PPSSPP"),
            Triple("ppsspp-gold", "org.ppsspp.ppssppgold", "PPSSPP Gold"),
            Triple("ppsspp-legacy", "org.ppsspp.ppsspplegacy", "PPSSPP Legacy"),
        )) view(id, pkg, name, "psp", "iso cso chd pbp elf prx", "org.ppsspp.ppsspp.PpssppActivity")
        view("azahar", "org.azahar_emu.azahar", "Azahar", "3ds", "cci cxi app 3dsx", "org.citra.citra_emu.activities.EmulationActivity")
        view("flycast", "com.flycast.emulator", "Flycast", "dreamcast naomi atomiswave", "gdi cdi chd cue zip 7z bin lst dat elf", "com.flycast.emulator.NativeGLActivity")
        view("dolphin", "org.dolphinemu.dolphinemu", "Dolphin", "gamecube wii", "iso gcm gcz ciso wbfs rvz wia dol elf tgc", "org.dolphinemu.dolphinemu.ui.main.MainActivity")
        view("cemu", "info.cemu.cemu", "Cemu", "wiiu", "wud wux wua wuhb iso elf rpx", "info.cemu.cemu.emulation.EmulationActivity")
        view("eden", "dev.eden.eden_emulator", "Eden", "switch", "xci nsp nro nca", "org.yuzu.yuzu_emu.activities.EmulationActivity")
        view("mupen64plus-fz", "org.mupen64plusae.v3.fzurita", "M64Plus FZ", "n64", "n64 v64 z64 zip 7z", "paulscode.android.mupen64plusae.SplashActivity")
        view("mupen64plus-fz-pro", "org.mupen64plusae.v3.fzurita.pro", "M64Plus FZ Pro", "n64", "n64 v64 z64 zip 7z", "paulscode.android.mupen64plusae.SplashActivity")
        view("mupen64plus-ae", "org.mupen64plusae.v3.alpha", "Mupen64Plus AE", "n64", "n64 v64 z64 zip 7z", "paulscode.android.mupen64plusae.SplashActivity")
        add(EmulatorProfile("melonds", "me.magnum.melonds", "melonDS", words("nds"), words("nds"), "me.magnum.melonds.ui.emulator.EmulatorActivity", EmulatorContract.MELONDS))
        add(EmulatorProfile("duckstation", "com.github.stenzek.duckstation", "DuckStation", words("psx"), words("cue bin iso img chd pbp m3u ccd exe"), "com.github.stenzek.duckstation.EmulationActivity", EmulatorContract.BOOT_PATH))
        add(EmulatorProfile("aethersx2", "xyz.aethersx2.android", "AetherSX2 / NetherSX2", words("ps2"), words("iso chd cso bin img mdf gz elf"), "xyz.aethersx2.android.EmulationActivity", EmulatorContract.BOOT_PATH))
        add(EmulatorProfile("nethersx2-turnip", "xyz.aethersx2.cturnip", "NetherSX2 (Turnip)", words("ps2"), words("iso chd cso bin img mdf gz elf"), "xyz.aethersx2.android.EmulationActivity", EmulatorContract.BOOT_PATH))
        for ((suffix, name, platforms, extensions) in listOf(
            listOf("NesEmu", "NES.emu", "nes", "nes fds unf unif"),
            listOf("Snes9xPlus", "Snes9x EX+", "snes", "smc sfc swc fig"),
            listOf("GbcEmu", "GBC.emu", "gb gbc", "gb gbc"),
            listOf("GbaEmu", "GBA.emu", "gba", "gba"),
            listOf("MdEmu", "MD.emu", "megadrive mastersystem segacd", "md smd bin gen sms cue iso chd"),
            listOf("PceEmu", "PCE.emu", "pce pcecd supergrafx", "pce sgx cue ccd chd"),
            listOf("NeoEmu", "NEO.emu", "neogeo", "zip"),
            listOf("MsxEmu", "MSX.emu", "msx msx2 colecovision", "rom mx1 mx2 dsk cas col"),
            listOf("NgpEmu", "NGP.emu", "neogeopocket neogeopocketcolor", "ngp ngc npc"),
            listOf("C64Emu", "C64.emu", "c64", "d64 d81 t64 tap prg p00 crt"),
            listOf("A2600Emu", "2600.emu", "atari2600", "a26 bin"),
            listOf("SaturnEmu", "Saturn.emu", "saturn", "cue ccd chd"),
        )) view(suffix.lowercase(), "com.explusalpha.$suffix", name, platforms, extensions, "com.imagine.BaseActivity")
        for ((id, pkg, name) in listOf(
            Triple("retroarch", "com.retroarch", "RetroArch"),
            Triple("retroarch-64", "com.retroarch.aarch64", "RetroArch (64-bit)"),
            Triple("retroarch-32", "com.retroarch.ra32", "RetroArch (32-bit)"),
        )) add(EmulatorProfile(id, pkg, name, retroArchCores.flatMap { it.platforms }.toSet(), retroArchCores.flatMap { it.supportedExtensions }.toSet(), "com.retroarch.browser.retroactivity.RetroActivityFuture", EmulatorContract.RETROARCH))

        fun detected(id: String, pkg: String, name: String, platforms: String, reason: String) {
            add(EmulatorProfile(id, pkg, name, words(platforms), emptySet(), contract = EmulatorContract.DETECTION_ONLY, unavailableReason = reason))
        }
        detected("drastic", "com.dsemu.drastic", "DraStic", "nds", "This emulator's document URI launch contract has not been verified. Select melonDS or a configured RetroArch core for direct opening.")
        detected("redream", "io.recompiled.redream", "redream", "dreamcast", "This emulator's document URI launch contract has not been verified. Select Flycast or open the game from redream.")
        detected("epsxe", "com.epsxe.ePSXe", "ePSXe", "psx", "This emulator's document URI launch contract has not been verified. Select DuckStation or open the game from ePSXe.")
        detected("pizzaboy-gba", "it.dbtecno.pizzaboygba", "Pizza Boy GBA", "gba", "This emulator's document URI launch contract has not been verified. Open the game from its library.")
        detected("myboy", "com.fastemulator.gba", "My Boy!", "gba", "This emulator's document URI launch contract has not been verified. Open the game from its library.")
        detected("myoldboy", "com.fastemulator.gbc", "My OldBoy!", "gb gbc", "This emulator's document URI launch contract has not been verified. Open the game from its library.")
        detected("vita3k", "org.vita3k.emulator", "Vita3K", "psvita", "Vita3K requires game installation and title-ID launching; an archive or disc file cannot be opened directly by this adapter.")
    }

    val packageNames: Set<String> = profiles.map { it.packageName }.toSet()
    fun coresForPlatform(platformId: String): List<RetroArchCore> = retroArchCores.filter { platformId in it.platforms }
}
