# Windows-OCR (Windows.Media.Ocr, lokal, ohne Netz) fuer alle PNG-Dateien eines Ordners.
# Schreibt je Bild eine JSON-Datei mit Zeilen und Woertern (Pixelkoordinaten).
# Vorhandene JSON-Dateien werden uebersprungen.
# Aufruf: powershell -NoProfile -File winocr.ps1 <ordner>
param([string]$Ordner)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics, ContentType = WindowsRuntime]
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$typ) {
    $t = $asTaskGeneric.MakeGenericMethod($typ).Invoke($null, @($op))
    $null = $t.Wait(-1)
    return $t.Result
}
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if ($null -eq $engine) { throw 'Keine OCR-Sprache' }
$bilder = Get-ChildItem -LiteralPath $Ordner -Filter '*.png' | Sort-Object Name
$n = 0
foreach ($b in $bilder) {
    $ziel = [System.IO.Path]::ChangeExtension($b.FullName, '.json')
    if (Test-Path -LiteralPath $ziel) { continue }
    $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($b.FullName)) ([Windows.Storage.StorageFile])
    $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    $dec = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $bmp = Await ($dec.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    $res = Await ($engine.RecognizeAsync($bmp)) ([Windows.Media.Ocr.OcrResult])
    $zeilen = @()
    foreach ($z in $res.Lines) {
        $w = @()
        foreach ($x in $z.Words) {
            $r = $x.BoundingRect
            $w += , @([math]::Round($r.X, 1), [math]::Round($r.Y, 1), [math]::Round($r.X + $r.Width, 1), [math]::Round($r.Y + $r.Height, 1), $x.Text)
        }
        $zeilen += , @{ t = $z.Text; w = $w }
    }
    $out = @{ breite = $bmp.PixelWidth; hoehe = $bmp.PixelHeight; winkel = $res.TextAngle; zeilen = $zeilen }
    $json = ConvertTo-Json $out -Depth 6 -Compress
    [System.IO.File]::WriteAllText($ziel, $json, (New-Object System.Text.UTF8Encoding($false)))
    $stream.Dispose()
    $n += 1
}
Write-Output ("ok " + $Ordner + " neu: " + $n)
