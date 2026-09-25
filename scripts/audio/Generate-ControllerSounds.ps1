# Original console-style ticks and pops, synthesized without sampled recordings.
# Rounded navigation ticks and action pops, matched RMS, no echo/reverb.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outputDirectory = Join-Path $projectRoot 'app/src/main/res/raw'
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$sampleRate = 44100

function Write-Cue {
    param([string]$Name, [int]$DurationMs, [double]$StartHz, [double]$EndHz, [double]$BodyHz, [double]$Chime = 0)
    $sampleCount = [int]($sampleRate * $DurationMs / 1000)
    $samples = [double[]]::new($sampleCount)
    $envelopes = [double[]]::new($sampleCount)
    $sum = 0.0
    $weight = 0.0
    for ($index = 0; $index -lt $sampleCount; $index++) {
        $time = $index / [double]$sampleRate
        $duration = ($sampleCount - 1) / [double]$sampleRate
        $attack = 0.5 - 0.5 * [Math]::Cos([Math]::Min(1.0, $time / 0.0018) * [Math]::PI)
        $tail = 0.5 - 0.5 * [Math]::Cos([Math]::Min(1.0, ($duration - $time) / 0.007) * [Math]::PI)
        $envelope = $attack * $tail * [Math]::Exp(-3.8 * $time / $duration)
        $phase = 2 * [Math]::PI * ($EndHz * $time + ($StartHz - $EndHz) * 0.004 * (1 - [Math]::Exp(-$time / 0.004)))
        $body = [Math]::Sin(2 * [Math]::PI * $BodyHz * $time)
        $spark = [Math]::Sin(2 * [Math]::PI * $EndHz * 2.1 * $time) * [Math]::Exp(-$time / 0.004)
        $chimeTone = $Chime * [Math]::Sin(2 * [Math]::PI * $EndHz * 1.5 * $time)
        $value = $envelope * (0.74 * [Math]::Sin($phase) + 0.18 * $body + 0.08 * $spark + $chimeTone)
        $samples[$index] = $value
        $envelopes[$index] = $envelope
        $sum += $value
        $weight += $envelope
    }
    # Remove DC without introducing an edge at the start or end of the sample.
    $energy = 0.0
    $peak = 0.0
    for ($index = 0; $index -lt $sampleCount; $index++) {
        $samples[$index] -= ($sum / $weight) * $envelopes[$index]
        $energy += $samples[$index] * $samples[$index]
        $peak = [Math]::Max($peak, [Math]::Abs($samples[$index]))
    }
    $rms = [Math]::Sqrt($energy / $sampleCount)
    $gain = [Math]::Min(0.044 / $rms, 0.20 / $peak)
    $writer = [IO.BinaryWriter]::new([IO.File]::Create((Join-Path $outputDirectory ($Name + '.wav'))))
    try {
        $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write([int](36 + $sampleCount * 2))
        $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write([int]16)
        $writer.Write([int16]1)
        $writer.Write([int16]1)
        $writer.Write([int]$sampleRate)
        $writer.Write([int]($sampleRate * 2))
        $writer.Write([int16]2)
        $writer.Write([int16]16)
        $writer.Write([Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write([int]($sampleCount * 2))
        foreach ($value in $samples) { $writer.Write([int16][Math]::Round($value * $gain * 32767)) }
    } finally { $writer.Dispose() }
    Write-Output "$Name.wav: $DurationMs ms, RMS $([Math]::Round($rms * $gain, 4)), peak $([Math]::Round($peak * $gain, 4))"
}

Write-Cue 'ui_move' 38 1250 780 350
Write-Cue 'ui_select' 48 920 680 310
Write-Cue 'ui_confirm' 72 1040 880 440 0.12
Write-Cue 'ui_back' 60 680 490 260
Write-Cue 'ui_page' 56 880 720 360 0.06
Write-Cue 'ui_filter' 40 1080 850 400
