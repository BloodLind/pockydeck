# Original launcher cues synthesized from sine waves. No samples or recordings are used.
# Reproduce the checked-in mono PCM WAVs with PowerShell 7 or Windows PowerShell.
# The gentle envelopes and low gain leave room for normal media playback.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outputDirectory = Join-Path $projectRoot 'app/src/main/res/raw'
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$sampleRate = 44100

function Write-Cue {
    param([string]$Name, [int]$DurationMs, [object[]]$Voices)
    $sampleCount = [int]($sampleRate * $DurationMs / 1000)
    $destination = Join-Path $outputDirectory ($Name + '.wav')
    $writer = [IO.BinaryWriter]::new([IO.File]::Create($destination))
    try {
        $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write([int](36 + $sampleCount * 2))
        $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write([int]16)
        $writer.Write([int16]1) # PCM
        $writer.Write([int16]1) # mono
        $writer.Write([int]$sampleRate)
        $writer.Write([int]($sampleRate * 2))
        $writer.Write([int16]2)
        $writer.Write([int16]16)
        $writer.Write([Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write([int]($sampleCount * 2))
        for ($index = 0; $index -lt $sampleCount; $index++) {
            $time = $index / [double]$sampleRate
            $value = 0.0
            foreach ($voice in $Voices) {
                $elapsed = $time - $voice.Start / 1000.0
                $duration = $voice.Length / 1000.0
                if ($elapsed -ge 0 -and $elapsed -lt $duration) {
                    $progress = $elapsed / $duration
                    $attack = [Math]::Sin([Math]::Min(1.0, $elapsed / 0.004) * [Math]::PI / 2)
                    $tail = [Math]::Sin([Math]::Min(1.0, ($duration - $elapsed) / 0.012) * [Math]::PI / 2)
                    $envelope = $attack * $tail * [Math]::Exp(-3.8 * $progress)
                    $phase = 2 * [Math]::PI * ($voice.From * $elapsed +
                        ($voice.To - $voice.From) * $elapsed * $elapsed / (2 * $duration))
                    $tone = [Math]::Sin($phase) + 0.12 * [Math]::Sin($phase * 2.01)
                    $value += $voice.Gain * $envelope * $tone
                }
            }
            $bounded = [Math]::Max(-0.95, [Math]::Min(0.95, $value))
            $writer.Write([int16][Math]::Round($bounded * 32767))
        }
    } finally { $writer.Dispose() }
    Write-Output "$Name.wav: $DurationMs ms, $sampleRate Hz, 16-bit mono PCM"
}

Write-Cue 'ui_move' 38 @(
    @{ Start = 0; Length = 38; From = 1120; To = 840; Gain = 0.32 }
)
Write-Cue 'ui_filter' 45 @(
    @{ Start = 0; Length = 45; From = 960; To = 1220; Gain = 0.33 }
)
Write-Cue 'ui_page' 70 @(
    @{ Start = 0; Length = 48; From = 640; To = 820; Gain = 0.31 },
    @{ Start = 22; Length = 48; From = 1010; To = 1130; Gain = 0.16 }
)
Write-Cue 'ui_confirm' 105 @(
    @{ Start = 0; Length = 55; From = 610; To = 735; Gain = 0.30 },
    @{ Start = 33; Length = 72; From = 1020; To = 1230; Gain = 0.27 }
)
Write-Cue 'ui_back' 90 @(
    @{ Start = 0; Length = 68; From = 950; To = 610; Gain = 0.30 },
    @{ Start = 21; Length = 69; From = 520; To = 350; Gain = 0.17 }
)
