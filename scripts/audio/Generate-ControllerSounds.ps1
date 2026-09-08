# Original launcher cues synthesized from sine waves. No samples or recordings are used.
# Reproduce the checked-in mono PCM WAVs with PowerShell 7 or Windows PowerShell.
# Low fundamentals, rounded 10 ms attacks and soft tails leave room for media playback.
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
                    $attack = 0.5 - 0.5 * [Math]::Cos([Math]::Min(1.0, $elapsed / 0.010) * [Math]::PI)
                    $tail = 0.5 - 0.5 * [Math]::Cos([Math]::Min(1.0, ($duration - $elapsed) / 0.026) * [Math]::PI)
                    $envelope = $attack * $tail * [Math]::Exp(-2.4 * $progress)
                    $phase = 2 * [Math]::PI * ($voice.From * $elapsed +
                        ($voice.To - $voice.From) * $elapsed * $elapsed / (2 * $duration))
                    $tone = [Math]::Sin($phase) + 0.025 * [Math]::Sin($phase * 2)
                    $value += $voice.Gain * $envelope * $tone
                }
            }
            $bounded = [Math]::Max(-0.95, [Math]::Min(0.95, $value))
            $writer.Write([int16][Math]::Round($bounded * 32767))
        }
    } finally { $writer.Dispose() }
    Write-Output "$Name.wav: $DurationMs ms, $sampleRate Hz, 16-bit mono PCM"
}

Write-Cue 'ui_move' 62 @(
    @{ Start = 0; Length = 62; From = 380; To = 350; Gain = 0.26 }
)
Write-Cue 'ui_filter' 72 @(
    @{ Start = 0; Length = 72; From = 420; To = 455; Gain = 0.26 }
)
Write-Cue 'ui_page' 96 @(
    @{ Start = 0; Length = 78; From = 380; To = 470; Gain = 0.24 },
    @{ Start = 18; Length = 78; From = 570; To = 625; Gain = 0.08 }
)
Write-Cue 'ui_confirm' 132 @(
    @{ Start = 0; Length = 94; From = 330; To = 390; Gain = 0.25 },
    @{ Start = 32; Length = 100; From = 520; To = 585; Gain = 0.19 }
)
Write-Cue 'ui_back' 110 @(
    @{ Start = 0; Length = 92; From = 490; To = 350; Gain = 0.24 },
    @{ Start = 18; Length = 92; From = 290; To = 245; Gain = 0.10 }
)
# A warm fifth marks an actual game/app selection, distinct from a header movement tick.
Write-Cue 'ui_select' 94 @(
    @{ Start = 0; Length = 94; From = 330; To = 345; Gain = 0.23 },
    @{ Start = 12; Length = 82; From = 495; To = 518; Gain = 0.12 }
)
